package com.mediflow.audit.chain;

import java.util.ArrayList;
import java.util.List;

/**
 * Walks rows in {@code seq} order and checks each one:
 *
 * <ul>
 *   <li><b>link</b>: its prev_hash equals the previous row's stored hash (detects deleted or
 *       inserted rows);
 *   <li><b>content</b>: SHA-256(prev_hash || row) equals its stored hash (detects edited rows).
 * </ul>
 *
 * It continues after a break (using the stored hash as the next link) so that one bad row does not
 * hide later ones; the report lists the first few broken sequence numbers.
 */
public final class ChainVerification {

  public static final int MAX_REPORTED = 20;

  private String expectedPrev = AuditHasher.GENESIS;
  private long rows;
  private long brokenCount;
  private final List<Long> brokenSeqs = new ArrayList<>();
  private final List<String> reasons = new ArrayList<>();
  private Long headSeq;
  private String headHash;

  public void accept(AuditRow row) {
    rows++;
    String problem = null;
    if (row.prevHash() == null || !row.prevHash().equals(expectedPrev)) {
      problem = "link";
    } else if (!AuditHasher.hash(row.prevHash(), row).equals(row.hash())) {
      problem = "content";
    }
    if (problem != null) {
      brokenCount++;
      if (brokenSeqs.size() < MAX_REPORTED) {
        brokenSeqs.add(row.seq());
        reasons.add(problem);
      }
    }
    expectedPrev = row.hash();
    headSeq = row.seq();
    headHash = row.hash();
  }

  public Result result() {
    return new Result(
        brokenCount == 0, rows, brokenCount, List.copyOf(brokenSeqs), List.copyOf(reasons), headSeq, headHash);
  }

  /**
   * @param brokenSeqs first {@value #MAX_REPORTED} broken rows
   * @param reasons "link" or "content", parallel to {@code brokenSeqs}
   */
  public record Result(
      boolean ok,
      long rowsChecked,
      long brokenCount,
      List<Long> brokenSeqs,
      List<String> reasons,
      Long headSeq,
      String headHash) {

    public Long firstBrokenSeq() {
      return brokenSeqs.isEmpty() ? null : brokenSeqs.get(0);
    }
  }
}
