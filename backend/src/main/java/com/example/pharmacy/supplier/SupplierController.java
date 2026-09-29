package com.example.pharmacy.supplier;

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
public class SupplierController {

  private final MasterService masters;

  public SupplierController(MasterService masters) {
    this.masters = masters;
  }

  @GetMapping("/suppliers")
  Object masters(
    @RequestParam(defaultValue = "") String name,
    @RequestParam(required = false) LocalDate from,
    @RequestParam(required = false) LocalDate to,
    @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
    @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size
  ) {
    return masters.list("suppliers", name, from, to, page, size);
  }

  @GetMapping("/suppliers/{id}")
  Object master(@PathVariable long id) {
    return masters.get("suppliers", id);
  }

  @PostMapping("/suppliers")
  @PreAuthorize("hasRole('OWNER')")
  Object supplier(@Valid @RequestBody Inputs.Supplier in) {
    return masters.supplier(null, in);
  }

  @PutMapping("/suppliers/{id}")
  @PreAuthorize("hasRole('OWNER')")
  Object supplier(@PathVariable long id, @Valid @RequestBody Inputs.Supplier in) {
    return masters.supplier(id, in);
  }
}
