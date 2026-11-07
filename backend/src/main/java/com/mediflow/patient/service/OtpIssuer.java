package com.mediflow.patient.service;

import com.mediflow.patient.domain.ContactChannel;
import com.mediflow.patient.domain.ContactVerification;
import com.mediflow.patient.repo.ContactVerificationRepository;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Issues and checks one-time codes. Not access-checked itself: callers are
 * {@link ContactVerificationService} (secured) and the registration flow.
 *
 * <p>6 digits, 10 minutes, 5 attempts, at most one code per channel per 60 s; only a keyed hash
 * of the code is stored; a new code invalidates the previous one.
 */
@Component
class OtpIssuer {

  static final Duration TTL = Duration.ofMinutes(10);
  static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
  static final int MAX_ATTEMPTS = 5;

  private static final SecureRandom RANDOM = new SecureRandom();

  private final ContactVerificationRepository verifications;
  private final NotificationPort notifications;
  private final MessageSource messages;
  private final ClockPort clock;

  OtpIssuer(
      ContactVerificationRepository verifications,
      NotificationPort notifications,
      MessageSource messages,
      ClockPort clock) {
    this.verifications = verifications;
    this.notifications = notifications;
    this.messages = messages;
    this.clock = clock;
  }

  /** Returns the new verification row; throws when the cooldown is active or no target exists. */
  ContactVerification issue(Patient patient, ContactChannel channel) {
    String target = channel == ContactChannel.SMS ? patient.getPhone() : patient.getEmail();
    if (target == null || target.isBlank()) {
      throw new PatientRuleException("patient.otp.noTarget");
    }
    Instant now = clock.now();
    Optional<ContactVerification> latest =
        verifications.findFirstByPatientIdAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
            patient.getId(), channel);
    if (latest.isPresent()
        && latest.get().getCreatedAt() != null
        && latest.get().getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
      throw new PatientRuleException("patient.otp.cooldown");
    }
    for (ContactVerification old :
        verifications.findByPatientIdAndChannelAndConsumedAtIsNull(patient.getId(), channel)) {
      old.setConsumedAt(now);
    }
    String code = String.format("%06d", RANDOM.nextInt(1_000_000));
    ContactVerification v = new ContactVerification();
    v.setPatientId(patient.getId());
    v.setChannel(channel);
    v.setCodeHash(hash(patient.getId(), channel, code));
    v.setExpiresAt(now.plus(TTL));
    v.setAttempts(0);
    verifications.save(v);

    Locale locale = LocaleContextHolder.getLocale();
    String subject = messages.getMessage("patient.otp.subject", null, "MediFlow", locale);
    String body =
        messages.getMessage(
            "patient.otp.body",
            new Object[] {code, TTL.toMinutes()},
            "MediFlow code " + code,
            locale);
    notifications.notifyPatient(patient.getId(), channel.name(), locale.getLanguage(), subject, body);
    return v;
  }

  enum Outcome {
    VERIFIED,
    WRONG_CODE,
    EXPIRED,
    TOO_MANY_ATTEMPTS,
    NO_PENDING_CODE
  }

  Outcome check(UUID patientId, ContactChannel channel, String code) {
    Optional<ContactVerification> latest =
        verifications.findFirstByPatientIdAndChannelAndConsumedAtIsNullOrderByCreatedAtDesc(
            patientId, channel);
    if (latest.isEmpty()) {
      return Outcome.NO_PENDING_CODE;
    }
    ContactVerification v = latest.get();
    Instant now = clock.now();
    if (!now.isBefore(v.getExpiresAt())) {
      v.setConsumedAt(now);
      return Outcome.EXPIRED;
    }
    if (v.getAttempts() >= MAX_ATTEMPTS) {
      v.setConsumedAt(now);
      return Outcome.TOO_MANY_ATTEMPTS;
    }
    v.setAttempts(v.getAttempts() + 1);
    String normalised = code == null ? "" : code.replaceAll("\\s", "");
    boolean ok =
        PatientKeys.equal(
            v.getCodeHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            hash(patientId, channel, normalised).getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    if (ok) {
      v.setConsumedAt(now);
      return Outcome.VERIFIED;
    }
    if (v.getAttempts() >= MAX_ATTEMPTS) {
      v.setConsumedAt(now);
      return Outcome.TOO_MANY_ATTEMPTS;
    }
    return Outcome.WRONG_CODE;
  }

  static String hash(UUID patientId, ContactChannel channel, String code) {
    return PatientKeys.hmacHex(PatientKeys.OTP, patientId + "|" + channel + "|" + code);
  }
}
