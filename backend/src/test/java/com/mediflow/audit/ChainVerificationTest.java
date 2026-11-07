package com.mediflow.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mediflow.audit.chain.AuditHasher;
import com.mediflow.audit.chain.AuditRow;
import com.mediflow.audit.chain.CanonicalJson;
import com.mediflow.audit.chain.ChainVerification;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChainVerificationTest {

  private final CanonicalJson json = new CanonicalJson(new ObjectMapper().findAndRegisterModules());

  private List<AuditRow> chain(int n) {
    List<AuditRow> rows = new ArrayList<>();
    String prev = AuditHasher.GENESIS;
    for (int i = 1; i <= n; i++) {
      AuditRow r =
          new AuditRow(
              i,
              UUID.randomUUID(),
              Instant.parse("2026-11-07T10:00:00.123456Z").plusSeconds(i),
              UUID.randomUUID(),
              i % 2 == 0 ? UUID.randomUUID() : null,
              "PATIENT_READ",
              "Patient",
              UUID.randomUUID(),
              json.ofMap(Map.of("purpose", "treatment", "n", i)),
              prev,
              null);
      String hash = AuditHasher.hash(prev, r);
      rows.add(withHash(r, hash));
      prev = hash;
    }
    return rows;
  }

  private static AuditRow withHash(AuditRow r, String hash) {
    return new AuditRow(
        r.seq(), r.id(), r.occurredAt(), r.actorUserId(), r.patientId(), r.action(), r.resourceType(),
        r.resourceId(), r.details(), r.prevHash(), hash);
  }

  private static ChainVerification.Result verify(List<AuditRow> rows) {
    ChainVerification v = new ChainVerification();
    rows.forEach(v::accept);
    return v.result();
  }

  @Test
  void intactChainVerifies() {
    ChainVerification.Result r = verify(chain(5));
    assertThat(r.ok()).isTrue();
    assertThat(r.rowsChecked()).isEqualTo(5);
    assertThat(r.headSeq()).isEqualTo(5);
  }

  @Test
  void editedRowIsDetectedAtItsSequence() {
    List<AuditRow> rows = chain(5);
    AuditRow orig = rows.get(2);
    rows.set(
        2,
        new AuditRow(
            orig.seq(), orig.id(), orig.occurredAt(), orig.actorUserId(), orig.patientId(), "TAMPERED",
            orig.resourceType(), orig.resourceId(), orig.details(), orig.prevHash(), orig.hash()));
    ChainVerification.Result r = verify(rows);
    assertThat(r.ok()).isFalse();
    assertThat(r.firstBrokenSeq()).isEqualTo(3);
    assertThat(r.brokenSeqs()).containsExactly(3L);
    assertThat(r.reasons()).containsExactly("content");
  }

  @Test
  void deletedRowBreaksTheNextLink() {
    List<AuditRow> rows = chain(5);
    rows.remove(1);
    ChainVerification.Result r = verify(rows);
    assertThat(r.ok()).isFalse();
    assertThat(r.firstBrokenSeq()).isEqualTo(3);
    assertThat(r.reasons()).containsExactly("link");
  }

  @Test
  void rehashedRowStillBreaksFollowingLink() {
    List<AuditRow> rows = chain(4);
    AuditRow orig = rows.get(1);
    AuditRow forged =
        new AuditRow(
            orig.seq(), orig.id(), orig.occurredAt(), orig.actorUserId(), orig.patientId(), "FORGED",
            orig.resourceType(), orig.resourceId(), orig.details(), orig.prevHash(), null);
    rows.set(1, withHash(forged, AuditHasher.hash(orig.prevHash(), forged)));
    ChainVerification.Result r = verify(rows);
    assertThat(r.ok()).isFalse();
    assertThat(r.firstBrokenSeq()).isEqualTo(3);
  }

  @Test
  void canonicalJsonIgnoresKeyOrderAndNumberFormatting() {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("b", 1.50);
    a.put("a", Map.of("y", 2, "x", "v"));
    String fromMap = json.ofMap(a);
    // what jsonb might hand back: different order, whitespace, trailing zeros
    String fromDb = json.ofText("{\"a\": {\"x\": \"v\", \"y\": 2.0}, \"b\": 1.5}");
    assertThat(fromMap).isEqualTo(fromDb).isEqualTo("{\"a\":{\"x\":\"v\",\"y\":2},\"b\":1.5}");
    assertThat(json.ofMap(Map.of())).isNull();
    assertThat(json.ofText(null)).isNull();
  }

  @Test
  void nullAndEmptyStringHashDifferently() {
    AuditRow withNull =
        new AuditRow(1, UUID.randomUUID(), Instant.EPOCH, null, null, "A", null, null, null, null, null);
    AuditRow withEmpty =
        new AuditRow(
            1, withNull.id(), Instant.EPOCH, null, null, "A", "", null, null, null, null);
    assertThat(AuditHasher.hash(AuditHasher.GENESIS, withNull))
        .isNotEqualTo(AuditHasher.hash(AuditHasher.GENESIS, withEmpty))
        .hasSize(64);
  }
}
