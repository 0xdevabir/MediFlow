package com.mediflow.notification.delivery;

import com.mediflow.notification.channel.ChannelSender;
import com.mediflow.notification.channel.Contact;
import com.mediflow.notification.channel.DeliveryException;
import com.mediflow.notification.channel.OutboundMessage;
import com.mediflow.notification.config.NotificationProperties;
import com.mediflow.notification.domain.Channel;
import com.mediflow.notification.domain.NotificationDelivery;
import com.mediflow.notification.repo.NotificationDeliveryRepository;
import com.mediflow.notification.service.NotificationWriter;
import com.mediflow.notification.service.RecipientResolver;
import com.mediflow.notification.service.StaffCallListService;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.NotificationStatus;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.repo.NotificationRepository;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * One delivery attempt for one notification: send through its channel, then mark SENT, ask for a
 * retry (exponential backoff), or — on final failure — mark FAILED, escalate to the next channel in
 * {@link Channel#ESCALATION_ORDER} and put the chain on the staff call list (risk R7).
 */
@Service
public class DeliveryService {

  private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

  private final NotificationRepository notifications;
  private final NotificationDeliveryRepository deliveries;
  private final Map<Channel, ChannelSender> senders = new EnumMap<>(Channel.class);
  private final RecipientResolver recipients;
  private final NotificationWriter writer;
  private final StaffCallListService callList;
  private final NotificationProperties props;
  private final ClockPort clock;

  public DeliveryService(
      NotificationRepository notifications,
      NotificationDeliveryRepository deliveries,
      List<ChannelSender> senderList,
      RecipientResolver recipients,
      NotificationWriter writer,
      StaffCallListService callList,
      NotificationProperties props,
      ClockPort clock) {
    this.notifications = notifications;
    this.deliveries = deliveries;
    senderList.forEach(s -> senders.put(s.channel(), s));
    this.recipients = recipients;
    this.writer = writer;
    this.callList = callList;
    this.props = props;
    this.clock = clock;
  }

  // REQUIRES_NEW: may be invoked from afterCommit() of the publishing transaction, whose resources
  // are still bound; joining it would silently lose these writes.
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
  public DeliveryOutcome attempt(UUID notificationId, int attempt) {
    Optional<Notification> found = notifications.findById(notificationId);
    if (found.isEmpty() || found.get().getStatus() != NotificationStatus.PENDING) {
      return DeliveryOutcome.skipped(); // already handled (redelivery) or deleted
    }
    Notification n = found.get();
    NotificationDelivery d =
        deliveries.findByNotificationId(notificationId).orElseThrow(IllegalStateException::new);
    Channel channel = Channel.parse(n.getChannel()).orElse(Channel.IN_APP);
    Contact contact = recipients.contact(n.getRecipientUserId(), n.getPatientId());
    d.setAttempts(attempt);
    try {
      ChannelSender sender = senders.get(channel);
      if (sender == null) {
        throw new DeliveryException("No sender for " + channel, true);
      }
      sender.send(
          new OutboundMessage(
              n.getId(), channel, contact, n.getLocale(), n.getSubject(), n.getBody(), d.getPriority()));
      n.setStatus(NotificationStatus.SENT);
      n.setSentAt(clock.now());
      d.setLastError(null);
      return DeliveryOutcome.delivered();
    } catch (DeliveryException e) {
      return onFailure(n, d, contact, channel, attempt, e.getMessage(), e.isPermanent());
    } catch (RuntimeException e) {
      return onFailure(n, d, contact, channel, attempt, e.getClass().getSimpleName(), false);
    }
  }

  private DeliveryOutcome onFailure(
      Notification n,
      NotificationDelivery d,
      Contact contact,
      Channel channel,
      int attempt,
      String error,
      boolean permanent) {
    d.setLastError(error);
    if (!permanent && attempt < props.maxAttempts()) {
      log.info("Notification {} via {} attempt {} failed ({}), retrying", n.getId(), channel, attempt, error);
      return DeliveryOutcome.retry(props.backoffAfter(attempt));
    }
    log.warn("Notification {} via {} failed finally after {} attempt(s): {}", n.getId(), channel, attempt, error);
    n.setStatus(NotificationStatus.FAILED);
    List<DeliveryOutcome.Escalation> escalations = new ArrayList<>();
    nextChannel(channel, contact)
        .ifPresent(
            next -> {
              Notification copy =
                  writer.create(
                      n.getRecipientUserId(),
                      n.getPatientId(),
                      next,
                      n.getLocale(),
                      n.getSubject(),
                      n.getBody(),
                      d.getEventType(),
                      d.getPriority(),
                      d.getRootNotificationId());
              d.setEscalatedToId(copy.getId());
              escalations.add(new DeliveryOutcome.Escalation(copy.getId(), next));
            });
    // In-app is not a phone/SMS substitute for patients: any final failure of an external channel
    // (or of the whole chain) goes to the staff call list.
    if (channel.external() || escalations.isEmpty()) {
      callList.addOrUpdate(
          d.getRootNotificationId(),
          n.getPatientId(),
          n.getRecipientUserId(),
          d.getEventType(),
          d.getPriority(),
          channel + " failed after " + attempt + " attempt(s): " + error,
          n.getBody());
    }
    return DeliveryOutcome.failed(escalations);
  }

  /** Next channel after {@code failed} that the recipient can actually receive. */
  Optional<Channel> nextChannel(Channel failed, Contact contact) {
    List<Channel> order = Channel.ESCALATION_ORDER;
    for (int i = order.indexOf(failed) + 1; i < order.size(); i++) {
      Channel c = order.get(i);
      if (com.mediflow.notification.service.NotificationService.reachable(contact, c)) {
        return Optional.of(c);
      }
    }
    return Optional.empty();
  }
}
