package com.mediflow.notification.service;

import com.mediflow.notification.channel.Contact;
import com.mediflow.notification.domain.NotificationPreference;
import com.mediflow.notification.domain.PushDeviceToken;
import com.mediflow.notification.repo.NotificationPreferenceRepository;
import com.mediflow.notification.repo.PushDeviceTokenRepository;
import com.mediflow.notification.repo.RecipientLookupRepository;
import com.mediflow.shared.domain.AppUser;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.repo.AppUserRepository;
import com.mediflow.shared.repo.PatientRepository;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves addresses, preferences and language for a user or patient. */
@Component
public class RecipientResolver {

  public static final String DEFAULT_LOCALE = "en";

  private final AppUserRepository users;
  private final PatientRepository patients;
  private final PushDeviceTokenRepository pushTokens;
  private final NotificationPreferenceRepository preferences;
  private final RecipientLookupRepository lookup;

  public RecipientResolver(
      AppUserRepository users,
      PatientRepository patients,
      PushDeviceTokenRepository pushTokens,
      NotificationPreferenceRepository preferences,
      RecipientLookupRepository lookup) {
    this.users = users;
    this.patients = patients;
    this.pushTokens = pushTokens;
    this.preferences = preferences;
    this.lookup = lookup;
  }

  /**
   * Contact for a patient (phone/email from the encrypted patient record) or, when {@code patientId}
   * is null, for a staff/user account (in-app + push only: staff contact fields are owned by T01).
   */
  public Contact contact(UUID userId, UUID patientId) {
    if (patientId != null) {
      Optional<Patient> patient = patients.findById(patientId);
      UUID uid = patient.map(Patient::getUserId).orElse(userId);
      return new Contact(
          uid,
          patientId,
          patient.map(p -> p.getGivenName() + " " + p.getFamilyName()).orElse(""),
          patient.map(Patient::getPhone).orElse(null),
          patient.map(Patient::getEmail).orElse(null),
          tokens(uid));
    }
    if (userId != null) {
      List<UUID> linkedPatient = lookup.findPatientIdByUserId(userId);
      if (!linkedPatient.isEmpty()) {
        return contact(userId, linkedPatient.get(0));
      }
    }
    String name = userId == null ? "" : users.findById(userId).map(AppUser::getDisplayName).orElse("");
    return new Contact(userId, null, name, null, null, tokens(userId));
  }

  public NotificationPreference preference(UUID userId) {
    if (userId == null) {
      return NotificationPreference.defaultsFor(null);
    }
    return preferences.findByUserId(userId).orElseGet(() -> NotificationPreference.defaultsFor(userId));
  }

  /** Preferred language: preference → account locale → English. Only {@code bn}/{@code en}. */
  public String locale(UUID userId) {
    if (userId == null) {
      return DEFAULT_LOCALE;
    }
    String pref = preference(userId).getLocale();
    String chosen =
        pref != null && !pref.isBlank()
            ? pref
            : users.findById(userId).map(AppUser::getLocale).orElse(DEFAULT_LOCALE);
    return normaliseLocale(chosen);
  }

  public static String normaliseLocale(String locale) {
    if (locale == null) {
      return DEFAULT_LOCALE;
    }
    return locale.toLowerCase(Locale.ROOT).startsWith("bn") ? "bn" : DEFAULT_LOCALE;
  }

  private List<String> tokens(UUID userId) {
    return userId == null
        ? List.of()
        : pushTokens.findByUserId(userId).stream().map(PushDeviceToken::getToken).toList();
  }
}
