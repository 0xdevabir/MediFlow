package com.mediflow.patient.service;

import com.mediflow.patient.domain.PatientProfile;
import com.mediflow.patient.domain.RegistrationSource;
import com.mediflow.patient.repo.PatientLookupRepository;
import com.mediflow.patient.repo.PatientProfileRepository;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.shared.web.CurrentUser;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientRegistrationService {

  private final PatientLookupRepository patients;
  private final PatientProfileRepository profiles;
  private final AppUserRepository users;
  private final MrnGenerator mrns;
  private final PatientConsentGateway consent;
  private final OtpIssuer otp;
  private final PatientAudit audit;
  private final PasswordEncoder passwords;
  private final ApplicationEventPublisher events;
  private final ClockPort clock;
  private final CurrentUser currentUser;

  public PatientRegistrationService(
      PatientLookupRepository patients,
      PatientProfileRepository profiles,
      AppUserRepository users,
      MrnGenerator mrns,
      PatientConsentGateway consent,
      OtpIssuer otp,
      PatientAudit audit,
      PasswordEncoder passwords,
      ApplicationEventPublisher events,
      ClockPort clock,
      CurrentUser currentUser) {
    this.patients = patients;
    this.profiles = profiles;
    this.users = users;
    this.mrns = mrns;
    this.consent = consent;
    this.otp = otp;
    this.audit = audit;
    this.passwords = passwords;
    this.events = events;
    this.clock = clock;
    this.currentUser = currentUser;
  }

  /**
   * Public self sign-up. Creates ROLE_PATIENT account + patient row. Caller must supply a username,
   * password (≥12 chars) and consent=true.
   */
  @Transactional
  public Patient registerSelf(NewPatient demographics, String username, String password, boolean consentGranted) {
    validateDemographics(demographics);
    if (!consentGranted) {
      throw new PatientRuleException("patient.register.consentRequired");
    }
    String user = requireUsername(username);
    requirePassword(password);
    if (users.findByUsername(user).isPresent()) {
      throw new PatientRuleException("patient.register.usernameTaken");
    }
    assertNoDuplicate(demographics);

    AppUser account = new AppUser();
    account.setUsername(user);
    account.setPasswordHash(passwords.encode(password));
    account.setDisplayName(displayName(demographics));
    account.setEmailEnc(blankToNull(demographics.email()));
    account.setPhoneEnc(blankToNull(demographics.phone()));
    account.setEnabled(true);
    account.setMfaEnabled(false);
    account.setLocale(Locale.ENGLISH.getLanguage());
    account.setRoles(Set.of(Role.ROLE_PATIENT));
    users.save(account);

    Patient patient = persist(demographics, RegistrationSource.SELF, account.getId(), account.getId());
    consent.recordRegistrationConsent(
        patient.getId(), account.getId(), PatientConsentGateway.REGISTRATION_VERSION);
    issueInitialOtps(patient);
    events.publishEvent(
        new DomainEvents.PatientRegistered(patient.getId(), account.getId(), clock.now()));
    audit.recordAs(
        account.getId(),
        patient.getId(),
        "PATIENT_REGISTERED",
        PatientAudit.PATIENT,
        patient.getId(),
        Map.of("source", RegistrationSource.SELF.name(), "mrn", patient.getMrn()));
    return patient;
  }

  /** Front-desk walk-in registration (demographics only; no portal account). */
  @Transactional
  @PreAuthorize("hasRole('FRONT_DESK')")
  public Patient registerWalkIn(NewPatient demographics, boolean consentGranted) {
    validateDemographics(demographics);
    if (!consentGranted) {
      throw new PatientRuleException("patient.register.consentRequired");
    }
    assertNoDuplicate(demographics);
    UUID actor = currentUser.requireUserId();
    Patient patient = persist(demographics, RegistrationSource.FRONT_DESK, null, actor);
    consent.recordRegistrationConsent(
        patient.getId(), actor, PatientConsentGateway.REGISTRATION_VERSION);
    issueInitialOtps(patient);
    events.publishEvent(new DomainEvents.PatientRegistered(patient.getId(), null, clock.now()));
    audit.record(
        patient.getId(),
        "PATIENT_REGISTERED",
        PatientAudit.PATIENT,
        patient.getId(),
        Map.of("source", RegistrationSource.FRONT_DESK.name(), "mrn", patient.getMrn()));
    return patient;
  }

  /** Returns candidate patient ids when national ID or name+DOB+phone already exist. */
  @Transactional(readOnly = true)
  public void assertNoDuplicate(NewPatient demographics) {
    String nidHash = BlindIndex.nationalIdHash(demographics.nationalId());
    if (nidHash != null) {
      profiles
          .findByNationalIdHash(nidHash)
          .ifPresent(
              existing -> {
                throw new DuplicatePatientException(
                    DuplicatePatientException.Kind.NATIONAL_ID, List.of(existing.getPatientId()));
              });
    }
    String idHash =
        BlindIndex.identityHash(
            demographics.givenName(),
            demographics.familyName(),
            demographics.dateOfBirth(),
            demographics.phone());
    if (idHash != null) {
      List<UUID> hits =
          profiles.findByIdentityHash(idHash).stream().map(PatientProfile::getPatientId).toList();
      if (!hits.isEmpty()) {
        throw new DuplicatePatientException(DuplicatePatientException.Kind.NAME_DOB_PHONE, hits);
      }
    }
  }

  private Patient persist(
      NewPatient demographics, RegistrationSource source, UUID userId, UUID registeredBy) {
    Patient patient = new Patient();
    patient.setUserId(userId);
    patient.setMrn(mrns.next());
    patient.setGivenName(demographics.givenName().trim());
    patient.setFamilyName(demographics.familyName().trim());
    patient.setDateOfBirth(demographics.dateOfBirth());
    patient.setSex(normaliseSex(demographics.sex()));
    // Store the raw phone (encryption handles at-rest); normalisation is for the blind index only.
    patient.setPhone(blankToNull(demographics.phone()));
    patient.setEmail(blankToNull(demographics.email()));
    patient.setNationalId(blankToNull(demographics.nationalId()));
    patient.setAddress(blankToNull(demographics.address()));
    patients.save(patient);

    PatientProfile profile = new PatientProfile();
    profile.setPatientId(patient.getId());
    profile.setRegistrationSource(source);
    profile.setRegisteredBy(registeredBy);
    profile.setNationalIdHash(BlindIndex.nationalIdHash(demographics.nationalId()));
    profile.setIdentityHash(
        BlindIndex.identityHash(
            demographics.givenName(),
            demographics.familyName(),
            demographics.dateOfBirth(),
            demographics.phone()));
    profiles.save(profile);
    return patient;
  }

  private void issueInitialOtps(Patient patient) {
    if (patient.getPhone() != null && !patient.getPhone().isBlank()) {
      try {
        otp.issue(patient, com.mediflow.patient.domain.ContactChannel.SMS);
      } catch (PatientRuleException ignored) {
        // cooldown / missing target — registration still succeeds
      }
    }
    if (patient.getEmail() != null && !patient.getEmail().isBlank()) {
      try {
        otp.issue(patient, com.mediflow.patient.domain.ContactChannel.EMAIL);
      } catch (PatientRuleException ignored) {
        // same
      }
    }
  }

  static void validateDemographics(NewPatient d) {
    if (d == null
        || blank(d.givenName())
        || blank(d.familyName())
        || d.dateOfBirth() == null
        || blank(d.sex())) {
      throw new PatientRuleException("patient.register.incomplete");
    }
    if (blank(d.phone()) && blank(d.email())) {
      throw new PatientRuleException("patient.register.contactRequired");
    }
  }

  private static void requirePassword(String password) {
    if (password == null || password.length() < 12) {
      throw new PatientRuleException("patient.register.passwordWeak");
    }
  }

  private static String requireUsername(String username) {
    if (blank(username) || username.trim().length() < 3 || username.trim().length() > 100) {
      throw new PatientRuleException("patient.register.usernameInvalid");
    }
    return username.trim().toLowerCase(Locale.ROOT);
  }

  private static String displayName(NewPatient d) {
    return (d.givenName().trim() + " " + d.familyName().trim()).trim();
  }

  private static String normaliseSex(String sex) {
    String s = sex.trim().toUpperCase(Locale.ROOT);
    return switch (s) {
      case "M", "MALE" -> "MALE";
      case "F", "FEMALE" -> "FEMALE";
      case "O", "OTHER", "X", "UNKNOWN" -> "OTHER";
      default -> throw new PatientRuleException("patient.register.sexInvalid");
    };
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private static String blankToNull(String s) {
    return blank(s) ? null : s.trim();
  }
}
