package com.example.pharmacy.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration
@Profile("prod")
public class ProductionConfig {

  @Bean
  InitializingBean productionConfiguration(Environment env) {
    return () -> validate(env);
  }

  public static void validate(Environment env) {
    required(env, "spring.datasource.url");
    required(env, "spring.datasource.username");
    secret(env, "spring.datasource.password");
    secret(env, "app.metrics-token");
    if (env.getProperty("app.seed-demo", Boolean.class, false)) {
      throw new IllegalStateException("Demo seeding must be disabled in production");
    }
    if (env.getProperty("app.redis-enabled", Boolean.class, false)) {
      required(env, "spring.data.redis.host");
      secret(env, "spring.data.redis.password");
    }
    if (env.getProperty("app.messaging-enabled", Boolean.class, false)) {
      required(env, "spring.rabbitmq.host");
      required(env, "spring.rabbitmq.username");
      secret(env, "spring.rabbitmq.password");
    }
  }

  private static void required(Environment env, String key) {
    String value = env.getProperty(key);
    if (
      value == null || value.isBlank() || value.equals("unconfigured")
    ) throw new IllegalStateException("Production configuration requires " + key);
  }

  private static void secret(Environment env, String key) {
    required(env, key);
    if (env.getProperty(key).length() < 12) throw new IllegalStateException(
      "Production secret must have at least 12 characters: " + key
    );
  }
}
