package com.mediflow.pharmacy.stock;

import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-pharmacy stock levels and batches. Quantity on {@code pharmacy_stock} = sum of batches. */
@Service
public class StockService {

  private final NamedParameterJdbcTemplate jdbc;
  private final AuditPort audit;
  private final ClockPort clock;

  public StockService(NamedParameterJdbcTemplate jdbc, AuditPort audit, ClockPort clock) {
    this.jdbc = jdbc;
    this.audit = audit;
    this.clock = clock;
  }

  public record StockRow(
      UUID stockId,
      UUID brandId,
      String brandName,
      String strength,
      String form,
      String genericName,
      int quantity,
      int reorderLevel,
      boolean low) {}

  public record BatchRow(
      UUID batchId, UUID stockId, String batchNo, LocalDate expiryDate, int quantity) {}

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional(readOnly = true)
  public List<StockRow> list(UUID hospitalId) {
    return jdbc.query(
        """
        SELECT s.id, s.brand_id, b.name AS brand_name, b.strength, b.form, g.name AS generic_name,
               s.quantity, s.reorder_level
        FROM pharmacy_stock s
        JOIN drug_brand b ON b.id = s.brand_id
        JOIN drug_generic g ON g.id = b.generic_id
        WHERE s.hospital_id = :h
        ORDER BY b.name, b.strength
        """,
        Map.of("h", hospitalId),
        (rs, n) -> {
          int qty = rs.getInt("quantity");
          int reorder = rs.getInt("reorder_level");
          return new StockRow(
              rs.getObject("id", UUID.class),
              rs.getObject("brand_id", UUID.class),
              rs.getString("brand_name"),
              rs.getString("strength"),
              rs.getString("form"),
              rs.getString("generic_name"),
              qty,
              reorder,
              qty <= reorder);
        });
  }

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional(readOnly = true)
  public List<StockRow> lowStock(UUID hospitalId) {
    return list(hospitalId).stream().filter(StockRow::low).toList();
  }

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional(readOnly = true)
  public List<BatchRow> batches(UUID stockId) {
    return jdbc.query(
        """
        SELECT id, stock_id, batch_no, expiry_date, quantity
        FROM pharmacy_stock_batch WHERE stock_id = :s AND quantity > 0
        ORDER BY expiry_date ASC, batch_no
        """,
        Map.of("s", stockId),
        (rs, n) ->
            new BatchRow(
                rs.getObject("id", UUID.class),
                rs.getObject("stock_id", UUID.class),
                rs.getString("batch_no"),
                rs.getDate("expiry_date").toLocalDate(),
                rs.getInt("quantity")));
  }

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional(readOnly = true)
  public int available(UUID hospitalId, UUID brandId) {
    List<Integer> q =
        jdbc.query(
            "SELECT quantity FROM pharmacy_stock WHERE hospital_id = :h AND brand_id = :b",
            Map.of("h", hospitalId, "b", brandId),
            (rs, n) -> rs.getInt(1));
    return q.isEmpty() ? 0 : q.get(0);
  }

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional
  public UUID upsertBatch(
      UUID hospitalId,
      UUID brandId,
      String batchNo,
      LocalDate expiry,
      int quantity,
      int reorderLevel,
      UUID actorUserId) {
    if (quantity < 0) {
      throw new IllegalArgumentException("quantity must be >= 0");
    }
    if (batchNo == null || batchNo.isBlank()) {
      throw new IllegalArgumentException("batchNo required");
    }
    if (expiry == null) {
      throw new IllegalArgumentException("expiry required");
    }
    UUID stockId = ensureStock(hospitalId, brandId, reorderLevel);
    List<UUID> existing =
        jdbc.query(
            "SELECT id FROM pharmacy_stock_batch WHERE stock_id = :s AND batch_no = :b",
            Map.of("s", stockId, "b", batchNo.trim()),
            (rs, n) -> rs.getObject(1, UUID.class));
    UUID batchId;
    if (existing.isEmpty()) {
      GeneratedKeyHolder kh = new GeneratedKeyHolder();
      jdbc.update(
          """
          INSERT INTO pharmacy_stock_batch (stock_id, batch_no, expiry_date, quantity, created_at, updated_at)
          VALUES (:s, :b, :e, :q, :now, :now)
          """,
          new MapSqlParameterSource()
              .addValue("s", stockId)
              .addValue("b", batchNo.trim())
              .addValue("e", java.sql.Date.valueOf(expiry))
              .addValue("q", quantity)
              .addValue("now", java.sql.Timestamp.from(clock.now())),
          kh,
          new String[] {"id"});
      batchId = (UUID) kh.getKeys().get("id");
    } else {
      batchId = existing.get(0);
      jdbc.update(
          """
          UPDATE pharmacy_stock_batch SET quantity = :q, expiry_date = :e, updated_at = :now
          WHERE id = :id
          """,
          new MapSqlParameterSource()
              .addValue("q", quantity)
              .addValue("e", java.sql.Date.valueOf(expiry))
              .addValue("now", java.sql.Timestamp.from(clock.now()))
              .addValue("id", batchId));
    }
    refreshStockQuantity(stockId, reorderLevel);
    audit.record(
        actorUserId,
        null,
        "PHARMACY_STOCK_UPSERT",
        "pharmacy_stock_batch",
        batchId,
        Map.of("hospitalId", hospitalId.toString(), "brandId", brandId.toString(), "qty", quantity));
    return batchId;
  }

