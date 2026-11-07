package com.mediflow.prescription.safety;

import com.mediflow.prescription.domain.CheckType;
import com.mediflow.prescription.safety.PatientSafetyContext.ActiveMedRef;
import com.mediflow.prescription.safety.PatientSafetyContext.AllergyRef;
import com.mediflow.prescription.safety.PatientSafetyContext.ConditionRef;
import com.mediflow.prescription.safety.PatientSafetyContext.VitalRef;
import com.mediflow.prescription.safety.RxDrugDictionary.DoseLimitInfo;
import com.mediflow.prescription.safety.RxDrugDictionary.GenericInfo;
import com.mediflow.prescription.safety.RxDrugDictionary.InteractionInfo;
import com.mediflow.shared.domain.ConflictSeverity;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic contradiction engine (DOCX Table 13 / PLAN §6). Runs in memory against a {@link
 * RxDrugDictionary} snapshot; p95 target ≤ 300 ms.
 *
 * <p>Checks every draft change for typed, AI and manual items alike. Works at the <em>generic</em>
 * level.
 */
public final class SafetyEngine {

  private static final Set<String> NSAID_ATC_PREFIXES = Set.of("M01A");
  private static final Set<String> NSAID_NAME_HINTS =
      Set.of(
          "ibuprofen",
          "diclofenac",
          "naproxen",
          "indomethacin",
          "ketorolac",
          "mefenamic",
          "celecoxib",
          "aspirin",
          "acetylsalicylic");
  private static final Set<String> ANTIHYPERTENSIVE_ATC_PREFIXES =
      Set.of("C02", "C03", "C07", "C08", "C09");
  private static final Set<String> PREGNANCY_UNSAFE_HINTS =
      Set.of(
          "isotretinoin",
          "thalidomide",
          "warfarin",
          "valproate",
          "valproic",
          "methotrexate",
          "misoprostol",
          "finasteride",
          "acitretin");

  private final RxDrugDictionary dictionary;
  private final LocalDate today;

  public SafetyEngine(RxDrugDictionary dictionary) {
    this(dictionary, LocalDate.now());
  }

  public SafetyEngine(RxDrugDictionary dictionary, LocalDate today) {
    this.dictionary = Objects.requireNonNull(dictionary);
    this.today = today == null ? LocalDate.now() : today;
  }

  public List<SafetyFinding> evaluate(List<DraftItemView> items, PatientSafetyContext patient) {
    List<SafetyFinding> out = new ArrayList<>();
    if (items == null || items.isEmpty()) {
      return out;
    }
    PatientSafetyContext ctx =
        patient == null
            ? new PatientSafetyContext(
                null, null, null, null, null, List.of(), List.of(), List.of(), List.of())
            : patient;

    for (DraftItemView item : items) {
      if (item == null) {
        continue;
      }
      if (!item.knownInDictionary() || item.genericId() == null) {
        out.add(
            SafetyFinding.of(
                item.itemId(),
                CheckType.UNKNOWN_ITEM,
                ConflictSeverity.UNKNOWN,
                fp("unknown", item.itemId(), item.brandName()),
                "rx.safety.unknown_item",
                item.brandName(),
                false));
        continue;
      }
      out.addAll(checkAllergy(item, ctx));
      out.addAll(checkDose(item, ctx));
      out.addAll(checkCondition(item, ctx));
      out.addAll(checkPregnancy(item, ctx));
      out.addAll(checkVitals(item, ctx));
    }

    out.addAll(checkInteractions(items, ctx));
    out.addAll(checkDuplicates(items));
    return List.copyOf(out);
  }

  /** Highest severity among findings for one item (draft-wide findings ignored). */
  public static ConflictSeverity maxSeverityFor(UUID itemId, List<SafetyFinding> findings) {
    ConflictSeverity max = null;
    for (SafetyFinding f : findings) {
      if (f.itemId() == null || !f.itemId().equals(itemId)) {
        continue;
      }
      if (max == null || f.severity().ordinal() < max.ordinal()) {
        max = f.severity();
      }
    }
    return max;
  }

