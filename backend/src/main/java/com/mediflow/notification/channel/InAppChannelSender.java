package com.mediflow.notification.channel;

import com.mediflow.notification.domain.Channel;
import org.springframework.stereotype.Component;

/** In-app: the persisted {@code notification} row is the delivery (bell + list page). */
@Component
public class InAppChannelSender implements ChannelSender {

  @Override
  public Channel channel() {
    return Channel.IN_APP;
  }

  @Override
  public void send(OutboundMessage message) throws DeliveryException {
    if (message.contact().userId() == null) {
      throw DeliveryException.noAddress("IN_APP");
    }
  }
}
