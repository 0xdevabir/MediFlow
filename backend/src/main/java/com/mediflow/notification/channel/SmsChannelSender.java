package com.mediflow.notification.channel;

import com.mediflow.notification.domain.Channel;
import org.springframework.stereotype.Component;

@Component
public class SmsChannelSender implements ChannelSender {

  private final SmsGateway gateway;

  public SmsChannelSender(SmsGateway gateway) {
    this.gateway = gateway;
  }

  @Override
  public Channel channel() {
    return Channel.SMS;
  }

  @Override
  public void send(OutboundMessage m) throws DeliveryException {
    if (!m.contact().hasPhone()) {
      throw DeliveryException.noAddress("SMS");
    }
    gateway.send(m.contact().phone(), m.body());
  }
}