  public static boolean hasUnresolvedCritical(
      List<SafetyFinding> findings, Set<String> ackedFingerprints) {
    Set<String> acked = ackedFingerprints == null ? Set.of() : ackedFingerprints;
    for (SafetyFinding f : findings) {
      if (f.severity() != ConflictSeverity.CRITICAL) {
        continue;
      }
      if (!f.overridable()) {
        return true;
      }
      if (!acked.contains(f.fingerprint())) {
        return true;
      }
      // interaction overrides need a second confirmation — caller filters via acked set that only
      // includes second-confirmed fingerprints when required
    }
    return false;
  }

  private List<SafetyFinding> checkAllergy(DraftItemView item, PatientSafetyContext ctx) {
    List<SafetyFinding> out = new ArrayList<>();
    Optional<GenericInfo> generic = dictionary.generic(item.genericId());
    String gName = generic.map(GenericInfo::name).orElse(item.genericName());
    String atc = generic.map(GenericInfo::atcCode).orElse(null);
    for (AllergyRef a : ctx.allergies()) {
      if (a == null || (blank(a.substance()) && blank(a.substanceCode()))) {
        continue;
      }
      if (matchesAllergy(a, gName, atc, item.brandName())) {
        out.add(
            SafetyFinding.of(
                item.itemId(),
                CheckType.ALLERGY,
                ConflictSeverity.CRITICAL,
                fp("allergy", item.itemId(), a.substance()),
                "rx.safety.allergy",
                a.substance(),
                false));
      }
    }
    return out;
  }

  private boolean matchesAllergy(AllergyRef a, String genericName, String atc, String brand) {
    String substance = norm(a.substance());
    String code = norm(a.substanceCode());
    if (!code.isEmpty() && atc != null && norm(atc).startsWith(code)) {
      return true;
    }
    if (substance.isEmpty()) {
      return false;
    }
    if (norm(genericName).contains(substance) || substance.contains(norm(genericName))) {
      return true;
    }
    if (brand != null && (norm(brand).contains(substance) || substance.contains(norm(brand)))) {
      return true;
    }
    // penicillin class: allergy to "penicillin" hits amoxicillin etc.
    if (substance.contains("penicillin")
        && (norm(genericName).contains("cillin")
            || (atc != null && atc.toUpperCase(Locale.ROOT).startsWith("J01C")))) {
      return true;
    }
    return false;
  }

