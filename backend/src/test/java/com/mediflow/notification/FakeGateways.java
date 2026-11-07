package com.mediflow.notification;

import com.mediflow.notification.channel.DeliveryException;
import com.mediflow.notification.channel.PushGateway;
import com.mediflow.notification.channel.SmsGateway;
import com.mediflow.notification.domain.Priority;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Controllable SMS / push gateways replacing the logging dev gateways in notification ITs. */
@TestConfiguration(proxyBeanMethods = false)
public class FakeGateways {

  /** Fails the next {@code transientFailures} calls, or every call while {@code alwaysFail}. */
  public static class Controllable {
    public final List<String> sent = new CopyOnWriteArrayList<>();
    public final AtomicInteger calls = new AtomicInteger();
    public final AtomicInteger transientFailures = new AtomicInteger();
    public volatile boolean alwaysFail;
    public volatile boolean permanent;

    public void reset() {
      sent.clear();
      calls.set(0);
      transientFailures.set(0);
      alwaysFail = false;
      permanent = false;
    }

    void call(String to, String text) throws DeliveryException {
      calls.incrementAndGet();
      if (alwaysFail || transientFailures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
        throw new DeliveryException("simulated gateway failure", permanent);
      }
      sent.add(to + "|" + text);
    }
  }

  public static class FakeSms extends Controllable implements SmsGateway {
    @Override
    public void send(String phone, String text) throws DeliveryException {
      call(phone, text);
    }
  }

  public static class FakePush extends Controllable implements PushGateway {
    @Override
    public void send(String deviceToken, String title, String body, Priority priority)
        throws DeliveryException {
      call(deviceToken, title);
    }
  }

  @Bean
  @Primary
  FakeSms fakeSms() {
    return new FakeSms();
  }

  @Bean
  @Primary
  FakePush fakePush() {
    return new FakePush();
  }
}
