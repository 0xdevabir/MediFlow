package com.mediflow.pharmacy.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on Spring's cache abstraction so Boot auto-configures a {@code CacheManager} (Redis when a
 * connection factory exists, otherwise a simple in-memory one). Harmless if another module also
 * declares {@code @EnableCaching}.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class PharmacyCachingConfig {}