  private List<SafetyFinding> checkDose(DraftItemView item, PatientSafetyContext ctx) {
    List<SafetyFinding> out = new ArrayList<>();
    List<DoseLimitInfo> limits = dictionary.doseLimits(item.genericId());
    if (limits.isEmpty()) {
      return out;
    }
    Optional<DoseParser.ParsedDose> parsed = DoseParser.parse(item.dose());
    if (parsed.isEmpty()) {
      out.add(
          SafetyFinding.of(
              item.itemId(),
              CheckType.DOSE_UNVERIFIED,
              ConflictSeverity.UNKNOWN,
              fp("dose-unverified", item.itemId(), item.dose()),
              "rx.safety.dose_unverified",
              item.dose(),
              false));
      return out;
    }
    DoseParser.ParsedDose dose = parsed.get();
    int age = ctx.ageYears(today);
    String population = pickPopulation(age, ctx.pregnant());
    BigDecimal weight = ctx.weightKg();
    int times = DoseParser.timesPerDay(item.frequency());

    for (DoseLimitInfo lim : limits) {
      if (!populationMatches(lim.population(), population, age)) {
        continue;
      }
      if (!blank(lim.route())
          && !blank(item.route())
          && !norm(lim.route()).equals(norm(item.route()))) {
        continue;
      }
      String unit = lim.unit() == null ? "" : lim.unit().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
      BigDecimal compare = dose.amount();
      boolean perKg = unit.contains("/kg");
      boolean perDay = unit.contains("/day");
      if (perKg) {
        if (weight == null || weight.compareTo(BigDecimal.ZERO) <= 0) {
          out.add(
              SafetyFinding.of(
                  item.itemId(),
                  CheckType.DOSE_UNVERIFIED,
                  ConflictSeverity.UNKNOWN,
                  fp("dose-weight", item.itemId(), lim.unit()),
                  "rx.safety.dose_needs_weight",
                  lim.unit(),
                  false));
          continue;
        }
        compare = dose.amount().divide(weight, 6, RoundingMode.HALF_UP);
      }
      if (perDay) {
        compare = compare.multiply(BigDecimal.valueOf(times));
      }
      // unit family check (mg vs g)
      if (!unitCompatible(dose.unit(), unit)) {
        continue;
      }
      BigDecimal scaled = scaleToLimitUnit(compare, dose.unit(), unit);
      if (lim.maxDose() != null && scaled.compareTo(lim.maxDose()) > 0) {
        out.add(
            SafetyFinding.of(
                item.itemId(),
                CheckType.DOSE_RANGE,
                ConflictSeverity.CRITICAL,
                fp("dose-max", item.itemId(), lim.maxDose().toPlainString()),
                "rx.safety.dose_high",
                "max=" + lim.maxDose() + " " + lim.unit(),
                false));
      }
      if (lim.minDose() != null && scaled.compareTo(lim.minDose()) < 0) {
        out.add(
            SafetyFinding.of(
                item.itemId(),
                CheckType.DOSE_RANGE,
                ConflictSeverity.CRITICAL,
                fp("dose-min", item.itemId(), lim.minDose().toPlainString()),
                "rx.safety.dose_low",
                "min=" + lim.minDose() + " " + lim.unit(),
                false));
      }
    }
    return out;
  }

  private List<SafetyFinding> checkCondition(DraftItemView item, PatientSafetyContext ctx) {
    List<SafetyFinding> out = new ArrayList<>();
    if (!isNsaid(item)) {
      return out;
    }
    for (ConditionRef c : ctx.conditions()) {
      if (c == null) {
        continue;
      }
      String display = norm(c.display());
      String icd = norm(c.icd10Code());
      boolean renal =
          display.contains("renal")
              || display.contains("ckd")
              || display.contains("kidney")
              || icd.startsWith("n17")
              || icd.startsWith("n18")
              || icd.startsWith("n19");
      if (renal) {
        out.add(
            SafetyFinding.of(
                item.itemId(),
                CheckType.CONDITION,
                ConflictSeverity.WARNING,
                fp("condition-nsaid-renal", item.itemId(), c.icd10Code()),
                "rx.safety.nsaid_renal",
                c.display(),
                true));
      }
    }
    return out;
  }

  private List<SafetyFinding> checkPregnancy(DraftItemView item, PatientSafetyContext ctx) {
    List<SafetyFinding> out = new ArrayList<>();
    if (!ctx.pregnant()) {
      return out;
    }
    String gName = norm(item.genericName());
    if (gName.isEmpty()) {
      gName =
          dictionary.generic(item.genericId()).map(g -> norm(g.name())).orElse("");
    }
    for (String hint : PREGNANCY_UNSAFE_HINTS) {
      if (gName.contains(hint)) {
        out.add(
            SafetyFinding.of(
                item.itemId(),
                CheckType.PREGNANCY,
                ConflictSeverity.WARNING,
                fp("pregnancy", item.itemId(), hint),
                "rx.safety.pregnancy",
                item.genericName(),
                true));
        break;
      }
    }
    int age = ctx.ageYears(today);
    if (age >= 0 && age < 12) {
      // paediatric caution placeholder when pregnant+child shouldn't happen; skip
    }
    return out;
  }

