package com.example.pharmacy.catalog;

import com.example.pharmacy.common.*;
import com.example.pharmacy.medicine.*;
import com.example.pharmacy.supplier.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MasterService {

  private final Store db;
  private final MedicineRepository medicines;
  private final SupplierRepository suppliers;
  private final ReadCache cache;

  public MasterService(
    Store db,
    MedicineRepository medicines,
    SupplierRepository suppliers,
    ReadCache cache
  ) {
    this.db = db;
    this.medicines = medicines;
    this.suppliers = suppliers;
    this.cache = cache;
  }

  public Map<String, Object> list(
    String table,
    String name,
    LocalDate from,
    LocalDate to,
    int page,
    int size
  ) {
    String where = " WHERE name ILIKE ? AND created_at::date >= ? AND created_at::date <= ?";
    Object[] args = {
      "%" + name + "%",
      from == null ? LocalDate.of(1900, 1, 1) : from,
      to == null ? LocalDate.of(9999, 12, 31) : to,
      size,
      page * size,
    };
    return Map.of(
      "content",
      db.list("SELECT * FROM " + table + where + " ORDER BY id DESC LIMIT ? OFFSET ?", args),
      "totalElements",
      db.number("SELECT count(*) FROM " + table + where, args[0], args[1], args[2]),
      "page",
      page,
      "size",
      size
    );
  }

  public Map<String, Object> get(String table, long id) {
    return cache.get(table + ":" + id, () -> db.one("SELECT * FROM " + table + " WHERE id=?", id));
  }

  @Transactional
  public Map<String, Object> medicine(Long id, Inputs.Medicine in) {
    var e =
      id == null
        ? new MedicineEntity()
        : medicines.findById(id).orElseThrow(() -> new ApiException(404, "Medicine not found"));
    e.name = in.name().trim();
    e.manufacturer = in.manufacturer();
    e.composition = in.composition();
    e.strength = in.strength();
    e.dosageForm = in.dosageForm();
    e.barcode = in.barcode();
    e.lowStockThreshold = in.lowStockThreshold();
    e.status = in.status();
    e.updatedAt = Instant.now();
    medicines.saveAndFlush(e);
    cache.evictAfterCommit("medicines:" + e.id);
    return db.one("SELECT * FROM medicines WHERE id=?", e.id);
  }

  @Transactional
  public Map<String, Object> supplier(Long id, Inputs.Supplier in) {
    var e =
      id == null
        ? new SupplierEntity()
        : suppliers.findById(id).orElseThrow(() -> new ApiException(404, "Supplier not found"));
    e.name = in.name().trim();
    e.contactPerson = in.contactPerson();
    e.phone = in.phone();
    e.email = in.email();
    e.address = in.address();
    e.gstNumber = in.gstNumber();
    e.status = in.status();
    e.updatedAt = Instant.now();
    suppliers.saveAndFlush(e);
    cache.evictAfterCommit("suppliers:" + e.id);
    return db.one("SELECT * FROM suppliers WHERE id=?", e.id);
  }
}
