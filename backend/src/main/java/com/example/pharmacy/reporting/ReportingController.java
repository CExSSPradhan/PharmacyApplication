package com.example.pharmacy.reporting;

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
public class ReportingController {

  private final InventoryService inventory;

  public ReportingController(InventoryService inventory) {
    this.inventory = inventory;
  }

  @GetMapping({ "/{type:sales|purchases}", "/reports/{type:sales|purchases|stock}" })
  Object history(
    @PathVariable String type,
    @RequestParam(required = false) LocalDate from,
    @RequestParam(required = false) LocalDate to,
    @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
    @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
  ) {
    return inventory.history(type, from, to, page, size);
  }
}
