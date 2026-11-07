package com.mediflow.pharmacy.dictionary;

import com.mediflow.pharmacy.dictionary.DrugMatch.MatchType;
import com.mediflow.shared.domain.ConflictSeverity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link DrugDictionaryPort} over the shared {@code drug_brand} / {@code drug_generic} tables.
 * Exact lookups are cached ({@link DictionaryCache}); fuzzy suggestions use pg_trgm when installed
 * and a Java trigram port otherwise. Dictionary reference data is not patient data, so lookups are
 * not audited.
 */
@Service
@Transactional(readOnly = true)
public class DrugDictionaryService implements DrugDictionaryPort {

  static final double SUGGESTION_MIN_SIMILARITY = 0.3;
  static final int MAX_SUGGESTIONS = 25;

  private static final Logger log = LoggerFactory.getLogger(DrugDictionaryService.class);

  static final String BRAND_SELECT =
      """
      SELECT b.id AS brand_id, b.name AS brand_name, b.strength, b.form, b.dgda_reg_no,
             g.id AS generic_id, g.name AS generic_name, g.atc_code, g.rxnorm_code,
             COALESCE(p.source = 'ILLUSTRATIVE_SEED', false) AS illustrative
      FROM drug_brand b
      JOIN drug_generic g ON g.id = b.generic_id
      LEFT JOIN drug_data_provenance p ON p.table_name = 'drug_brand' AND p.row_id = b.id
      """;

  static final String GENERIC_SELECT =
      """
      SELECT g.id, g.name, g.atc_code, g.rxnorm_code,
             COALESCE(p.source = 'ILLUSTRATIVE_SEED', false) AS illustrative
      FROM drug_generic g
      LEFT JOIN drug_data_provenance p ON p.table_name = 'drug_generic' AND p.row_id = g.id
      """;

  static final RowMapper<DrugMatch> BRAND_ROW =
      (rs, n) ->
          new DrugMatch(
              rs.getObject("brand_id", UUID.class),
              rs.getString("brand_name"),
              rs.getString("strength"),
              rs.getString("form"),
              rs.getString("dgda_reg_no"),
              rs.getObject("generic_id", UUID.class),
              rs.getString("generic_name"),
              rs.getString("atc_code"),
              rs.getString("rxnorm_code"),
              MatchType.EXACT_BRAND,
              1.0,
              rs.getBoolean("illustrative"));

  static final RowMapper<GenericRef> GENERIC_ROW =
      (rs, n) ->
          new GenericRef(
              rs.getObject("id", UUID.class),
              rs.getString("name"),
              rs.getString("atc_code"),
              rs.getString("rxnorm_code"),
              rs.getBoolean("illustrative"));

  private final NamedParameterJdbcTemplate jdbc;
  private final DictionaryCache cache;
  private volatile Boolean trigramAvailable;

  public DrugDictionaryService(NamedParameterJdbcTemplate jdbc, DictionaryCache cache) {
    this.jdbc = jdbc;
    this.cache = cache;
  }

  @Override
  public List<DrugMatch> findBrandProducts(String brandName) {
    String key = DrugNames.normalize(brandName);
    if (key.isEmpty()) {
      return List.of();
    }
    ArrayList<DrugMatch> rows =
        cache.get(
            "brand:" + key,
            () ->
                new ArrayList<>(
                    jdbc.query(
                        BRAND_SELECT + " WHERE lower(b.name) = :name ORDER BY b.strength, b.form",
                        Map.of("name", key),
                        BRAND_ROW)));
    return List.copyOf(rows);
  }

  @Override
  public Optional<GenericRef> resolveGeneric(String name) {
    List<DrugMatch> brands = findBrandProducts(name);
    if (!brands.isEmpty()) {
      Set<UUID> generics = brands.stream().map(DrugMatch::genericId).collect(Collectors.toSet());
      // A brand name mapping to several generics is ambiguous → unknown, never guessed.
      return generics.size() == 1 ? Optional.of(brands.get(0).generic()) : Optional.empty();
    }
    return findGenericByName(name);
  }

