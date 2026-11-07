package com.mediflow.pharmacy.dictionary;

import com.mediflow.pharmacy.config.PharmacyProperties;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Versioned DGDA register CSV importer. Expected header (case-insensitive, order-flexible):
 * {@code brand_name,generic_name,strength,form,dgda_reg_no,atc_code,rxnorm_code}. Rows missing
 * brand or generic are reported as unmapped and never guessed into the dictionary.
 */
@Service
public class DgdaImportService {

  private final NamedParameterJdbcTemplate jdbc;
  private final DictionaryCache cache;
  private final PharmacyProperties props;
  private final AuditPort audit;
  private final ClockPort clock;

  public DgdaImportService(
      NamedParameterJdbcTemplate jdbc,
      DictionaryCache cache,
      PharmacyProperties props,
      AuditPort audit,
      ClockPort clock) {
    this.jdbc = jdbc;
    this.cache = cache;
    this.props = props;
    this.audit = audit;
    this.clock = clock;
  }

  public record ImportResult(
      UUID importId,
      int importVersion,
      int totalRows,
      int createdGenerics,
      int createdBrands,
      int updatedBrands,
      int unmappedRows,
      String unmappedReport) {}

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional
  public ImportResult importCsv(
      String sourceLabel, String fileName, byte[] bytes, UUID actorUserId) throws IOException {
    if (bytes == null || bytes.length == 0) {
      throw new IllegalArgumentException("Empty CSV");
    }
    String text = new String(bytes, StandardCharsets.UTF_8);
    List<CsvReader.Row> rows = CsvReader.read(new StringReader(text), props.getImportMaxRows() + 1);
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("CSV has no rows");
    }
    Map<String, Integer> header = headerIndex(rows.get(0).fields());
    require(header, "brand_name");
    require(header, "generic_name");

    int createdGenerics = 0;
    int createdBrands = 0;
    int updatedBrands = 0;
    List<String> unmapped = new ArrayList<>();
    int dataRows = 0;

    Integer nextVersion =
        jdbc.getJdbcTemplate()
            .queryForObject(
                "SELECT COALESCE(max(import_version), 0) + 1 FROM drug_dictionary_import",
                Integer.class);
    int version = nextVersion == null ? 1 : nextVersion;

    GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
    jdbc.update(
        """
        INSERT INTO drug_dictionary_import
          (import_version, source_label, file_name, imported_by, total_rows, created_at, updated_at)
        VALUES (:v, :label, :file, :by, 0, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("v", version)
            .addValue("label", blankTo(sourceLabel, "DGDA import"))
            .addValue("file", fileName)
            .addValue("by", actorUserId)
            .addValue("now", java.sql.Timestamp.from(clock.now())),
        keyHolder,
        new String[] {"id"});
    UUID importId = (UUID) keyHolder.getKeys().get("id");

    for (int i = 1; i < rows.size(); i++) {
      CsvReader.Row row = rows.get(i);
      if (row.blank()) {
        continue;
      }
      dataRows++;
      String brand = DrugNames.clean(cell(row, header, "brand_name"));
      String generic = DrugNames.clean(cell(row, header, "generic_name"));
      String strength = DrugNames.clean(cell(row, header, "strength"));
      String form = DrugNames.clean(cell(row, header, "form"));
      String dgda = DrugNames.clean(cell(row, header, "dgda_reg_no"));
      String atc = DrugNames.clean(cell(row, header, "atc_code"));
      String rxnorm = DrugNames.clean(cell(row, header, "rxnorm_code"));

      if (brand == null || generic == null) {
        unmapped.add(row.line() + ": missing brand_name or generic_name | " + join(row.fields()));
        continue;
      }

      UUID genericId = findGenericId(generic);
      if (genericId == null) {
        genericId = insertGeneric(generic, atc, rxnorm, importId);
        createdGenerics++;
      } else if (atc != null || rxnorm != null) {
        jdbc.update(
            """
            UPDATE drug_generic SET
              atc_code = COALESCE(:atc, atc_code),
              rxnorm_code = COALESCE(:rx, rxnorm_code),
              updated_at = :now
            WHERE id = :id
            """,
            new MapSqlParameterSource()
                .addValue("atc", atc)
                .addValue("rx", rxnorm)
                .addValue("now", java.sql.Timestamp.from(clock.now()))
                .addValue("id", genericId));
      }

      UUID brandId = findBrandId(dgda, brand, strength, form, genericId);
      if (brandId == null) {
        insertBrand(brand, dgda, strength, form, genericId, importId);
        createdBrands++;
      } else {
        jdbc.update(
            """
            UPDATE drug_brand SET
              name = :name, dgda_reg_no = COALESCE(:dgda, dgda_reg_no),
              strength = COALESCE(:strength, strength), form = COALESCE(:form, form),
              generic_id = :gid, updated_at = :now
            WHERE id = :id
            """,
            new MapSqlParameterSource()
                .addValue("name", brand)
                .addValue("dgda", dgda)
                .addValue("strength", strength)
                .addValue("form", form)
                .addValue("gid", genericId)
                .addValue("now", java.sql.Timestamp.from(clock.now()))
                .addValue("id", brandId));
        tagProvenance("drug_brand", brandId, "DGDA_IMPORT", importId, null);
        updatedBrands++;
      }
    }

    String report = String.join("\n", unmapped);
    jdbc.update(
        """
        UPDATE drug_dictionary_import SET
          total_rows = :total, created_generics = :cg, created_brands = :cb,
          updated_brands = :ub, unmapped_rows = :um, unmapped_report = :report, updated_at = :now
        WHERE id = :id
        """,
        new MapSqlParameterSource()
            .addValue("total", dataRows)
            .addValue("cg", createdGenerics)
            .addValue("cb", createdBrands)
            .addValue("ub", updatedBrands)
            .addValue("um", unmapped.size())
            .addValue("report", report.isEmpty() ? null : report)
            .addValue("now", java.sql.Timestamp.from(clock.now()))
            .addValue("id", importId));

    cache.evictAll();
    audit.record(
        actorUserId,
        null,
        "DGDA_DICTIONARY_IMPORT",
        "drug_dictionary_import",
        importId,
        Map.of(
            "version", version,
            "totalRows", dataRows,
            "unmapped", unmapped.size(),
            "createdBrands", createdBrands));
    return new ImportResult(
        importId,
        version,
        dataRows,
        createdGenerics,
        createdBrands,
        updatedBrands,
        unmapped.size(),
        report);
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public List<Map<String, Object>> listImports() {
    return jdbc.query(
        """
        SELECT id, import_version, source_label, file_name, total_rows, created_generics,
               created_brands, updated_brands, unmapped_rows, created_at
        FROM drug_dictionary_import ORDER BY import_version DESC
        """,
        (rs, n) -> {
          Map<String, Object> m = new HashMap<>();
          m.put("id", rs.getObject("id", UUID.class));
          m.put("importVersion", rs.getInt("import_version"));
          m.put("sourceLabel", rs.getString("source_label"));
          m.put("fileName", rs.getString("file_name"));
          m.put("totalRows", rs.getInt("total_rows"));
          m.put("createdGenerics", rs.getInt("created_generics"));
          m.put("createdBrands", rs.getInt("created_brands"));
          m.put("updatedBrands", rs.getInt("updated_brands"));
          m.put("unmappedRows", rs.getInt("unmapped_rows"));
          m.put("createdAt", rs.getTimestamp("created_at").toInstant());
          return m;
        });
  }

  @PreAuthorize("hasRole('ADMIN')")
  @Transactional(readOnly = true)
  public String unmappedReport(UUID importId) {
    List<String> rows =
        jdbc.query(
            "SELECT unmapped_report FROM drug_dictionary_import WHERE id = :id",
            Map.of("id", importId),
            (rs, n) -> rs.getString(1));
    return rows.isEmpty() || rows.get(0) == null ? "" : rows.get(0);
  }

  private UUID findGenericId(String name) {
    List<UUID> ids =
        jdbc.query(
            "SELECT id FROM drug_generic WHERE lower(name) = :n",
            Map.of("n", DrugNames.normalize(name)),
            (rs, n) -> rs.getObject(1, UUID.class));
    return ids.isEmpty() ? null : ids.get(0);
  }

  private UUID insertGeneric(String name, String atc, String rxnorm, UUID importId) {
    GeneratedKeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(
        """
        INSERT INTO drug_generic (name, atc_code, rxnorm_code, created_at, updated_at)
        VALUES (:name, :atc, :rx, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("name", name)
            .addValue("atc", atc)
            .addValue("rx", rxnorm)
            .addValue("now", java.sql.Timestamp.from(clock.now())),
        kh,
        new String[] {"id"});
    UUID id = (UUID) kh.getKeys().get("id");
    tagProvenance("drug_generic", id, "DGDA_IMPORT", importId, null);
    return id;
  }

