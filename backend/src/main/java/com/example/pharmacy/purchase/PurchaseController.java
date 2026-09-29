package com.example.pharmacy.purchase;

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
public class PurchaseController {

  private final StockService stock;

  public PurchaseController(StockService stock) {
    this.stock = stock;
  }

  @PostMapping("/purchases")
  @PreAuthorize("hasRole('OWNER')")
  Object purchase(
    @Valid @RequestBody Inputs.Purchase in,
    @AuthenticationPrincipal AuthService.Principal user
  ) {
    return stock.purchase(in, user.id());
  }

  @GetMapping("/purchases/{id}")
  Object detail(@PathVariable long id) {
    return stock.detail("purchases", id);
  }
}
