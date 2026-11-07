package com.mediflow.notification.channel;

import com.mediflow.notification.domain.Priority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev/test push gateway (FCM stub). */
@Component
@ConditionalOnProperty(
    name = "mediflow.notification.push.provider",
    havingValue = "log",
    matchIfMissing = true)
public class LoggingPushGateway implements PushGateway {

  private static final Logger log = LoggerFactory.getLogger(LoggingPushGateway.class);

  @Override
  public void send(String deviceToken, String title, String body, Priority priority) {
    log.info("PUSH (log gateway) token=…{} priority={}", tail(deviceToken), priority);
  }

  private static String tail(String token) {
    return token == null || token.length() < 6 ? "" : token.substring(token.length() - 6);
  }
}
