package com.example.pharmacy.auth;

import com.example.pharmacy.auth.AuthService;
import com.example.pharmacy.catalog.MasterService;
import com.example.pharmacy.common.*;
import com.example.pharmacy.inventory.InventoryService;
import com.example.pharmacy.stock.StockService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {

  private final AuthService auth;

  public AuthController(AuthService auth) {
    this.auth = auth;
  }

  @PostMapping("/auth/login")
  Object login(@Valid @RequestBody Inputs.Login in) {
    return auth.login(in);
  }

  @GetMapping("/auth/me")
  Object me(@AuthenticationPrincipal AuthService.Principal user) {
    return user;
  }

  @PostMapping("/auth/logout")
  Object logout(@RequestHeader("Authorization") String token) {
    auth.logout(token.substring(7));
    return Map.of("message", "Signed out");
  }
}
