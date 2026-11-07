package com.mediflow.pharmacy.dictionary;

import java.io.Serializable;
import java.util.UUID;

/**
 * One dictionary hit. {@code brandId} is null when the hit is a generic name. {@code score} is 1.0
 * for exact hits and the trigram similarity for suggestions. {@code illustrative} marks seeded
 * sample rows that are not from the DGDA register.
 */
public record DrugMatch(
    UUID brandId,
    String brandName,
    String strength,
    String form,
    String dgdaRegNo,
    UUID genericId,
    String genericName,
    String atcCode,
    String rxnormCode,
    MatchType matchType,
    double score,
    boolean illustrative)
    implements Serializable {

  public enum MatchType {
    EXACT_BRAND,
    EXACT_GENERIC,
    SUGGESTION
  }

  public GenericRef generic() {
    return new GenericRef(genericId, genericName, atcCode, rxnormCode, illustrative);
  }

  DrugMatch withMatch(MatchType type, double newScore) {
    return new DrugMatch(
        brandId, brandName, strength, form, dgdaRegNo, genericId, genericName, atcCode, rxnormCode,
        type, newScore, illustrative);
  }
}
