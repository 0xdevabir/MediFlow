package com.mediflow.notification.channel;

import com.mediflow.notification.domain.Channel;
import com.mediflow.notification.domain.Priority;
import java.util.UUID;

public record OutboundMessage(
    UUID notificationId,
    Channel channel,
    Contact contact,
    String locale,
    String subject,
    String body,
    Priority priority) {}
