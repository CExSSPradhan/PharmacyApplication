package com.example.pharmacy.medicine;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "medicines")
public class MedicineEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String name, manufacturer, composition, strength, barcode, status;

  @Column(name = "dosage_form")
  public String dosageForm;

  @Column(name = "low_stock_threshold")
  public int lowStockThreshold;

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  public Instant updatedAt;
}
