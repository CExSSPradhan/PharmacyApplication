package com.example.pharmacy.common;

import java.time.Duration;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.*;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReadCache {

  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final boolean enabled;

  public ReadCache(
    StringRedisTemplate redis,
    ObjectMapper json,
    @Value("${app.redis-enabled}") boolean enabled
  ) {
    this.redis = redis;
    this.json = json;
    this.enabled = enabled;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> get(String key, Supplier<Map<String, Object>> source) {
    if (enabled) try {
      String value = redis.opsForValue().get(key);
      if (value != null) return json.readValue(value, Map.class);
    } catch (Exception ignored) {}
    var data = source.get();
    if (enabled) try {
      redis.opsForValue().set(key, json.writeValueAsString(data), Duration.ofMinutes(5));
    } catch (Exception ignored) {}
    return data;
  }

  public void evictAfterCommit(String key) {
    if (enabled) TransactionSynchronizationManager.registerSynchronization(
      new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          try {
            redis.delete(key);
          } catch (Exception ignored) {}
        }
      }
    );
  }
}
