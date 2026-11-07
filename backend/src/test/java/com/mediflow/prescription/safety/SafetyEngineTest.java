package com.mediflow.prescription.safety;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mediflow.prescription.domain.CheckType;
import com.mediflow.prescription.safety.PatientSafetyContext.ActiveMedRef;
import com.mediflow.prescription.safety.PatientSafetyContext.AllergyRef;
import com.mediflow.prescription.safety.PatientSafetyContext.ConditionRef;
import com.mediflow.prescription.safety.PatientSafetyContext.VitalRef;
import com.mediflow.prescription.safety.RxDrugDictionary.BrandInfo;
import com.mediflow.prescription.safety.RxDrugDictionary.DoseLimitInfo;
import com.mediflow.prescription.safety.RxDrugDictionary.GenericInfo;
import com.mediflow.prescription.safety.RxDrugDictionary.InteractionInfo;
import com.mediflow.shared.domain.ConflictSeverity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Every contradiction type from DOCX Table 13. */
class SafetyEngineTest {

  private static final UUID AMOX = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID IBUPROFEN = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID WARFARIN = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final UUID ENALAPRIL = UUID.fromString("44444444-4444-4444-4444-444444444444");
  private static final UUID BRAND_AMOX = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final UUID BRAND_IBU_A = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final UUID BRAND_IBU_B = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

  private DictionarySnapshot dict;
  private SafetyEngine engine;

  @BeforeEach
  void setUp() {
    dict =
        new DictionarySnapshot(
            List.of(
                new GenericInfo(AMOX, "Amoxicillin", "J01CA04"),
                new GenericInfo(IBUPROFEN, "Ibuprofen", "M01AE01"),
                new GenericInfo(WARFARIN, "Warfarin", "B01AA03"),
                new GenericInfo(ENALAPRIL, "Enalapril", "C09AA02")),
            List.of(
                new BrandInfo(BRAND_AMOX, "Moxacil", AMOX, "500 mg", "capsule", "DGDA-1"),
                new BrandInfo(BRAND_IBU_A, "Brufen", IBUPROFEN, "400 mg", "tablet", "DGDA-2"),
                new BrandInfo(BRAND_IBU_B, "Nurofen", IBUPROFEN, "400 mg", "tablet", "DGDA-3")),
            List.of(
                new InteractionInfo(
                    IBUPROFEN, WARFARIN, ConflictSeverity.CRITICAL, "NSAID + warfarin bleeding")),
            List.of(
                new DoseLimitInfo(
                    IBUPROFEN,
                    "PAEDIATRIC",
                    "PO",
                    new BigDecimal("5"),
                    new BigDecimal("10"),
                    "mg/kg"),
                new DoseLimitInfo(
                    IBUPROFEN,
                    "ADULT",
                    "PO",
                    new BigDecimal("200"),
                    new BigDecimal("800"),
                    "mg")));
    engine = new SafetyEngine(dict, LocalDate.of(2026, 11, 7));
  }

  @Test
  void allergy_penicillin_hardStop() {
    DraftItemView item =
        new DraftItemView(
            UUID.randomUUID(), BRAND_AMOX, AMOX, "Moxacil", "Amoxicillin", "500 mg", "PO", "TDS", "5d", true);
    PatientSafetyContext ctx =
        basePatient().allergies(List.of(new AllergyRef("Penicillin", null))).build();
    List<SafetyFinding> findings = engine.evaluate(List.of(item), ctx);
    assertTrue(
        findings.stream()
            .anyMatch(
                f ->
                    f.checkType() == CheckType.ALLERGY
                        && f.severity() == ConflictSeverity.CRITICAL
                        && !f.overridable()));
  }

  @Test
  void severeDrugDrug_vsActiveMed_criticalOverridable() {
    DraftItemView item =
        new DraftItemView(
            UUID.randomUUID(),
            BRAND_IBU_A,
            IBUPROFEN,
            "Brufen",
            "Ibuprofen",
            "400 mg",
            "PO",
            "TDS",
            "3d",
            true);
    PatientSafetyContext ctx =
        basePatient()
            .activeMeds(
                List.of(new ActiveMedRef(UUID.randomUUID(), UUID.randomUUID(), WARFARIN, "Warfarin")))
            .build();
    List<SafetyFinding> findings = engine.evaluate(List.of(item), ctx);
    assertTrue(
        findings.stream()
            .anyMatch(
                f ->
                    f.checkType() == CheckType.INTERACTION
                        && f.severity() == ConflictSeverity.CRITICAL
                        && f.overridable()));
  }

  @Test
  void doseOutOfRange_paediatric_critical() {
    DraftItemView item =
        new DraftItemView(
            UUID.randomUUID(),
            BRAND_IBU_A,
            IBUPROFEN,
            "Brufen",
            "Ibuprofen",
            "400 mg",
            "PO",
            "TDS",
            "3d",
            true);
    // 20 kg child → 400 mg = 20 mg/kg > 10 mg/kg max
    PatientSafetyContext ctx =
        basePatient()
            .dob(LocalDate.of(2020, 1, 1))
            .weightKg(new BigDecimal("20"))
            .build();
    List<SafetyFinding> findings = engine.evaluate(List.of(item), ctx);
    assertTrue(
        findings.stream()
            .anyMatch(
                f ->
                    f.checkType() == CheckType.DOSE_RANGE
                        && f.severity() == ConflictSeverity.CRITICAL));
  }