  private List<SafetyFinding> checkVitals(DraftItemView item, PatientSafetyContext ctx) {
    List<SafetyFinding> out = new ArrayList<>();
    if (!isAntihypertensive(item)) {
      return out;
    }
    Optional<BigDecimal> systolic = latestSystolic(ctx.latestVitals());
    if (systolic.isPresent() && systolic.get().compareTo(new BigDecimal("90")) < 0) {
      out.add(
          SafetyFinding.of(
              item.itemId(),
              CheckType.VITALS,
              ConflictSeverity.WARNING,
              fp("vitals-lowbp", item.itemId(), systolic.get().toPlainString()),
              "rx.safety.antihypertensive_low_bp",
              "SBP=" + systolic.get().toPlainString(),
              true));
    }
    return out;
  }

  private List<SafetyFinding> checkInteractions(
      List<DraftItemView> items, PatientSafetyContext ctx) {
    List<SafetyFinding> out = new ArrayList<>();
    List<DraftItemView> known =
        items.stream().filter(i -> i != null && i.genericId() != null).toList();

    // vs other draft items
    for (int i = 0; i < known.size(); i++) {
      for (int j = i + 1; j < known.size(); j++) {
        DraftItemView a = known.get(i);
        DraftItemView b = known.get(j);
        dictionary
            .interaction(a.genericId(), b.genericId())
            .filter(ix -> ix.severity() == ConflictSeverity.CRITICAL)
            .ifPresent(
                ix -> {
                  out.add(interactionFinding(a, b.genericId(), ix));
                  out.add(interactionFinding(b, a.genericId(), ix));
                });
      }
    }
    // vs active meds
    for (DraftItemView item : known) {
      for (ActiveMedRef med : ctx.activeMeds()) {
        if (med == null || med.genericId() == null) {
          continue;
        }
        dictionary
            .interaction(item.genericId(), med.genericId())
            .filter(ix -> ix.severity() == ConflictSeverity.CRITICAL)
            .ifPresent(ix -> out.add(interactionFinding(item, med.genericId(), ix)));
      }
    }
    return out;
  }

  private SafetyFinding interactionFinding(
      DraftItemView item, UUID otherGenericId, InteractionInfo ix) {
    return SafetyFinding.of(
        item.itemId(),
        CheckType.INTERACTION,
        ConflictSeverity.CRITICAL,
        fp("interaction", item.itemId(), otherGenericId.toString()),
        "rx.safety.interaction",
        ix.description(),
        true); // change or override with reason + second confirmation
  }

  private List<SafetyFinding> checkDuplicates(List<DraftItemView> items) {
    List<SafetyFinding> out = new ArrayList<>();
    Map<UUID, List<DraftItemView>> byGeneric = new HashMap<>();
    for (DraftItemView item : items) {
      if (item == null || item.genericId() == null) {
        continue;
      }
      byGeneric.computeIfAbsent(item.genericId(), k -> new ArrayList<>()).add(item);
    }
    for (Map.Entry<UUID, List<DraftItemView>> e : byGeneric.entrySet()) {
      List<DraftItemView> group = e.getValue();
      if (group.size() < 2) {
        continue;
      }
      Set<UUID> brandIds = new HashSet<>();
      for (DraftItemView i : group) {
        if (i.brandId() != null) {
          brandIds.add(i.brandId());
        }
      }
      // same generic, two brands (or two lines)
      if (brandIds.size() >= 2 || group.size() >= 2) {
        for (DraftItemView i : group) {
          out.add(
              SafetyFinding.of(
                  i.itemId(),
                  CheckType.DUPLICATE_THERAPY,
                  ConflictSeverity.WARNING,
                  fp("dup", i.itemId(), e.getKey().toString()),
                  "rx.safety.duplicate_therapy",
                  dictionary.generic(e.getKey()).map(GenericInfo::name).orElse(e.getKey().toString()),
                  true));
        }
      }
    }
    return out;
  }

