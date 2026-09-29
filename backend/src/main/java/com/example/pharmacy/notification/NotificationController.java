package com.example.pharmacy.notification;

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
public class NotificationController {

  private final Store db;

  public NotificationController(Store db) {
    this.db = db;
  }

  @GetMapping("/notifications")
  Object notifications() {
    return db.list("SELECT * FROM notifications ORDER BY created_at DESC LIMIT 50");
  }
}
