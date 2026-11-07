package com.mediflow.pharmacy.dispensing;

import com.mediflow.pharmacy.dictionary.DrugDictionaryPort;
import com.mediflow.pharmacy.dictionary.DrugMatch;
import com.mediflow.pharmacy.dispensing.PatientIdentityPort.Method;
import com.mediflow.pharmacy.dispensing.PatientIdentityPort.VerifiedIdentity;
import com.mediflow.pharmacy.stock.StockService;
import com.mediflow.pharmacy.stock.StockService.BatchRow;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionStatus;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.PrescriptionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pharmacist dispensing: signed Rx only (read-only items), identity check required, stock/batch
 * deduction, {@link DomainEvents.MedicationDispensed}. AI never dispenses.
 */
@Service
public class DispensingService {

  private final PrescriptionRepository prescriptions;
  private final NamedParameterJdbcTemplate jdbc;
  private final PatientIdentityPort identity;
  private final StockService stock;
  private final DrugDictionaryPort dictionary;
  private final PharmacyHospitalResolver hospitals;
  private final AuditPort audit;
  private final ClockPort clock;
  private final ApplicationEventPublisher events;

  public DispensingService(
      PrescriptionRepository prescriptions,
      NamedParameterJdbcTemplate jdbc,
      PatientIdentityPort identity,
      StockService stock,
      DrugDictionaryPort dictionary,
      PharmacyHospitalResolver hospitals,
      AuditPort audit,
      ClockPort clock,
      ApplicationEventPublisher events) {
    this.prescriptions = prescriptions;
    this.jdbc = jdbc;
    this.identity = identity;
    this.stock = stock;
    this.dictionary = dictionary;
    this.hospitals = hospitals;
    this.audit = audit;
    this.clock = clock;
    this.events = events;
  }

  public record RxItemView(
      UUID itemId,
      UUID brandId,
      String brandName,
      UUID genericId,
      String genericName,
      String dose,
      String route,
      String frequency,
      String duration,
      boolean dispensed,
      int stockQty,
      List<DrugMatch> sameGenericBrands) {}

  public record RxView(
      Prescription rx,
      List<RxItemView> items,
      boolean blocked,
      boolean alreadyDispensedAlert,
      String patientMrn,
      String patientName) {}

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional(readOnly = true)
  public List<Map<String, Object>> queueSigned(UUID hospitalId) {
    return jdbc.query(
        """
        SELECT p.id, p.patient_id, p.signed_at, pt.mrn, pt.given_name, pt.family_name,
               (SELECT count(*) FROM prescription_item i WHERE i.prescription_id = p.id) AS item_count
        FROM prescription p
        JOIN patient pt ON pt.id = p.patient_id
        JOIN encounter e ON e.id = p.encounter_id
        WHERE p.status = 'SIGNED'
          AND p.revoked_at IS NULL
          AND e.hospital_id = :h
          AND NOT EXISTS (SELECT 1 FROM pharmacy_rx_block b WHERE b.prescription_id = p.id)
        ORDER BY p.signed_at DESC NULLS LAST
        LIMIT 100
        """,
        Map.of("h", hospitalId),
        (rs, n) -> {
          Map<String, Object> m = new HashMap<>();
          m.put("id", rs.getObject("id", UUID.class));
          m.put("patientId", rs.getObject("patient_id", UUID.class));
          m.put(
              "signedAt",
              rs.getTimestamp("signed_at") == null
                  ? null
                  : rs.getTimestamp("signed_at").toInstant());
          m.put("mrn", rs.getString("mrn"));
          m.put("patientName", rs.getString("given_name") + " " + rs.getString("family_name"));
          m.put("itemCount", rs.getInt("item_count"));
          return m;
        });
  }

  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional(readOnly = true)
  public RxView loadForDispense(UUID prescriptionId, UUID hospitalId) {
    Prescription rx =
        prescriptions
            .findById(prescriptionId)
            .orElseThrow(() -> new PharmacyException("pharmacy.error.rxNotFound"));
    assertReadable(rx);
    Map<String, Object> patient =
        jdbc
            .query(
                "SELECT mrn, given_name, family_name FROM patient WHERE id = :id",
                Map.of("id", rx.getPatientId()),
                (rs, n) -> {
                  Map<String, Object> m = new HashMap<>();
                  m.put("mrn", rs.getString("mrn"));
                  m.put("name", rs.getString("given_name") + " " + rs.getString("family_name"));
                  return m;
                })
            .stream()
            .findFirst()
            .orElse(Map.of("mrn", "", "name", ""));

    boolean blocked = isBlocked(prescriptionId);
    boolean alert =
        jdbc
            .query(
                "SELECT already_dispensed FROM pharmacy_rx_block WHERE prescription_id = :id",
                Map.of("id", prescriptionId),
                (rs, n) -> rs.getBoolean(1))
            .stream()
            .findFirst()
            .orElse(false);

    List<RxItemView> views = new ArrayList<>();
    for (ItemRow item : findItems(prescriptionId)) {
      Optional<DrugMatch> brand = dictionary.findBrand(item.brandId());
      String brandName = brand.map(DrugMatch::brandName).orElse(null);
      String genericName =
          dictionary.findGeneric(item.genericId()).map(g -> g.name()).orElse("?");
      int stockQty = item.brandId() == null ? 0 : stock.available(hospitalId, item.brandId());
      views.add(
          new RxItemView(
              item.id(),
              item.brandId(),
              brandName,
              item.genericId(),
              genericName,
              item.dose(),
              item.route(),
              item.frequency(),
              item.duration(),
              alreadyDispensed(item.id()),
              stockQty,
              dictionary.brandsOfGeneric(item.genericId())));
    }
    return new RxView(
        rx,
        views,
        blocked,
        alert,
        String.valueOf(patient.get("mrn")),
        String.valueOf(patient.get("name")));
  }