  @Test
  void duplicateTherapy_sameGenericTwoBrands_warning() {
    DraftItemView a =
        new DraftItemView(
            UUID.randomUUID(),
            BRAND_IBU_A,
            IBUPROFEN,
            "Brufen",
            "Ibuprofen",
            "400 mg",
            "PO",
            "BD",
            "3d",
            true);
    DraftItemView b =
        new DraftItemView(
            UUID.randomUUID(),
            BRAND_IBU_B,
            IBUPROFEN,
            "Nurofen",
            "Ibuprofen",
            "400 mg",
            "PO",
            "BD",
            "3d",
            true);
    List<SafetyFinding> findings = engine.evaluate(List.of(a, b), basePatient().build());
    assertEquals(
        2,
        findings.stream().filter(f -> f.checkType() == CheckType.DUPLICATE_THERAPY).count());
    assertTrue(
        findings.stream()
            .filter(f -> f.checkType() == CheckType.DUPLICATE_THERAPY)
            .allMatch(f -> f.severity() == ConflictSeverity.WARNING));
  }

  @Test
  void nsaidWithRenalImpairment_warning() {
    DraftItemView item =
        new DraftItemView(
            UUID.randomUUID(),
            BRAND_IBU_A,
            IBUPROFEN,
            "Brufen",
            "Ibuprofen",
            "400 mg",
            "PO",
            "BD",
            "3d",
            true);
    PatientSafetyContext ctx =
        basePatient()
            .conditions(List.of(new ConditionRef("N18.3", "Chronic kidney disease stage 3")))
            .build();
    assertTrue(
        engine.evaluate(List.of(item), ctx).stream()
            .anyMatch(
                f ->
                    f.checkType() == CheckType.CONDITION
                        && f.severity() == ConflictSeverity.WARNING));
  }

  @Test
  void antihypertensiveMismatch_antihypertensiveWithLowBp_warning() {
    DraftItemView item =
        new DraftItemView(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ENALAPRIL,
            "Enaprin",
            "Enalapril",
            "10 mg",
            "PO",
            "OD",
            "30d",
            true);
    PatientSafetyContext ctx =
        basePatient()
            .vitals(List.of(new VitalRef("BP", null, "85/50")))
            .build();
    assertTrue(
        engine.evaluate(List.of(item), ctx).stream()
            .anyMatch(
                f -> f.checkType() == CheckType.VITALS && f.severity() == ConflictSeverity.WARNING));
  }

  @Test
  void pregnancyCaution_warning() {
    DraftItemView item =
        new DraftItemView(
            UUID.randomUUID(),
            UUID.randomUUID(),
            WARFARIN,
            "Coumadin",
            "Warfarin",
            "5 mg",
            "PO",
            "OD",
            "30d",
            true);
    PatientSafetyContext ctx = basePatient().pregnancy("pregnant").build();
    assertTrue(
        engine.evaluate(List.of(item), ctx).stream()
            .anyMatch(
                f ->
                    f.checkType() == CheckType.PREGNANCY
                        && f.severity() == ConflictSeverity.WARNING));
  }

  @Test
  void unknownItem_unknownSeverity() {
    DraftItemView item = DraftItemView.unknown(UUID.randomUUID(), "NotARealBrand");
    List<SafetyFinding> findings = engine.evaluate(List.of(item), basePatient().build());
    assertEquals(1, findings.size());
    assertEquals(CheckType.UNKNOWN_ITEM, findings.get(0).checkType());
    assertEquals(ConflictSeverity.UNKNOWN, findings.get(0).severity());
  }

  @Test
  void hasUnresolvedCritical_respectsNonOverridableAndAcks() {
    UUID itemId = UUID.randomUUID();
    SafetyFinding allergy =
        SafetyFinding.of(
            itemId,
            CheckType.ALLERGY,
            ConflictSeverity.CRITICAL,
            "allergy|x|pen",
            "rx.safety.allergy",
            "pen",
            false);
    assertTrue(SafetyEngine.hasUnresolvedCritical(List.of(allergy), Set.of("allergy|x|pen")));

    SafetyFinding interaction =
        SafetyFinding.of(
            itemId,
            CheckType.INTERACTION,
            ConflictSeverity.CRITICAL,
            "interaction|x|y",
            "rx.safety.interaction",
            "bleed",
            true);
    assertTrue(SafetyEngine.hasUnresolvedCritical(List.of(interaction), Set.of()));
    assertFalse(SafetyEngine.hasUnresolvedCritical(List.of(interaction), Set.of("interaction|x|y")));
  }

  private static PatientBuilder basePatient() {
    return new PatientBuilder();
  }

  private static final class PatientBuilder {
    private LocalDate dob = LocalDate.of(1990, 1, 1);
    private String pregnancy = null;
    private BigDecimal weightKg = new BigDecimal("70");
    private List<AllergyRef> allergies = List.of();
    private List<ConditionRef> conditions = List.of();
    private List<ActiveMedRef> activeMeds = List.of();
    private List<VitalRef> vitals = List.of();

    PatientBuilder dob(LocalDate d) {
      this.dob = d;
      return this;
    }

    PatientBuilder pregnancy(String p) {
      this.pregnancy = p;
      return this;
    }

    PatientBuilder weightKg(BigDecimal w) {
      this.weightKg = w;
      return this;
    }

    PatientBuilder allergies(List<AllergyRef> a) {
      this.allergies = a;
      return this;
    }

    PatientBuilder conditions(List<ConditionRef> c) {
      this.conditions = c;
      return this;
    }

    PatientBuilder activeMeds(List<ActiveMedRef> m) {
      this.activeMeds = m;
      return this;
    }

    PatientBuilder vitals(List<VitalRef> v) {
      this.vitals = v;
      return this;
    }

    PatientSafetyContext build() {
      return new PatientSafetyContext(
          UUID.randomUUID(),
          dob,
          "F",
          pregnancy,
          weightKg,
          allergies,
          conditions,
          activeMeds,
          vitals);
    }
  }
}
