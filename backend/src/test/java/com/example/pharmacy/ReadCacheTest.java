package com.example.pharmacy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.example.pharmacy.common.ReadCache;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

class ReadCacheTest {

  @Test
  void unavailableRedisFallsBackToAuthoritativeRead() {
    var redis = mock(StringRedisTemplate.class);
    when(redis.opsForValue()).thenThrow(new RuntimeException("Redis unavailable"));
    var cache = new ReadCache(redis, new ObjectMapper(), true, java.time.Duration.ofMinutes(5));
    assertEquals(
      "Paracetamol",
      cache.get("medicines:1", () -> Map.of("name", "Paracetamol")).get("name")
    );
  }

  @Test
  @SuppressWarnings("unchecked")
  void corruptCacheEntryFallsBackRatherThanFailingTheRequest() {
    var redis = mock(StringRedisTemplate.class);
    ValueOperations<String, String> values = mock(ValueOperations.class);
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("medicines:1")).thenReturn("broken-json");
    var cache = new ReadCache(redis, new ObjectMapper(), true, java.time.Duration.ofMinutes(5));
    assertEquals(7, cache.get("medicines:1", () -> Map.of("id", 7)).get("id"));
  }

  @Test
  void disabledCacheDoesNotRequireRedis() {
    var redis = mock(StringRedisTemplate.class);
    var cache = new ReadCache(redis, new ObjectMapper(), false, java.time.Duration.ofMinutes(5));
    cache.get("suppliers:1", () -> Map.of("name", "Supplier"));
    verifyNoInteractions(redis);
  }
}
