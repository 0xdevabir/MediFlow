package com.mediflow.audit.chain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** {@code hash = SHA-256(prev_hash || canonical row)}, hex encoded. */
public final class AuditHasher {

  /** prev_hash of the first event in the chain. */
  public static final String GENESIS = "0".repeat(64);

  private static final char SEP = '\u001F'; // unit separator
  private static final String NULL = "\u0000";

  private AuditHasher() {}

  /** Canonical row text: every hashed field in fixed order, nulls distinguishable from "". */
  public static String canonicalRow(AuditRow r) {
    StringBuilder sb = new StringBuilder(256);
    append(sb, r.id());
    append(sb, r.occurredAt());
    append(sb, r.actorUserId());
    append(sb, r.patientId());
    append(sb, r.action());
    append(sb, r.resourceType());
    append(sb, r.resourceId());
    append(sb, r.details());
    return sb.toString();
  }

  public static String hash(String prevHash, AuditRow row) {
    Objects.requireNonNull(prevHash, "prevHash");
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      md.update(prevHash.getBytes(StandardCharsets.UTF_8));
      md.update((byte) SEP);
      md.update(canonicalRow(row).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(md.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static void append(StringBuilder sb, Object value) {
    sb.append(value == null ? NULL : value.toString()).append(SEP);
  }
}
