package com.example.pharmacy.supplier;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "suppliers")
public class SupplierEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  public String name, phone, email, address, status;

  @Column(name = "contact_person")
  public String contactPerson;

  @Column(name = "gst_number")
  public String gstNumber;

  @Column(name = "created_at", insertable = false, updatable = false)
  public Instant createdAt;

  @Column(name = "updated_at", insertable = false)
  public Instant updatedAt;
}
