package com.mediflow.notification.channel;

import com.mediflow.notification.config.NotificationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Generic Bangladeshi SMS aggregator adapter (BulkSMSBD / SSL Wireless style): form POST with
 * {@code api_key, senderid, number, message} to {@code mediflow.notification.sms.url}. 4xx is a
 * permanent failure (bad number / rejected), 5xx and I/O errors are retried.
 */
@Component
@ConditionalOnProperty(name = "mediflow.notification.sms.provider", havingValue = "http")
public class HttpSmsGateway implements SmsGateway {

  private final RestClient client;
  private final NotificationProperties.Sms config;

  public HttpSmsGateway(RestClient.Builder builder, NotificationProperties props) {
    this.config = props.sms();
    if (config.url().isBlank()) {
      throw new IllegalStateException("mediflow.notification.sms.url is required for provider=http");
    }
    this.client = builder.baseUrl(config.url()).build();
  }

  @Override
  public void send(String phone, String text) throws DeliveryException {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("api_key", config.apiKey());
    form.add("senderid", config.senderId());
    form.add("number", phone);
    form.add("message", text);
    try {
      client.post().contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().toBodilessEntity();
    } catch (RestClientResponseException e) {
      throw e.getStatusCode().is4xxClientError()
          ? new DeliveryException("SMS gateway rejected: HTTP " + e.getStatusCode().value(), true)
          : new DeliveryException("SMS gateway error: HTTP " + e.getStatusCode().value(), e);
    } catch (RestClientException e) {
      throw new DeliveryException("SMS gateway unreachable", e);
    }
  }
}