  /**
   * Consume {@code qty} from a specific batch (FEFO callers pick the batch). Returns false when
   * insufficient.
   */
  @Transactional
  public boolean consumeBatch(UUID batchId, int qty) {
    if (qty <= 0) {
      return false;
    }
    int updated =
        jdbc.update(
            """
            UPDATE pharmacy_stock_batch SET quantity = quantity - :q, updated_at = :now
            WHERE id = :id AND quantity >= :q
            """,
            new MapSqlParameterSource()
                .addValue("q", qty)
                .addValue("now", java.sql.Timestamp.from(clock.now()))
                .addValue("id", batchId));
    if (updated == 0) {
      return false;
    }
    UUID stockId =
        jdbc.queryForObject(
            "SELECT stock_id FROM pharmacy_stock_batch WHERE id = :id",
            Map.of("id", batchId),
            UUID.class);
    refreshStockQuantity(stockId, null);
    return true;
  }

  @Transactional(readOnly = true)
  public BatchRow requireUsableBatch(UUID batchId, UUID hospitalId, UUID brandId) {
    List<BatchRow> rows =
        jdbc.query(
            """
            SELECT bb.id, bb.stock_id, bb.batch_no, bb.expiry_date, bb.quantity
            FROM pharmacy_stock_batch bb
            JOIN pharmacy_stock s ON s.id = bb.stock_id
            WHERE bb.id = :id AND s.hospital_id = :h AND s.brand_id = :b AND bb.quantity > 0
              AND bb.expiry_date >= CURRENT_DATE
            """,
            Map.of("id", batchId, "h", hospitalId, "b", brandId),
            (rs, n) ->
                new BatchRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("stock_id", UUID.class),
                    rs.getString("batch_no"),
                    rs.getDate("expiry_date").toLocalDate(),
                    rs.getInt("quantity")));
    if (rows.isEmpty()) {
      throw new IllegalArgumentException("Batch unavailable, expired, or wrong brand/hospital");
    }
    return rows.get(0);
  }

  private UUID ensureStock(UUID hospitalId, UUID brandId, int reorderLevel) {
    List<UUID> ids =
        jdbc.query(
            "SELECT id FROM pharmacy_stock WHERE hospital_id = :h AND brand_id = :b",
            Map.of("h", hospitalId, "b", brandId),
            (rs, n) -> rs.getObject(1, UUID.class));
    if (!ids.isEmpty()) {
      return ids.get(0);
    }
    GeneratedKeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(
        """
        INSERT INTO pharmacy_stock (hospital_id, brand_id, quantity, reorder_level, created_at, updated_at)
        VALUES (:h, :b, 0, :r, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("h", hospitalId)
            .addValue("b", brandId)
            .addValue("r", Math.max(0, reorderLevel))
            .addValue("now", java.sql.Timestamp.from(clock.now())),
        kh,
        new String[] {"id"});
    return (UUID) kh.getKeys().get("id");
  }

  private void refreshStockQuantity(UUID stockId, Integer reorderLevel) {
    MapSqlParameterSource p =
        new MapSqlParameterSource()
            .addValue("id", stockId)
            .addValue("now", java.sql.Timestamp.from(clock.now()));
    if (reorderLevel != null) {
      p.addValue("r", Math.max(0, reorderLevel));
      jdbc.update(
          """
          UPDATE pharmacy_stock SET
            quantity = (SELECT COALESCE(sum(quantity),0) FROM pharmacy_stock_batch WHERE stock_id = :id),
            reorder_level = :r,
            updated_at = :now
          WHERE id = :id
          """,
          p);
    } else {
      jdbc.update(
          """
          UPDATE pharmacy_stock SET
            quantity = (SELECT COALESCE(sum(quantity),0) FROM pharmacy_stock_batch WHERE stock_id = :id),
            updated_at = :now
          WHERE id = :id
          """,
          p);
    }
  }
}
