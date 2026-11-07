package com.mediflow.notification.channel;

/** Pluggable SMS gateway (BD aggregator over HTTP, or logging in dev). */
public interface SmsGateway {

  void send(String phone, String text) throws DeliveryException;
}
