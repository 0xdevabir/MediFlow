package com.mediflow.notification.channel;

import com.mediflow.notification.domain.Priority;

/** Web-push gateway (FCM, or logging in dev). */
public interface PushGateway {

  void send(String deviceToken, String title, String body, Priority priority)
      throws DeliveryException;
}
