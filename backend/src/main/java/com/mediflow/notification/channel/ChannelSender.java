package com.mediflow.notification.channel;

import com.mediflow.notification.domain.Channel;

/** One implementation per {@link Channel}. */
public interface ChannelSender {

  Channel channel();

  void send(OutboundMessage message) throws DeliveryException;
}
