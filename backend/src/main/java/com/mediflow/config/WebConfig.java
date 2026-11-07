package com.mediflow.config;

import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

@Configuration
public class WebConfig implements WebMvcConfigurer {

  @Bean
  LocaleResolver localeResolver() {
    CookieLocaleResolver resolver = new CookieLocaleResolver("MEDIFLOW_LANG");
    resolver.setDefaultLocale(Locale.ENGLISH);
    resolver.setCookieHttpOnly(true);
    resolver.setCookieSecure(false);
    return resolver;
  }

  @Bean
  LocaleChangeInterceptor localeChangeInterceptor() {
    LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
    interceptor.setParamName("lang");
    return interceptor;
  }

  @Bean
  MessageSource messageSource() {
    ReloadableResourceBundleMessageSource source = new ReloadableResourceBundleMessageSource();
    source.setBasenames(
        "classpath:messages/common",
        "classpath:messages/identity",
        "classpath:messages/patient",
        "classpath:messages/directory",
        "classpath:messages/appointment",
        "classpath:messages/clinical",
        "classpath:messages/prescription",
        "classpath:messages/lab",
        "classpath:messages/pharmacy",
        "classpath:messages/notification",
        "classpath:messages/audit",
        "classpath:messages/admin",
        "classpath:messages/fhir");
    source.setDefaultEncoding("UTF-8");
    source.setFallbackToSystemLocale(false);
    return source;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(localeChangeInterceptor());
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/login").setViewName("login");
  }
}
