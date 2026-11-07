package com.mediflow.notification.channel;

import com.mediflow.notification.domain.Channel;
import org.springframework.stereotype.Component;

@Component
public class PushChannelSender implements ChannelSender {

  private final PushGateway gateway;

  public PushChannelSender(PushGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  public Channel channel() {
    return Channel.PUSH;
  }

  /** Succeeds if at least one registered device accepted the message. */
  @Override
  public void send(OutboundMessage m) throws DeliveryException {
    if (m.contact().pushTokens().isEmpty()) {
      throw DeliveryException.noAddress("PUSH");
    }
    DeliveryException last = null;
    int delivered = 0;
    for (String token : m.contact().pushTokens()) {
      try {
        gateway.send(token, m.subject(), m.body(), m.priority());
        delivered++;
      } catch (DeliveryException e) {
        last = e;
      }
    }
    if (delivered == 0 && last != null) {
      throw last;
    }
  }
}
