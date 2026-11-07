package com.mediflow.notification.service;

import com.mediflow.notification.config.NotificationProperties;
import com.mediflow.notification.domain.NotificationEventType;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

/** Renders per-event, per-locale templates from {@code messages/notification_{en,bn}}. */
@Component
public class TemplateRenderer {

  public record Rendered(String subject, String body) {}

  private final MessageSource messages;
  private final NotificationProperties props;

  public TemplateRenderer(MessageSource messages, NotificationProperties props) {
    this.messages = messages;
    this.props = props;
  }

  public Rendered render(NotificationEventType type, String locale, Object... args) {
    Locale loc = Locale.forLanguageTag(locale);
    Object[] formatted = new Object[args.length];
    for (int i = 0; i < args.length; i++) {
      formatted[i] = args[i] instanceof Instant at ? formatInstant(at, loc) : args[i];
    }
    String subject = messages.getMessage(type.subjectKey(), formatted, loc);
    String body = messages.getMessage(type.bodyKey(), formatted, loc);
    return new Rendered(subject, body);
  }

  public String formatInstant(Instant at, Locale locale) {
    return DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", locale).format(at.atZone(props.zone()));
  }
}