  Optional<GenericRef> findGenericByName(String name) {
    String key = DrugNames.normalize(name);
    if (key.isEmpty()) {
      return Optional.empty();
    }
    ArrayList<GenericRef> rows =
        cache.get(
            "generic:" + key,
            () ->
                new ArrayList<>(
                    jdbc.query(
                        GENERIC_SELECT + " WHERE lower(g.name) = :name",
                        Map.of("name", key),
                        GENERIC_ROW)));
    return rows.size() == 1 ? Optional.of(rows.get(0)) : Optional.empty();
  }

  @Override
  public List<DrugMatch> suggest(String text, int limit) {
    String q = DrugNames.normalize(text);
    int max = Math.max(1, Math.min(limit, MAX_SUGGESTIONS));
    if (q.isEmpty()) {
      return List.of();
    }
    List<DrugMatch> hits;
    if (trigramAvailable()) {
      try {
        hits = suggestSql(q, max);
      } catch (DataAccessException e) {
        log.warn("pg_trgm lookup failed ({}); using Java fallback", e.getMostSpecificCause().toString());
        trigramAvailable = false;
        hits = suggestJava(q);
      }
    } else {
      hits = suggestJava(q);
    }
    return hits.stream()
        .sorted(Comparator.comparingDouble(DrugMatch::score).reversed()
            .thenComparing(m -> m.brandName() == null ? m.genericName() : m.brandName()))
        .limit(max)
        .toList();
  }

  private List<DrugMatch> suggestSql(String q, int max) {
    MapSqlParameterSource p =
        new MapSqlParameterSource("q", q).addValue("min", SUGGESTION_MIN_SIMILARITY).addValue("lim", max);
    List<DrugMatch> out = new ArrayList<>();
    out.addAll(
        jdbc.query(
            "SELECT * FROM (" + BRAND_SELECT.replace("SELECT b.id", "SELECT similarity(lower(b.name), :q) AS score, b.id")
                + ") x WHERE x.score >= :min ORDER BY x.score DESC LIMIT :lim",
            p,
            (rs, n) -> BRAND_ROW.mapRow(rs, n).withMatch(MatchType.SUGGESTION, rs.getDouble("score"))));
    out.addAll(
        jdbc.query(
            "SELECT * FROM (" + GENERIC_SELECT.replace("SELECT g.id", "SELECT similarity(lower(g.name), :q) AS score, g.id")
                + ") x WHERE x.score >= :min ORDER BY x.score DESC LIMIT :lim",
            p,
            (rs, n) -> genericAsMatch(GENERIC_ROW.mapRow(rs, n), rs.getDouble("score"))));
    return out;
  }

  private List<DrugMatch> suggestJava(String q) {
    List<DrugMatch> out = new ArrayList<>();
    for (DrugMatch b : jdbc.query(BRAND_SELECT, BRAND_ROW)) {
      double s = DrugNames.similarity(b.brandName(), q);
      if (s >= SUGGESTION_MIN_SIMILARITY) {
        out.add(b.withMatch(MatchType.SUGGESTION, s));
      }
    }
    for (GenericRef g : jdbc.query(GENERIC_SELECT, GENERIC_ROW)) {
      double s = DrugNames.similarity(g.name(), q);
      if (s >= SUGGESTION_MIN_SIMILARITY) {
        out.add(genericAsMatch(g, s));
      }
    }
    return out;
  }

  private static DrugMatch genericAsMatch(GenericRef g, double score) {
    return new DrugMatch(
        null, null, null, null, null, g.id(), g.name(), g.atcCode(), g.rxnormCode(),
        MatchType.SUGGESTION, score, g.illustrative());
  }