  private boolean isNsaid(DraftItemView item) {
    Optional<GenericInfo> g = dictionary.generic(item.genericId());
    if (g.isPresent()) {
      String atc = g.get().atcCode();
      if (atc != null) {
        String u = atc.toUpperCase(Locale.ROOT);
        for (String p : NSAID_ATC_PREFIXES) {
          if (u.startsWith(p)) {
            return true;
          }
        }
      }
      String name = norm(g.get().name());
      for (String h : NSAID_NAME_HINTS) {
        if (name.contains(h)) {
          return true;
        }
      }
    }
    String brand = norm(item.brandName());
    for (String h : NSAID_NAME_HINTS) {
      if (brand.contains(h)) {
        return true;
      }
    }
    return false;
  }

  private boolean isAntihypertensive(DraftItemView item) {
    Optional<GenericInfo> g = dictionary.generic(item.genericId());
    if (g.isEmpty() || g.get().atcCode() == null) {
      return false;
    }
    String atc = g.get().atcCode().toUpperCase(Locale.ROOT);
    for (String p : ANTIHYPERTENSIVE_ATC_PREFIXES) {
      if (atc.startsWith(p)) {
        return true;
      }
    }
    return false;
  }

  private static Optional<BigDecimal> latestSystolic(List<VitalRef> vitals) {
    if (vitals == null) {
      return Optional.empty();
    }
    for (VitalRef v : vitals) {
      if (v == null) {
        continue;
      }
      String type = norm(v.vitalType());
      if (type.contains("bp") || type.contains("blood_pressure") || type.equals("sbp")) {
        if (v.valueNumeric() != null) {
          return Optional.of(v.valueNumeric());
        }
        if (v.valueText() != null && v.valueText().contains("/")) {
          String sys = v.valueText().split("/")[0].trim();
          try {
            return Optional.of(new BigDecimal(sys));
          } catch (NumberFormatException ignored) {
            // continue
          }
        }
      }
    }
    return Optional.empty();
  }

  private static String pickPopulation(int age, boolean pregnant) {
    if (pregnant) {
      return "PREGNANCY";
    }
    if (age < 0) {
      return "ADULT";
    }
    if (age < 12) {
      return "PAEDIATRIC";
    }
    if (age >= 65) {
      return "GERIATRIC";
    }
    return "ADULT";
  }

  private static boolean populationMatches(String limitPop, String actual, int age) {
    if (blank(limitPop) || "ALL".equalsIgnoreCase(limitPop)) {
      return true;
    }
    String p = limitPop.trim().toUpperCase(Locale.ROOT);
    if (p.equals(actual)) {
      return true;
    }
    // paediatric limits also apply when age < 12 even if actual labeled PAEDIATRIC
    if (p.startsWith("PAED") && age >= 0 && age < 12) {
      return true;
    }
    if (p.equals("ADULT") && age >= 12 && age < 65) {
      return true;
    }
    return false;
  }

  private static boolean unitCompatible(String doseUnit, String limitUnit) {
    String d = baseUnit(doseUnit);
    String l = baseUnit(limitUnit);
    return d.equals(l) || (d.equals("g") && l.equals("mg")) || (d.equals("mg") && l.equals("g"));
  }

  private static BigDecimal scaleToLimitUnit(BigDecimal amount, String doseUnit, String limitUnit) {
    String d = baseUnit(doseUnit);
    String l = baseUnit(limitUnit);
    if (d.equals("g") && l.equals("mg")) {
      return amount.multiply(new BigDecimal("1000"));
    }
    if (d.equals("mg") && l.equals("g")) {
      return amount.divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
    }
    return amount;
  }

  private static String baseUnit(String unit) {
    if (unit == null) {
      return "";
    }
    String u = unit.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    int slash = u.indexOf('/');
    if (slash >= 0) {
      u = u.substring(0, slash);
    }
    return u;
  }

  private static String fp(String kind, UUID itemId, String extra) {
    return kind + "|" + (itemId == null ? "-" : itemId) + "|" + norm(extra);
  }

  private static String norm(String s) {
    return s == null ? "" : s.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }
}