  private UUID findBrandId(
      String dgda, String brand, String strength, String form, UUID genericId) {
    if (dgda != null) {
      List<UUID> byDgda =
          jdbc.query(
              "SELECT id FROM drug_brand WHERE dgda_reg_no = :d",
              Map.of("d", dgda),
              (rs, n) -> rs.getObject(1, UUID.class));
      if (!byDgda.isEmpty()) {
        return byDgda.get(0);
      }
    }
    List<UUID> ids =
        jdbc.query(
            """
            SELECT id FROM drug_brand
            WHERE lower(name) = :n AND generic_id = :g
              AND COALESCE(lower(strength),'') = COALESCE(lower(:s),'')
              AND COALESCE(lower(form),'') = COALESCE(lower(:f),'')
            """,
            new MapSqlParameterSource()
                .addValue("n", DrugNames.normalize(brand))
                .addValue("g", genericId)
                .addValue("s", strength)
                .addValue("f", form),
            (rs, n) -> rs.getObject(1, UUID.class));
    return ids.isEmpty() ? null : ids.get(0);
  }

  private UUID insertBrand(
      String brand, String dgda, String strength, String form, UUID genericId, UUID importId) {
    GeneratedKeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(
        """
        INSERT INTO drug_brand (name, dgda_reg_no, strength, form, generic_id, created_at, updated_at)
        VALUES (:name, :dgda, :strength, :form, :gid, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("name", brand)
            .addValue("dgda", dgda)
            .addValue("strength", strength)
            .addValue("form", form)
            .addValue("gid", genericId)
            .addValue("now", java.sql.Timestamp.from(clock.now())),
        kh,
        new String[] {"id"});
    UUID id = (UUID) kh.getKeys().get("id");
    tagProvenance("drug_brand", id, "DGDA_IMPORT", importId, null);
    return id;
  }

  private void tagProvenance(
      String table, UUID rowId, String source, UUID importId, String note) {
    jdbc.update(
        """
        INSERT INTO drug_data_provenance (table_name, row_id, source, import_id, note, created_at, updated_at)
        VALUES (:t, :r, :s, :i, :n, :now, :now)
        ON CONFLICT (table_name, row_id) DO UPDATE SET
          source = EXCLUDED.source, import_id = EXCLUDED.import_id, note = EXCLUDED.note,
          updated_at = EXCLUDED.updated_at
        """,
        new MapSqlParameterSource()
            .addValue("t", table)
            .addValue("r", rowId)
            .addValue("s", source)
            .addValue("i", importId)
            .addValue("n", note)
            .addValue("now", java.sql.Timestamp.from(clock.now())));
  }

  private static Map<String, Integer> headerIndex(List<String> fields) {
    Map<String, Integer> map = new HashMap<>();
    for (int i = 0; i < fields.size(); i++) {
      String key = DrugNames.normalize(fields.get(i)).replace(' ', '_');
      map.put(key, i);
    }
    return map;
  }

  private static void require(Map<String, Integer> header, String col) {
    if (!header.containsKey(col)) {
      throw new IllegalArgumentException("CSV header missing column: " + col);
    }
  }

  private static String cell(CsvReader.Row row, Map<String, Integer> header, String col) {
    Integer idx = header.get(col);
    if (idx == null || idx >= row.fields().size()) {
      return null;
    }
    return row.fields().get(idx);
  }

  private static String blankTo(String v, String def) {
    return v == null || v.isBlank() ? def : v.trim();
  }

  private static String join(List<String> fields) {
    return String.join(",", fields);
  }
}
