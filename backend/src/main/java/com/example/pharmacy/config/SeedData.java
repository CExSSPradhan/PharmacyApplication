package com.example.pharmacy.config;

import com.example.pharmacy.catalog.MasterService;
import com.example.pharmacy.common.*;
import com.example.pharmacy.stock.StockService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
public class SeedData {

  @Bean
  ApplicationRunner seed(
    Store db,
    PasswordEncoder passwords,
    MasterService masters,
    StockService stock,
    TransactionTemplate tx,
    @Value("${app.owner-email}") String email,
    @Value("${app.owner-password}") String password,
    @Value("${app.seed-demo}") boolean demo
  ) {
    return args -> {
      if (db.number("SELECT count(*) FROM users") == 0) {
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new IllegalStateException(
          "Set OWNER_EMAIL to a valid email for first startup"
        );
        if (
          password.length() < 12 ||
          password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
        ) throw new IllegalStateException(
          "Set OWNER_PASSWORD to 12 or more characters and at most 72 UTF-8 bytes for first startup"
        );
        db.update(
          "INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,'OWNER')",
          "Pharmacy Owner",
          email.toLowerCase(Locale.ROOT),
          passwords.encode(password)
        );
      }
      if (!demo || db.number("SELECT count(*) FROM medicines") > 0) return;
      tx.executeWithoutResult(status -> {
        long owner = db.number("SELECT id FROM users WHERE role='OWNER' ORDER BY id LIMIT 1");
        long supplier = Store.id(
          masters.supplier(
            null,
            new Inputs.Supplier(
              "Medline Distribution",
              "Anita Shah",
              "9876543210",
              "orders@example.test",
              "Bhubaneswar, Odisha",
              "",
              "ACTIVE"
            )
          )
        );
        String[] names = {
          "Paracetamol",
          "Amoxicillin",
          "Cetirizine",
          "Omeprazole",
          "Vitamin C",
          "ORS Electrolytes",
        };
        String[] strengths = { "500 mg", "250 mg", "10 mg", "20 mg", "500 mg", "21 g" };
        var lines = new ArrayList<Inputs.PurchaseLine>();
        for (int i = 0; i < names.length; i++) {
          long medicine = Store.id(
            masters.medicine(
              null,
              new Inputs.Medicine(
                names[i],
                "Demo Pharma",
                names[i],
                strengths[i],
                i == 5 ? "Sachet" : "Tablet",
                "",
                20,
                "ACTIVE"
              )
            )
          );
          if (i == 4) continue;
          lines.add(
            new Inputs.PurchaseLine(
              medicine,
              "DEMO-" + (i + 1),
              LocalDate.now().plusDays(i == 2 ? 20 : 180 + i * 30),
              i == 1 ? 12 : 100,
              BigDecimal.valueOf(2 + i),
              BigDecimal.valueOf(4 + i * 2)
            )
          );
        }
        stock.purchase(
          new Inputs.Purchase(supplier, "DEMO-OPENING-001", LocalDate.now(), lines),
          owner
        );
      });
    };
  }

  @Bean
  TransactionTemplate transactionTemplate(
    org.springframework.transaction.PlatformTransactionManager manager
  ) {
    return new TransactionTemplate(manager);
  }
}
