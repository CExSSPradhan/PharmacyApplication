package com.example.pharmacy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
  excludeName = "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration"
)
@EnableScheduling
public class PharmacyApplication {

  public static void main(String[] args) {
    SpringApplication.run(PharmacyApplication.class, args);
  }
}
