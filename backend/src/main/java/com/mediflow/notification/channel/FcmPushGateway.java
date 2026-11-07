package com.mediflow.notification.channel;

import com.mediflow.notification.config.NotificationProperties;
import com.mediflow.notification.domain.Priority;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * FCM HTTP v1 adapter. The OAuth access token is supplied by the environment (rotated by the
 * deployment's service-account sidecar); 404/400 = unregistered token (permanent).
 */
@Component
@ConditionalOnProperty(name = "mediflow.notification.push.provider", havingValue = "fcm")
public class FcmPushGateway implements PushGateway {

  private final RestClient client;
  private final NotificationProperties.Push config;

  public FcmPushGateway(RestClient.Builder builder, NotificationProperties props) {
    this.config = props.push();
    if (config.projectId().isBlank()) {
      throw new IllegalStateException("mediflow.notification.push.project-id is required for fcm");
    }
    this.client = builder.baseUrl("https://fcm.googleapis.com").build();
  }

  @Override
  public void send(String deviceToken, String title, String body, Priority priority)
      throws DeliveryException {
    Map<String, Object> message =
        Map.of(
            "message",
            Map.of(
                "token", deviceToken,
                "notification", Map.of("title", title, "body", body),
                "webpush",
                    Map.of("headers", Map.of("Urgency", priority == Priority.HIGH ? "high" : "normal"))));
    try {
      client
          .post()
          .uri("/v1/projects/{project}/messages:send", config.projectId())
          .header("Authorization", "Bearer " + config.accessToken())
          .contentType(MediaType.APPLICATION_JSON)
          .body(message)
          .retrieve()
          .toBodilessEntity();
    } catch (RestClientResponseException e) {
      int code = e.getStatusCode().value();
      throw code == 400 || code == 404
          ? new DeliveryException("FCM rejected token: HTTP " + code, true)
          : new DeliveryException("FCM error: HTTP " + code, e);
    } catch (RestClientException e) {
      throw new DeliveryException("FCM unreachable", e);
    }
  }
}
