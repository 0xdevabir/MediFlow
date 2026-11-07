package com.mediflow.patient.service;

import com.mediflow.patient.domain.ChangeRequestStatus;
import com.mediflow.patient.domain.ContactChannel;
import com.mediflow.patient.domain.DemographicChangeRequest;
import com.mediflow.patient.domain.PatientProfile;
import com.mediflow.patient.repo.DemographicChangeRequestRepository;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.repo.PatientProfileRepository;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.web.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientProfileService {

  private final PatientLookupRepository patients;
  private final PatientProfileRepository profiles;
  private final DemographicChangeRequestRepository changeRequests;
  private final OtpIssuer otp;
  private final PatientAudit audit;
  private final QrTokenService qrTokens;
  private final ClockPort clock;
  private final CurrentUser currentUser;

  public PatientProfileService(
      PatientLookupRepository patients,
      PatientProfileRepository profiles,
      DemographicChangeRequestRepository changeRequests,
      OtpIssuer otp,
      PatientAudit audit,
      QrTokenService qrTokens,
      ClockPort clock,
      CurrentUser currentUser) {
    this.patients = patients;
    this.profiles = profiles;
    this.changeRequests = changeRequests;
    this.otp = otp;
    this.audit = audit;
    this.qrTokens = qrTokens;
    this.clock = clock;
    this.currentUser = currentUser;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@patientAccess.canReadDemographics(#patientId)")
  public PatientView get(UUID patientId) {
    Patient patient = requirePatient(patientId);
    PatientProfile profile = profiles.findByPatientId(patientId).orElse(null);
    audit.record(patientId, "PATIENT_READ", PatientAudit.PATIENT, patientId);
    return PatientView.of(patient, profile);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('FRONT_DESK') or hasRole('NURSE')")
  public List<PatientView> search(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    return patients.search(query.trim(), org.springframework.data.domain.PageRequest.of(0, 25)).stream()
        .map(
            p -> {
              PatientProfile profile = profiles.findByPatientId(p.getId()).orElse(null);
              return PatientView.of(p, profile);
            })
        .toList();
  }

  /** Contact-only update (phone / email / address). Name and DOB are rejected. */
  @Transactional
  @PreAuthorize("@patientAccess.canUpdateContact(#patientId)")
  public PatientView updateContact(UUID patientId, String phone, String email, String address) {
    Patient patient = requirePatient(patientId);
    PatientProfile profile =
        profiles
            .findByPatientId(patientId)
            .orElseThrow(() -> new PatientRuleException("patient.notFound"));

    String newPhone = blankToNull(phone);
    String newEmail = blankToNull(email);
    boolean phoneChanged = !equalsIgnoreBlank(patient.getPhone(), newPhone);
    boolean emailChanged = !equalsIgnoreBlank(patient.getEmail(), newEmail);

    patient.setPhone(newPhone);
    patient.setEmail(newEmail);
    patient.setAddress(blankToNull(address));
    patients.save(patient);

    if (phoneChanged) {
      profile.setPhoneVerifiedAt(null);
      profile.setIdentityHash(
          BlindIndex.identityHash(
              patient.getGivenName(), patient.getFamilyName(), patient.getDateOfBirth(), newPhone));
    }
    if (emailChanged) {
      profile.setEmailVerifiedAt(null);
    }
    profiles.save(profile);

    if (phoneChanged && newPhone != null) {
      otp.issue(patient, ContactChannel.SMS);
    }
    if (emailChanged && newEmail != null) {
      otp.issue(patient, ContactChannel.EMAIL);
    }

    audit.record(
        patientId,
        "PATIENT_CONTACT_UPDATED",
        PatientAudit.PATIENT,
        patientId,
        Map.of("phoneChanged", phoneChanged, "emailChanged", emailChanged));
    return PatientView.of(patient, profile);
  }

  @Transactional
  @PreAuthorize("@patientAccess.isSelf(#patientId)")
  public DemographicChangeRequest requestDemographicChange(
      UUID patientId,
      String newGivenName,
      String newFamilyName,
      LocalDate newDateOfBirth,
      String reason) {
    requirePatient(patientId);
    if (blank(newGivenName) && blank(newFamilyName) && newDateOfBirth == null) {
      throw new PatientRuleException("patient.change.empty");
    }
    DemographicChangeRequest req = new DemographicChangeRequest();
    req.setPatientId(patientId);
    req.setRequestedBy(currentUser.requireUserId());
    req.setNewGivenName(blankToNull(newGivenName));
    req.setNewFamilyName(blankToNull(newFamilyName));
    req.setNewDateOfBirth(newDateOfBirth);
    req.setReason(blankToNull(reason));
    req.setStatus(ChangeRequestStatus.PENDING);
    changeRequests.save(req);
    audit.record(patientId, "DEMOGRAPHIC_CHANGE_REQUESTED", PatientAudit.CHANGE_REQUEST, req.getId());
    return req;
  }

  @Transactional
  @PreAuthorize("hasRole('FRONT_DESK')")
  public DemographicChangeRequest reviewDemographicChange(
      UUID requestId, boolean approve, String note) {
    DemographicChangeRequest req =
        changeRequests
            .findById(requestId)
            .orElseThrow(() -> new PatientRuleException("patient.change.notFound"));
    if (req.getStatus() != ChangeRequestStatus.PENDING) {
      throw new PatientRuleException("patient.change.notPending");
    }
    Patient patient = requirePatient(req.getPatientId());
    PatientProfile profile =
        profiles
            .findByPatientId(patient.getId())
            .orElseThrow(() -> new PatientRuleException("patient.notFound"));

    req.setReviewedBy(currentUser.requireUserId());
    req.setReviewedAt(clock.now());
    req.setReviewNote(blankToNull(note));
    if (approve) {
      if (req.getNewGivenName() != null) {
        patient.setGivenName(req.getNewGivenName());
      }
      if (req.getNewFamilyName() != null) {
        patient.setFamilyName(req.getNewFamilyName());
      }
      if (req.getNewDateOfBirth() != null) {
        patient.setDateOfBirth(req.getNewDateOfBirth());
      }
      patients.save(patient);
      profile.setIdentityHash(
          BlindIndex.identityHash(
              patient.getGivenName(),
              patient.getFamilyName(),
              patient.getDateOfBirth(),
              patient.getPhone()));
      profiles.save(profile);
      req.setStatus(ChangeRequestStatus.APPROVED);
    } else {
      req.setStatus(ChangeRequestStatus.REJECTED);
    }
    changeRequests.save(req);
    audit.record(
        patient.getId(),
        approve ? "DEMOGRAPHIC_CHANGE_APPROVED" : "DEMOGRAPHIC_CHANGE_REJECTED",
        PatientAudit.CHANGE_REQUEST,
        req.getId());
    return req;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("hasRole('FRONT_DESK')")
  public List<DemographicChangeRequest> listPendingChanges() {
    return changeRequests.findByStatusOrderByCreatedAtAsc(ChangeRequestStatus.PENDING);
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@patientAccess.canReadDemographics(#patientId)")
  public QrTokenService.IssuedToken issueCardToken(UUID patientId) {
    Patient patient = requirePatient(patientId);
    QrTokenService.IssuedToken token = qrTokens.issue(patient.getId(), patient.getMrn());
    audit.record(patientId, "PATIENT_CARD_TOKEN_ISSUED", PatientAudit.PATIENT, patientId);
    return token;
  }

  @Transactional(readOnly = true)
  @PreAuthorize("@patientAccess.canReadDemographics(#patientId)")
  public String cardQrDataUri(UUID patientId) {
    QrTokenService.IssuedToken token = issueCardToken(patientId);
    return qrTokens.qrDataUri(token.token());
  }

  private Patient requirePatient(UUID patientId) {
    return patients
        .findById(patientId)
        .orElseThrow(() -> new PatientRuleException("patient.notFound"));
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String blankToNull(String s) {
    return blank(s) ? null : s.trim();
  }

  private static boolean equalsIgnoreBlank(String a, String b) {
    String left = blankToNull(a);
    String right = blankToNull(b);
    if (left == null && right == null) {
      return true;
    }
    return left != null && left.equals(right);
  }
}