  /**
   * Dispense one signed item. Does not modify prescription_item rows (pharmacist cannot edit the
   * Rx). Substitution brand allowed only when an APPROVED substitution request exists.
   */
  @PreAuthorize("hasAnyRole('PHARMACIST','ADMIN')")
  @Transactional
  public UUID dispense(
      UUID prescriptionId,
      UUID itemId,
      UUID batchId,
      int quantity,
      UUID brandIdOverride,
      UUID substitutionRequestId,
      String qrToken,
      String idA,
      String idB,
      UUID pharmacistUserId) {
    if (quantity <= 0) {
      throw new PharmacyException("pharmacy.error.badQty");
    }
    Prescription rx =
        prescriptions
            .findById(prescriptionId)
            .orElseThrow(() -> new PharmacyException("pharmacy.error.rxNotFound"));
    assertReadable(rx);
    if (isBlocked(prescriptionId)) {
      throw new PharmacyException("pharmacy.error.revoked");
    }

    ItemRow item =
        findItems(prescriptionId).stream()
            .filter(i -> i.id().equals(itemId))
            .findFirst()
            .orElseThrow(() -> new PharmacyException("pharmacy.error.itemNotFound"));
    if (alreadyDispensed(itemId)) {
      throw new PharmacyException("pharmacy.error.alreadyDispensed");
    }

    VerifiedIdentity verified = requireIdentity(rx.getPatientId(), qrToken, idA, idB);
    UUID hospitalId = hospitals.requireHospital(pharmacistUserId);

    UUID brandId = item.brandId();
    if (brandIdOverride != null && !brandIdOverride.equals(item.brandId())) {
      brandId =
          approveSubstitutionBrand(
              substitutionRequestId, prescriptionId, itemId, brandIdOverride, item.genericId());
    }
    if (brandId == null) {
      throw new PharmacyException("pharmacy.error.noBrand");
    }
    DrugMatch product =
        dictionary
            .findBrand(brandId)
            .orElseThrow(() -> new PharmacyException("pharmacy.error.unknownBrand"));
    if (!product.genericId().equals(item.genericId())) {
      throw new PharmacyException("pharmacy.error.genericMismatch");
    }

    BatchRow batch = stock.requireUsableBatch(batchId, hospitalId, brandId);
    if (batch.quantity() < quantity) {
      throw new PharmacyException("pharmacy.error.insufficientStock");
    }
    if (!stock.consumeBatch(batchId, quantity)) {
      throw new PharmacyException("pharmacy.error.insufficientStock");
    }

    GeneratedKeyHolder kh = new GeneratedKeyHolder();
    jdbc.update(
        """
        INSERT INTO dispense
          (prescription_id, prescription_item_id, pharmacist_id, hospital_id, quantity, qr_token,
           dispensed_at, created_at, updated_at)
        VALUES (:rx, :item, :pharm, :h, :q, :qr, :now, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("rx", prescriptionId)
            .addValue("item", itemId)
            .addValue("pharm", pharmacistUserId)
            .addValue("h", hospitalId)
            .addValue("q", quantity)
            .addValue("qr", verified.method() == Method.QR ? qrToken : null)
            .addValue("now", java.sql.Timestamp.from(clock.now())),
        kh,
        new String[] {"id"});
    UUID dispenseId = (UUID) kh.getKeys().get("id");

    jdbc.update(
        """
        INSERT INTO dispense_batch
          (dispense_id, batch_id, brand_id, batch_no, expiry_date, substitution_request_id,
           identity_method, created_at, updated_at)
        VALUES (:d, :b, :brand, :bno, :exp, :sub, :idm, :now, :now)
        """,
        new MapSqlParameterSource()
            .addValue("d", dispenseId)
            .addValue("b", batchId)
            .addValue("brand", brandId)
            .addValue("bno", batch.batchNo())
            .addValue("exp", java.sql.Date.valueOf(batch.expiryDate()))
            .addValue("sub", substitutionRequestId)
            .addValue("idm", verified.method().name())
            .addValue("now", java.sql.Timestamp.from(clock.now())));

    audit.record(
        pharmacistUserId,
        rx.getPatientId(),
        "MEDICATION_DISPENSED",
        "dispense",
        dispenseId,
        Map.of(
            "prescriptionId",
            prescriptionId.toString(),
            "itemId",
            itemId.toString(),
            "brandId",
            brandId.toString(),
            "qty",
            quantity,
            "identity",
            verified.method().name()));

    events.publishEvent(
        new DomainEvents.MedicationDispensed(
            dispenseId, prescriptionId, rx.getPatientId(), clock.now()));
    return dispenseId;
  }

  private VerifiedIdentity requireIdentity(
      UUID patientId, String qrToken, String idA, String idB) {
    if (qrToken != null && !qrToken.isBlank()) {
      return identity
          .verifyQr(qrToken)
          .filter(v -> v.patientId().equals(patientId))
          .orElseThrow(() -> new PharmacyException("pharmacy.error.identity"));
    }
    return identity
        .verifyTwoIdentifiers(patientId, idA, idB)
        .orElseThrow(() -> new PharmacyException("pharmacy.error.identity"));
  }

  private UUID approveSubstitutionBrand(
      UUID requestId,
      UUID prescriptionId,
      UUID itemId,
      UUID proposedBrandId,
      UUID expectedGenericId) {
    if (requestId == null) {
      throw new PharmacyException("pharmacy.error.substitutionNeeded");
    }
    List<Map<String, Object>> rows =
        jdbc.query(
            """
            SELECT status, proposed_brand_id, prescription_item_id
            FROM pharmacy_request
            WHERE id = :id AND kind = 'SUBSTITUTION' AND prescription_id = :rx
            """,
            Map.of("id", requestId, "rx", prescriptionId),
            (rs, n) -> {
              Map<String, Object> m = new HashMap<>();
              m.put("status", rs.getString("status"));
              m.put("brand", rs.getObject("proposed_brand_id", UUID.class));
              m.put("item", rs.getObject("prescription_item_id", UUID.class));
              return m;
            });
    if (rows.isEmpty()) {
      throw new PharmacyException("pharmacy.error.substitutionNeeded");
    }
    Map<String, Object> row = rows.get(0);
    if (!"APPROVED".equals(row.get("status"))) {
      throw new PharmacyException("pharmacy.error.substitutionNotApproved");
    }
    if (!itemId.equals(row.get("item"))) {
      throw new PharmacyException("pharmacy.error.substitutionNeeded");
    }
    UUID approvedBrand = (UUID) row.get("brand");
    if (approvedBrand == null || !approvedBrand.equals(proposedBrandId)) {
      throw new PharmacyException("pharmacy.error.substitutionNeeded");
    }
    DrugMatch m =
        dictionary
            .findBrand(approvedBrand)
            .orElseThrow(() -> new PharmacyException("pharmacy.error.unknownBrand"));
    if (!m.genericId().equals(expectedGenericId)) {
      throw new PharmacyException("pharmacy.error.genericMismatch");
    }
    return approvedBrand;
  }

  private void assertReadable(Prescription rx) {
    if (rx.getStatus() == PrescriptionStatus.ENTERED_IN_ERROR || rx.getRevokedAt() != null) {
      throw new PharmacyException("pharmacy.error.revoked");
    }
    if (rx.getStatus() != PrescriptionStatus.SIGNED) {
      throw new PharmacyException("pharmacy.error.notSigned");
    }
  }

  private boolean isBlocked(UUID prescriptionId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM pharmacy_rx_block WHERE prescription_id = :id",
            Map.of("id", prescriptionId),
            Integer.class);
    return n != null && n > 0;
  }

  private boolean alreadyDispensed(UUID itemId) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM dispense WHERE prescription_item_id = :id",
            Map.of("id", itemId),
            Integer.class);
    return n != null && n > 0;
  }

  private record ItemRow(
      UUID id,
      UUID brandId,
      UUID genericId,
      String dose,
      String route,
      String frequency,
      String duration) {}

  private List<ItemRow> findItems(UUID prescriptionId) {
    return jdbc.query(
        """
        SELECT id, brand_id, generic_id, dose, route, frequency, duration
        FROM prescription_item WHERE prescription_id = :rx ORDER BY created_at
        """,
        Map.of("rx", prescriptionId),
        (rs, n) ->
            new ItemRow(
                rs.getObject("id", UUID.class),
                rs.getObject("brand_id", UUID.class),
                rs.getObject("generic_id", UUID.class),
                rs.getString("dose"),
                rs.getString("route"),
                rs.getString("frequency"),
                rs.getString("duration")));
  }
}