  boolean trigramAvailable() {
    Boolean known = trigramAvailable;
    if (known == null) {
      try {
        Integer n =
            jdbc.getJdbcTemplate()
                .queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);
        known = n != null && n > 0;
      } catch (DataAccessException e) {
        known = false;
      }
      trigramAvailable = known;
    }
    return known;
  }

  @Override
  public Optional<GenericRef> findGeneric(UUID genericId) {
    if (genericId == null) {
      return Optional.empty();
    }
    return jdbc.query(GENERIC_SELECT + " WHERE g.id = :id", Map.of("id", genericId), GENERIC_ROW).stream()
        .findFirst();
  }

  @Override
  public Optional<DrugMatch> findBrand(UUID brandId) {
    if (brandId == null) {
      return Optional.empty();
    }
    return jdbc.query(BRAND_SELECT + " WHERE b.id = :id", Map.of("id", brandId), BRAND_ROW).stream()
        .findFirst();
  }

  @Override
  public List<DrugMatch> brandsOfGeneric(UUID genericId) {
    if (genericId == null) {
      return List.of();
    }
    return jdbc.query(
        BRAND_SELECT + " WHERE b.generic_id = :id ORDER BY b.name, b.strength",
        Map.of("id", genericId),
        BRAND_ROW);
  }

  @Override
  public List<InteractionRule> interactionsAmong(Collection<UUID> genericIds) {
    if (genericIds == null || genericIds.size() < 2) {
      return List.of();
    }
    return jdbc.query(
        """
        SELECT i.id, i.generic_a_id, ga.name AS a_name, i.generic_b_id, gb.name AS b_name,
               i.severity, i.description,
               COALESCE(p.source = 'PLACEHOLDER_SEED', false) AS placeholder
        FROM drug_interaction i
        JOIN drug_generic ga ON ga.id = i.generic_a_id
        JOIN drug_generic gb ON gb.id = i.generic_b_id
        LEFT JOIN drug_data_provenance p ON p.table_name = 'drug_interaction' AND p.row_id = i.id
        WHERE i.generic_a_id IN (:ids) AND i.generic_b_id IN (:ids)
        """,
        Map.of("ids", List.copyOf(genericIds)),
        DrugDictionaryService::interaction);
  }

  private static InteractionRule interaction(ResultSet rs, int n) throws SQLException {
    return new InteractionRule(
        rs.getObject("id", UUID.class),
        rs.getObject("generic_a_id", UUID.class),
        rs.getString("a_name"),
        rs.getObject("generic_b_id", UUID.class),
        rs.getString("b_name"),
        severity(rs.getString("severity")),
        rs.getString("description"),
        rs.getBoolean("placeholder"));
  }

  /** Unrecognised severities are reported as UNKNOWN (never silently downgraded to "safe"). */
  static ConflictSeverity severity(String raw) {
    try {
      return ConflictSeverity.valueOf(raw);
    } catch (RuntimeException e) {
      return ConflictSeverity.UNKNOWN;
    }
  }

  @Override
  public List<DoseLimitRule> doseLimits(UUID genericId) {
    if (genericId == null) {
      return List.of();
    }
    return jdbc.query(
        """
        SELECT d.id, d.generic_id, d.population, d.route, d.min_dose, d.max_dose, d.unit,
               COALESCE(p.source = 'PLACEHOLDER_SEED', false) AS placeholder
        FROM drug_dose_limit d
        LEFT JOIN drug_data_provenance p ON p.table_name = 'drug_dose_limit' AND p.row_id = d.id
        WHERE d.generic_id = :id
        ORDER BY d.population, d.unit
        """,
        Map.of("id", genericId),
        (rs, n) ->
            new DoseLimitRule(
                rs.getObject("id", UUID.class),
                rs.getObject("generic_id", UUID.class),
                rs.getString("population"),
                rs.getString("route"),
                rs.getBigDecimal("min_dose"),
                rs.getBigDecimal("max_dose"),
                rs.getString("unit"),
                rs.getBoolean("placeholder")));
  }

  /** Full snapshot used by the internal export (T09 matcher). Not cached: export is infrequent. */
  public DictionarySnapshot snapshot() {
    List<GenericRef> generics = jdbc.query(GENERIC_SELECT + " ORDER BY g.name", GENERIC_ROW);
    List<DrugMatch> brands = jdbc.query(BRAND_SELECT + " ORDER BY b.name, b.strength", BRAND_ROW);
    Integer version =
        jdbc.getJdbcTemplate()
            .queryForObject("SELECT COALESCE(max(import_version), 0) FROM drug_dictionary_import", Integer.class);
    Map<UUID, GenericRef> byId = new LinkedHashMap<>();
    generics.forEach(g -> byId.put(g.id(), g));
    return new DictionarySnapshot(version == null ? 0 : version, List.copyOf(byId.values()), brands);
  }

  public record DictionarySnapshot(int importVersion, List<GenericRef> generics, List<DrugMatch> brands) {}
}
