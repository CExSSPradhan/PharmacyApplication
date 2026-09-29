package com.example.pharmacy.inventory;

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
public class InventoryController {

  private final InventoryService inventory;

  public InventoryController(InventoryService inventory) {
    this.inventory = inventory;
  }

  @GetMapping({
    "/inventory",
    "/inventory/{view:current|low-stock|out-of-stock|near-expiry|expired}",
  })
  Object inventory(
    @PathVariable(required = false) String view,
    @RequestParam(defaultValue = "") String name,
    @RequestParam(defaultValue = "30") @Min(1) @Max(3650) int days,
    @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
    @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
  ) {
    return inventory.inventory(view == null ? "current" : view, name, days, page, size);
  }

  @GetMapping("/inventory/medicines/{id}/batches")
  Object batches(@PathVariable long id) {
    return inventory.batches(id);
  }

  @GetMapping("/inventory/batches/{id}/movements")
  Object movements(@PathVariable long id) {
    return inventory.movements(id);
  }
}
