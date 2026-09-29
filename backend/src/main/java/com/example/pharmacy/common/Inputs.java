package com.example.pharmacy.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class Inputs {

  public record Medicine(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 150) String manufacturer,
    @Size(max = 255) String composition,
    @Size(max = 50) String strength,
    @Size(max = 50) String dosageForm,
    @Size(max = 100) String barcode,
    @Min(0) int lowStockThreshold,
    @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status
  ) {}

  public record Supplier(
    @NotBlank @Size(max = 150) String name,
    @Size(max = 100) String contactPerson,
    @Size(max = 20) String phone,
    @Email @Size(max = 150) String email,
    @Size(max = 2000) String address,
    @Size(max = 30) String gstNumber,
    @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status
  ) {}

  public record PurchaseLine(
    @Positive long medicineId,
    @NotBlank @Size(max = 100) String batchNumber,
    @NotNull LocalDate expiryDate,
    @Min(1) @Max(1000000) int quantity,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal purchasePrice,
    @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal sellingPrice
  ) {}

  public record Purchase(
    @Positive long supplierId,
    @NotBlank @Size(max = 100) String invoiceNumber,
    @NotNull @PastOrPresent LocalDate purchaseDate,
    @NotEmpty @Size(max = 100) List<@Valid PurchaseLine> items
  ) {}

  public record SaleLine(@Positive long medicineId, @Min(1) @Max(1000000) int quantity) {}

  public record Sale(@NotEmpty @Size(max = 100) List<@Valid SaleLine> items) {}

  public record Cancel(@NotBlank @Size(max = 255) String reason) {}

  public record Login(
    @NotBlank @Email @Size(max = 150) String email,
    @NotBlank @Size(max = 200) String password
  ) {}

  public record User(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Email @Size(max = 150) String email,
    @NotBlank @Size(min = 12, max = 72) String password,
    @NotNull @Pattern(regexp = "OWNER|CASHIER") String role
  ) {}
}
