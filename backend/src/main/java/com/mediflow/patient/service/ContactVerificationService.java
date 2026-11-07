package com.mediflow.patient.service;

import com.mediflow.patient.domain.ContactChannel;
import com.mediflow.patient.domain.PatientProfile;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.repo.PatientProfileRepository;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.ClockPort;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactVerificationService {

  private final PatientLookupRepository patients;
  private final PatientProfileRepository profiles;
  private final OtpIssuer otp;
  private final PatientAudit audit;
  private final ClockPort clock;

  public ContactVerificationService(
      PatientLookupRepository patients,
      PatientProfileRepository profiles,
      OtpIssuer otp,
      PatientAudit audit,
      ClockPort clock) {
    this.patients = patients;
    this.profiles = profiles;
    this.otp = otp;
    this.audit = audit;
    this.clock = clock;
  }

  @Transactional
  @PreAuthorize("@patientAccess.canUpdateContact(#patientId)")
  public void resend(UUID patientId, ContactChannel channel) {
    Patient patient =
        patients.findById(patientId).orElseThrow(() -> new PatientRuleException("patient.notFound"));
    otp.issue(patient, channel);
    audit.record(
        patientId,
        "CONTACT_OTP_SENT",
        PatientAudit.CONTACT_VERIFICATION,
        patientId,
        Map.of("channel", channel.name()));
  }

  @Transactional
  @PreAuthorize("@patientAccess.canUpdateContact(#patientId)")
  public OtpIssuer.Outcome verify(UUID patientId, ContactChannel channel, String code) {
    patients.findById(patientId).orElseThrow(() -> new PatientRuleException("patient.notFound"));
    OtpIssuer.Outcome outcome = otp.check(patientId, channel, code);
    if (outcome == OtpIssuer.Outcome.VERIFIED) {
      PatientProfile profile =
          profiles
              .findByPatientId(patientId)
              .orElseThrow(() -> new PatientRuleException("patient.notFound"));
      if (channel == ContactChannel.SMS) {
        profile.setPhoneVerifiedAt(clock.now());
      } else {
        profile.setEmailVerifiedAt(clock.now());
      }
      profiles.save(profile);
      audit.record(
          patientId,
          "CONTACT_VERIFIED",
          PatientAudit.CONTACT_VERIFICATION,
          patientId,
          Map.of("channel", channel.name()));
    } else {
      audit.record(
          patientId,
          "CONTACT_VERIFY_FAILED",
          PatientAudit.CONTACT_VERIFICATION,
          patientId,
          Map.of("channel", channel.name(), "outcome", outcome.name()));
    }
    return outcome;
  }
}
