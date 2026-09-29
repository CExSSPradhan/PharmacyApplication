package com.example.pharmacy.user;

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
public class UserController {

  private final AuthService auth;
  private final Store db;

  public UserController(AuthService auth, Store db) {
    this.auth = auth;
    this.db = db;
  }

  @GetMapping("/users")
  @PreAuthorize("hasRole('OWNER')")
  Object users() {
    return db.list("SELECT id,name,email,role,status FROM users ORDER BY id");
  }

  @PostMapping("/users")
  @PreAuthorize("hasRole('OWNER')")
  Object user(@Valid @RequestBody Inputs.User in) {
    return auth.createUser(in);
  }
}
