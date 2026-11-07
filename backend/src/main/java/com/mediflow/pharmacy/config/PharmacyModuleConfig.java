package com.mediflow.pharmacy.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PharmacyProperties.class)
public class PharmacyModuleConfig {}
