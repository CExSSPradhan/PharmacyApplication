package com.example.pharmacy.config;

import com.example.pharmacy.auth.AuthService;
import com.example.pharmacy.common.Store;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

  @Bean
  PasswordEncoder passwords() {
    return new BCryptPasswordEncoder(12);
  }

  @Bean
  SecurityFilterChain security(
    HttpSecurity http,
    AuthService auth,
    Store db,
    StringRedisTemplate redis,
    @Value("${app.redis-enabled}") boolean redisEnabled,
    @Value("${METRICS_TOKEN:}") String metricsToken
  ) throws Exception {
    var filter = new OncePerRequestFilter() {
      @Override
      protected void doFilterInternal(
        HttpServletRequest req,
        HttpServletResponse res,
        FilterChain chain
      ) throws ServletException, IOException {
        String trace = UUID.randomUUID().toString();
        MDC.put("traceId", trace);
        res.setHeader("X-Request-ID", trace);
        try {
          String header = req.getHeader("Authorization");
          AuthService.Principal user = null;
          if (header != null && header.startsWith("Bearer ") && header.length() < 200) {
            user =
              req.getRequestURI().equals("/actuator/prometheus") &&
              !metricsToken.isBlank() &&
              java.security.MessageDigest.isEqual(
                header.substring(7).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                metricsToken.getBytes(java.nio.charset.StandardCharsets.UTF_8)
              )
                ? new AuthService.Principal(0, "Metrics", "metrics@internal", "OWNER")
                : auth.authenticate(header.substring(7));
            if (user != null) {
              SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                  user,
                  null,
                  List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))
                )
              );
              MDC.put("userId", Long.toString(user.id()));
            }
          }
          boolean login = req.getRequestURI().equals("/api/auth/login");
          if (login || user != null) {
            String bucket = login
              ? "login:" + AuthService.hash(req.getRemoteAddr())
              : "api:" + user.id();
            int limit = login ? 20 : 600;
            long count;
            try {
              if (!redisEnabled) throw new IllegalStateException();
              Long n = redis.execute(
                new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
                  "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n",
                  Long.class
                ),
                List.of("rate:" + bucket)
              );
              count = n == null ? 0 : n;
            } catch (Exception e) {
              count = db.number(
                "INSERT INTO rate_limits(bucket,hits,window_end) VALUES (?,1,now()+interval '1 minute') ON CONFLICT(bucket) DO UPDATE SET hits=CASE WHEN rate_limits.window_end<now() THEN 1 ELSE rate_limits.hits+1 END,window_end=CASE WHEN rate_limits.window_end<now() THEN now()+interval '1 minute' ELSE rate_limits.window_end END RETURNING hits",
                bucket
              );
            }
            if (count > limit) {
              res.setHeader("Retry-After", "60");
              write(res, 429, "Too many requests", trace);
              return;
            }
          }
          chain.doFilter(req, res);
        } finally {
          MDC.clear();
          SecurityContextHolder.clearContext();
        }
      }
    };
    return http
      .csrf(c -> c.disable())
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a ->
        a
          .requestMatchers("/api/auth/login", "/actuator/health", "/actuator/health/**")
          .permitAll()
          .requestMatchers("/actuator/**")
          .hasRole("OWNER")
          .anyRequest()
          .authenticated()
      )
      .exceptionHandling(e ->
        e
          .authenticationEntryPoint((q, s, x) ->
            write(s, 401, "Authentication required", MDC.get("traceId"))
          )
          .accessDeniedHandler((q, s, x) -> write(s, 403, "Permission denied", MDC.get("traceId")))
      )
      .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
      .build();
  }

  static void write(HttpServletResponse res, int status, String detail, String trace)
    throws IOException {
    res.setStatus(status);
    res.setContentType("application/problem+json");
    res
      .getWriter()
      .write(
        "{\"status\":" + status + ",\"detail\":\"" + detail + "\",\"traceId\":\"" + trace + "\"}"
      );
  }
}
