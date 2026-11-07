package com.mediflow.pharmacy.dictionary;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

/**
 * Read-through cache for dictionary lookups. Uses whatever {@link CacheManager} Boot configures
 * (Redis in dev/prod, in-memory when Redis is excluded as in tests). Any cache failure (e.g. Redis
 * down) is logged and bypassed: the dictionary always falls back to the database.
 */
@Component
public class DictionaryCache {

  public static final String CACHE_NAME = "pharmacy.drugDictionary";

  private static final Logger log = LoggerFactory.getLogger(DictionaryCache.class);

  private final ObjectProvider<CacheManager> cacheManagers;

  public DictionaryCache(ObjectProvider<CacheManager> cacheManagers) {
    this.cacheManagers = cacheManagers;
  }

  @SuppressWarnings("unchecked")
  public <T> T get(String key, Supplier<T> loader) {
    Cache cache = cache();
    if (cache != null) {
      try {
        Cache.ValueWrapper hit = cache.get(key);
        if (hit != null && hit.get() != null) {
          return (T) hit.get();
        }
      } catch (RuntimeException e) {
        log.warn("Dictionary cache read failed ({}); using database", e.toString());
      }
    }
    T value = loader.get();
    if (cache != null && value != null) {
      try {
        cache.put(key, value);
      } catch (RuntimeException e) {
        log.warn("Dictionary cache write failed ({})", e.toString());
      }
    }
    return value;
  }

  public void evictAll() {
    Cache cache = cache();
    if (cache != null) {
      try {
        cache.clear();
      } catch (RuntimeException e) {
        log.warn("Dictionary cache clear failed ({})", e.toString());
      }
    }
  }

  private Cache cache() {
    try {
      CacheManager manager = cacheManagers.getIfAvailable();
      return manager == null ? null : manager.getCache(CACHE_NAME);
    } catch (RuntimeException e) {
      log.warn("Dictionary cache unavailable ({})", e.toString());
      return null;
    }
  }
}
