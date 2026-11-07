package com.mediflow.notification.service;

import com.mediflow.notification.channel.Contact;
import com.mediflow.notification.config.NotificationProperties;
import com.mediflow.notification.delivery.NotificationDispatcher;
import com.mediflow.notification.domain.Channel;
import com.mediflow.notification.domain.NotificationEventType;
import com.mediflow.notification.domain.NotificationPreference;
import com.mediflow.notification.domain.Priority;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.NotificationPort;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M8 entry point. Implements {@link NotificationPort} (so the T00 logging stub backs off) and offers
 * the richer templated {@link #send(Request)} used by the event listeners and the reminder job.
 */
@Service
public class NotificationService implements NotificationPort {

  /**
   * @param patientId set for patient-facing messages (contact comes from the patient record)
   * @param recipientUserId set for staff/user messages (ignored when patientId resolves a user)
   * @param channels explicit channels, or null/empty to use the recipient's preferences
   */
  public record Request(
      NotificationEventType type,
      UUID recipientUserId,
      UUID patientId,
      Priority priority,
      Set<Channel> channels,
      Object... args) {

    public static Request toPatient(NotificationEventType type, UUID patientId, Object... args) {
      return new Request(type, null, patientId, Priority.NORMAL, null, args);
    }

    public static Request toUser(NotificationEventType type, UUID userId, Object... args) {
      return new Request(type, userId, null, Priority.NORMAL, null, args);
    }

    public Request withPriority(Priority p) {
      return new Request(type, recipientUserId, patientId, p, channels, args);
    }

    public Request withChannels(Set<Channel> c) {
      return new Request(type, recipientUserId, patientId, priority, c, args);
    }
  }

  private final RecipientResolver recipients;
  private final TemplateRenderer templates;
  private final NotificationWriter writer;
  private final NotificationDispatcher dispatcher;
  private final StaffCallListService callList;
  private final NotificationProperties props;
  private final ClockPort clock;

  public NotificationService(
      RecipientResolver recipients,
      TemplateRenderer templates,
      NotificationWriter writer,
      NotificationDispatcher dispatcher,
      StaffCallListService callList,
      NotificationProperties props,
      ClockPort clock) {
    this.recipients = recipients;
    this.templates = templates;
    this.writer = writer;
    this.dispatcher = dispatcher;
    this.callList = callList;
    this.props = props;
    this.clock = clock;
  }

  /** Renders the event template in the recipient's language and fans out to the chosen channels. */
  @Transactional
  public List<Notification> send(Request req) {
    Contact contact = recipients.contact(req.recipientUserId(), req.patientId());
    String locale = recipients.locale(contact.userId());
    TemplateRenderer.Rendered text = templates.render(req.type(), locale, req.args());
    Set<Channel> channels =
        req.channels() == null || req.channels().isEmpty()
            ? chooseChannels(contact, req.priority())
            : req.channels();
    return fanOut(contact, channels, locale, text.subject(), text.body(), req.type(), req.priority());
  }

  @Override
  @Transactional
  public void notifyUser(UUID recipientUserId, String channel, String locale, String subject, String body) {
    direct(recipients.contact(recipientUserId, null), channel, locale, subject, body);
  }

  @Override
  @Transactional
  public void notifyPatient(UUID patientId, String channel, String locale, String subject, String body) {
    direct(recipients.contact(null, patientId), channel, locale, subject, body);
  }

  private void direct(Contact contact, String channel, String locale, String subject, String body) {
    String loc =
        locale == null || locale.isBlank()
            ? recipients.locale(contact.userId())
            : RecipientResolver.normaliseLocale(locale);
    Set<Channel> channels =
        Channel.parse(channel)
            .map(c -> (Set<Channel>) EnumSet.of(c))
            .orElseGet(() -> chooseChannels(contact, Priority.NORMAL));
    fanOut(contact, channels, loc, subject, body, NotificationEventType.DIRECT, Priority.NORMAL);
  }

  /**
   * Preference-driven channel choice. Patients: SMS / email / push / in-app as enabled and
   * reachable. Staff: in-app + push. Quiet hours drop external channels for NORMAL priority; HIGH
   * ignores quiet hours and always adds in-app.
   */
  Set<Channel> chooseChannels(Contact contact, Priority priority) {
    NotificationPreference pref = recipients.preference(contact.userId());
    Set<Channel> chosen = EnumSet.noneOf(Channel.class);
    boolean high = priority == Priority.HIGH;
    if (contact.patientId() != null) {
      if (contact.hasPhone() && (high || pref.allows(Channel.SMS))) {
        chosen.add(Channel.SMS);
      }
      if (contact.hasEmail() && pref.allows(Channel.EMAIL)) {
        chosen.add(Channel.EMAIL);
      }
    }
    if (!contact.pushTokens().isEmpty() && (high || pref.allows(Channel.PUSH))) {
      chosen.add(Channel.PUSH);
    }
    if (contact.userId() != null && (high || pref.allows(Channel.IN_APP))) {
      chosen.add(Channel.IN_APP);
    }
    LocalTime now = clock.now().atZone(props.zone()).toLocalTime();
    if (!high && pref.isQuiet(now) && contact.userId() != null) {
      chosen.removeIf(Channel::external);
      chosen.add(Channel.IN_APP);
    }
    return chosen;
  }

  private List<Notification> fanOut(
      Contact contact,
      Set<Channel> channels,
      String locale,
      String subject,
      String body,
      NotificationEventType type,
      Priority priority) {
    List<Notification> created = new ArrayList<>();
    for (Channel channel : channels) {
      if (!reachable(contact, channel)) {
        continue; // e.g. explicit PUSH for a user without a registered device
      }
      Notification n =
          writer.create(
              contact.userId(), contact.patientId(), channel, locale, subject, body, type, priority, null);
      created.add(n);
      dispatcher.dispatchAfterCommit(n.getId(), channel);
    }
    if (created.isEmpty()) {
      callList.addOrUpdate(
          null,
          contact.patientId(),
          contact.userId(),
          type,
          priority,
          "No reachable channel for recipient",
          body);
    }
    return created;
  }

  /** Whether the recipient can receive {@code channel} at all. */
  public static boolean reachable(Contact contact, Channel channel) {
    return switch (channel) {
      case PUSH -> !contact.pushTokens().isEmpty();
      case SMS -> contact.hasPhone();
      case EMAIL -> contact.hasEmail();
      case IN_APP -> contact.userId() != null;
    };
  }
}
