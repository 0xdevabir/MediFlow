package com.mediflow.prescription.sign;

import com.mediflow.prescription.support.RxExceptions.MfaStepUpRequiredException;
import java.lang.reflect.Method;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * When T01's {@code MfaReauthService} is on the classpath, prefer it over {@link
 * SessionRxMfaStepUp}. Reflection keeps this module compiling in isolation.
 */
@Configuration
@ConditionalOnClass(name = "com.mediflow.identity.mfa.MfaReauthService")
public class IdentityMfaConfig {

  @Bean
  @Primary
  RxMfaStepUp identityRxMfaStepUp(ApplicationContext ctx) throws Exception {
    Class<?> type = Class.forName("com.mediflow.identity.mfa.MfaReauthService");
    Object delegate = ctx.getBean(type);
    Method requireFresh = type.getMethod("requireFreshMfa");
    Method isFresh = type.getMethod("isFresh");
    return new RxMfaStepUp() {
      @Override
      public void requireFreshMfa() {
        try {
          requireFresh.invoke(delegate);
        } catch (ReflectiveOperationException e) {
          Throwable cause = e.getCause() != null ? e.getCause() : e;
          if (cause.getClass().getName().endsWith("MfaStepUpRequiredException")) {
            throw new MfaStepUpRequiredException();
          }
          throw new IllegalStateException("MFA step-up failed", cause);
        }
      }

      @Override
      public boolean isFresh() {
        try {
          return Boolean.TRUE.equals(isFresh.invoke(delegate));
        } catch (ReflectiveOperationException e) {
          return false;
        }
      }
    };
  }
}
