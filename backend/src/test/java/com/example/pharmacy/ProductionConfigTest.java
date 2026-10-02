package com.example.pharmacy;

import static org.junit.jupiter.api.Assertions.*;

import com.example.pharmacy.config.ProductionConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ProductionConfigTest {

  private MockEnvironment valid() {
    return new MockEnvironment()
      .withProperty("spring.datasource.url", "jdbc:postgresql://db/pharmacy")
      .withProperty("spring.datasource.username", "service")
      .withProperty("spring.datasource.password", java.util.UUID.randomUUID().toString())
      .withProperty("app.metrics-token", java.util.UUID.randomUUID().toString());
  }

  @Test
  void validConfigurationAllowsDisabledOptionalServices() {
    assertDoesNotThrow(() -> ProductionConfig.validate(valid()));
  }

  @Test
  void missingAndWeakSecretsAreRejectedWithoutExposingTheirValues() {
    var env = valid().withProperty("spring.datasource.password", "");
    assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
    env.withProperty("spring.datasource.password", "weak");
    var error = assertThrows(IllegalStateException.class, () -> ProductionConfig.validate(env));
    assertFalse(error.getMessage().contains("weak"));
  }

  @Test
  void demoDataAndUnauthenticatedOptionalServicesAreRejected() {
    assertThrows(IllegalStateException.class, () ->
      ProductionConfig.validate(valid().withProperty("app.seed-demo", "true"))
    );
    assertThrows(IllegalStateException.class, () ->
      ProductionConfig.validate(
        valid()
          .withProperty("app.redis-enabled", "true")
          .withProperty("spring.data.redis.host", "redis")
      )
    );
    assertThrows(IllegalStateException.class, () ->
      ProductionConfig.validate(
        valid()
          .withProperty("app.messaging-enabled", "true")
          .withProperty("spring.rabbitmq.host", "rabbit")
          .withProperty("spring.rabbitmq.username", "service")
      )
    );
  }
}
