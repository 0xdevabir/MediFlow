package com.mediflow.notification.channel;

import com.mediflow.notification.config.NotificationProperties;
import com.mediflow.notification.domain.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/** Spring Mail (Mailpit in dev). Without a configured mail sender (test profile) it only logs. */
@Component
public class EmailChannelSender implements ChannelSender {

  private static final Logger log = LoggerFactory.getLogger(EmailChannelSender.class);

  private final ObjectProvider<JavaMailSender> mailSender;
  private final NotificationProperties props;

  public EmailChannelSender(ObjectProvider<JavaMailSender> mailSender, NotificationProperties props) {
    this.mailSender = mailSender;
    this.props = props;
  }

  @Override
  public Channel channel() {
    return Channel.EMAIL;
  }

  @Override
  public void send(OutboundMessage m) throws DeliveryException {
    if (!m.contact().hasEmail()) {
      throw DeliveryException.noAddress("EMAIL");
    }
    JavaMailSender sender = mailSender.getIfAvailable();
    if (sender == null) {
      log.info("EMAIL (no mail sender configured) to={}", Masking.email(m.contact().email()));
      return;
    }
    SimpleMailMessage mail = new SimpleMailMessage();
    mail.setFrom(props.mailFrom());
    mail.setTo(m.contact().email());
    mail.setSubject(m.subject());
    mail.setText(m.body());
    try {
      sender.send(mail);
    } catch (MailException e) {
      throw new DeliveryException("Mail delivery failed: " + e.getClass().getSimpleName(), e);
    }
  }
}
