package com.example.pharmacy.sale;

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
public class SaleController {

  private final StockService stock;

  public SaleController(StockService stock) {
    this.stock = stock;
  }

  @PostMapping("/sales")
  Object sale(
    @Valid @RequestBody Inputs.Sale in,
    @AuthenticationPrincipal AuthService.Principal user
  ) {
    return stock.sale(in, user.id());
  }

  @PostMapping("/sales/{id}/cancel")
  @PreAuthorize("hasRole('OWNER')")
  Object cancel(
    @PathVariable long id,
    @Valid @RequestBody Inputs.Cancel in,
    @AuthenticationPrincipal AuthService.Principal user
  ) {
    return stock.cancel(id, in.reason(), user.id());
  }

  @GetMapping("/sales/{id}")
  Object detail(@PathVariable long id) {
    return stock.detail("sales", id);
  }
}
