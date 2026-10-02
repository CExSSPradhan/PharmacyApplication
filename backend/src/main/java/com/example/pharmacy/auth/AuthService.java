package com.example.pharmacy.auth;

import com.example.pharmacy.common.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  public record Principal(long id, String name, String email, String role) {}

  private final Store db;
  private final PasswordEncoder passwords;

  private final String dummyHash;
  private final Duration sessionTtl;

  public AuthService(
    Store db,
    PasswordEncoder passwords,
    @Value("${app.session-ttl}") Duration sessionTtl
  ) {
    this.db = db;
    this.passwords = passwords;
    if (sessionTtl.isZero() || sessionTtl.isNegative()) throw new IllegalArgumentException(
      "app.session-ttl must be positive"
    );
    this.sessionTtl = sessionTtl;
    this.dummyHash = passwords.encode(UUID.randomUUID().toString());
  }

  public static String hash(String value) {
    try {
      return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
      );
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public Principal authenticate(String token) {
    var rows = db.list(
      "SELECT u.id,u.name,u.email,u.role FROM auth_tokens t JOIN users u ON u.id=t.user_id WHERE t.token_hash=? AND t.expires_at>now() AND u.status='ACTIVE'",
      hash(token)
    );
    return rows.isEmpty() ? null : principal(rows.getFirst());
  }

  @Transactional
  public Map<String, Object> login(Inputs.Login input) {
    var rows = db.list(
      "SELECT * FROM users WHERE lower(email)=lower(?) AND status='ACTIVE'",
      input.email()
    );
    String encoded = rows.isEmpty() ? dummyHash : rows.getFirst().get("password_hash").toString();
    if (!passwords.matches(input.password(), encoded) || rows.isEmpty()) throw new ApiException(
      401,
      "Invalid email or password"
    );
    byte[] bytes = new byte[32];
    new SecureRandom().nextBytes(bytes);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    db.update(
      "INSERT INTO auth_tokens(token_hash,user_id,expires_at) VALUES (?,?,now()+(? * interval '1 millisecond'))",
      hash(token),
      Store.id(rows.getFirst()),
      sessionTtl.toMillis()
    );
    return Map.of("token", token, "user", principal(rows.getFirst()));
  }

  public void logout(String token) {
    db.update("DELETE FROM auth_tokens WHERE token_hash=?", hash(token));
  }

  private Principal principal(Map<String, Object> row) {
    return new Principal(
      Store.id(row),
      row.get("name").toString(),
      row.get("email").toString(),
      row.get("role").toString()
    );
  }

  public Map<String, Object> createUser(Inputs.User u) {
    long id = db.insert(
      "INSERT INTO users(name,email,password_hash,role) VALUES (?,?,?,?)",
      u.name().trim(),
      u.email().toLowerCase(Locale.ROOT),
      passwords.encode(u.password()),
      u.role()
    );
    return db.one("SELECT id,name,email,role,status FROM users WHERE id=?", id);
  }
}
