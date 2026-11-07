package com.mediflow.notification.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev/test SMS gateway: logs a masked number, never the message body. */
@Component
@ConditionalOnProperty(
    name = "mediflow.notification.sms.provider",
    havingValue = "log",
    matchIfMissing = true)
public class LoggingSmsGateway implements SmsGateway {

  private static final Logger log = LoggerFactory.getLogger(LoggingSmsGateway.class);

  @Override
  public void send(String phone, String text) {
    log.info("SMS (log gateway) to={} chars={}", Masking.phone(phone), text.length());
  }
}
