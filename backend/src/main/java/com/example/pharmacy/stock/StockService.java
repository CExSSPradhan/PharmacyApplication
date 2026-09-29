package com.example.pharmacy.stock;

import com.example.pharmacy.common.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

  private final Store db;
  private final MeterRegistry metrics;

  public StockService(Store db, MeterRegistry metrics) {
    this.db = db;
    this.metrics = metrics;
  }

  private void active(String table, long id) {
    var row = db.one("SELECT * FROM " + table + " WHERE id=? FOR UPDATE", id);
    if (!"ACTIVE".equals(row.get("status"))) throw ApiException.conflict(
      "Inactive " + table + " cannot be used in a transaction"
    );
  }

  private void lockMedicines(Collection<Long> ids) {
    ids
      .stream()
      .distinct()
      .sorted()
      .forEach(id -> active("medicines", id));
  }

  private void movement(
    long batch,
    String type,
    int qty,
    String ref,
    long refId,
    long user,
    String notes
  ) {
    db.update(
      "INSERT INTO stock_movements(batch_id,movement_type,quantity,reference_type,reference_id,created_by,notes) VALUES (?,?,?,?,?,?,?)",
      batch,
      type,
      qty,
      ref,
      refId,
      user,
      notes
    );
  }

  private void event(String type, long id) {
    db.update(
      "INSERT INTO outbox_events(event_type,payload) VALUES (?,?)",
      type,
      "{\"type\":\"" + type + "\",\"resourceId\":" + id + "}"
    );
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
      new org.springframework.transaction.support.TransactionSynchronization() {
        @Override
        public void afterCommit() {
          org.slf4j.LoggerFactory.getLogger(StockService.class).info(
            "Committed {} resourceId={}",
            type,
            id
          );
        }
      }
    );
  }

  @Transactional
  public Map<String, Object> purchase(Inputs.Purchase input, long user) {
    active("suppliers", input.supplierId());
    lockMedicines(input.items().stream().map(Inputs.PurchaseLine::medicineId).toList());
    long id = db.insert(
      "INSERT INTO purchases(supplier_id,invoice_number,purchase_date,created_by) VALUES (?,?,?,?)",
      input.supplierId(),
      input.invoiceNumber().trim(),
      input.purchaseDate(),
      user
    );
    BigDecimal total = BigDecimal.ZERO;
    for (var item : input.items()) {
      if (item.expiryDate().isBefore(LocalDate.now())) throw new ApiException(
        400,
        "Cannot receive an expired batch"
      );
      if (
        item.quantity() <= 0 ||
        item.purchasePrice().signum() < 0 ||
        item.sellingPrice().signum() < 0
      ) throw new ApiException(400, "Invalid quantity or price");
      var found = db.list(
        "SELECT * FROM batches WHERE medicine_id=? AND batch_number=? FOR UPDATE",
        item.medicineId(),
        item.batchNumber().trim()
      );
      long batch;
      if (found.isEmpty()) batch = db.insert(
        "INSERT INTO batches(medicine_id,batch_number,expiry_date,purchase_price,selling_price) VALUES (?,?,?,?,?)",
        item.medicineId(),
        item.batchNumber().trim(),
        item.expiryDate(),
        item.purchasePrice(),
        item.sellingPrice()
      );
      else {
        var b = found.getFirst();
        batch = Store.id(b);
        if (
          !b.get("expiry_date").toString().equals(item.expiryDate().toString()) ||
          ((BigDecimal) b.get("purchase_price")).compareTo(item.purchasePrice()) != 0 ||
          ((BigDecimal) b.get("selling_price")).compareTo(item.sellingPrice()) != 0 ||
          !"ACTIVE".equals(b.get("status"))
        ) throw ApiException.conflict(
          "Existing batch has different expiry, prices or status. Use matching batch details."
        );
      }
      BigDecimal line = item.purchasePrice().multiply(BigDecimal.valueOf(item.quantity()));
      db.update(
        "INSERT INTO purchase_items(purchase_id,medicine_id,batch_id,quantity,purchase_price,selling_price,line_total) VALUES (?,?,?,?,?,?,?)",
        id,
        item.medicineId(),
        batch,
        item.quantity(),
        item.purchasePrice(),
        item.sellingPrice(),
        line
      );
      db.update(
        "UPDATE batches SET current_quantity=current_quantity+?,updated_at=now() WHERE id=?",
        item.quantity(),
        batch
      );
      movement(batch, "PURCHASE_IN", item.quantity(), "PURCHASE", id, user, null);
      total = total.add(line);
    }
    db.update("UPDATE purchases SET total_amount=? WHERE id=?", total, id);
    event("PURCHASE_COMPLETED", id);
    return detail("purchases", id);
  }

  @Transactional
  public Map<String, Object> sale(Inputs.Sale input, long user) {
    lockMedicines(input.items().stream().map(Inputs.SaleLine::medicineId).toList());
    long id = db.insert(
      "INSERT INTO sales(invoice_number,created_by) VALUES (?,?)",
      "RX-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT),
      user
    );
    BigDecimal total = BigDecimal.ZERO;
    // Every stock writer locks medicine IDs first in ascending order; batch rows are still explicitly locked.
    // This also serializes first-batch creation with sales and avoids cross-medicine deadlocks.
    for (var line : input.items()) {
      if (line.quantity() <= 0) throw new ApiException(400, "Quantity must be positive");
      var batches = db.list(
        "SELECT * FROM batches WHERE medicine_id=? AND current_quantity>0 AND status='ACTIVE' AND expiry_date>=CURRENT_DATE ORDER BY expiry_date,id FOR UPDATE",
        line.medicineId()
      );
      long available = batches.stream().mapToLong(Store::qty).sum();
      if (available < line.quantity()) throw ApiException.conflict(
        "Requested " +
          line.quantity() +
          " units; only " +
          available +
          " sellable units remain for medicine #" +
          line.medicineId()
      );
      int remaining = line.quantity();
      for (var batch : batches) {
        if (remaining == 0) break;
        int take = Math.min(remaining, Store.qty(batch));
        long batchId = Store.id(batch);
        BigDecimal price = (BigDecimal) batch.get("selling_price");
        BigDecimal amount = price.multiply(BigDecimal.valueOf(take));
        db.update(
          "INSERT INTO sale_items(sale_id,medicine_id,batch_id,quantity,unit_price,line_total) VALUES (?,?,?,?,?,?)",
          id,
          line.medicineId(),
          batchId,
          take,
          price,
          amount
        );
        db.update(
          "UPDATE batches SET current_quantity=current_quantity-?,updated_at=now() WHERE id=?",
          take,
          batchId
        );
        movement(batchId, "SALE_OUT", take, "SALE", id, user, null);
        remaining -= take;
        total = total.add(amount);
      }
    }
    db.update("UPDATE sales SET total_amount=? WHERE id=?", total, id);
    event("SALE_COMPLETED", id);
    afterCommitMetric("pharmacy.sales.completed");
    return detail("sales", id);
  }

  @Transactional
  public Map<String, Object> cancel(long id, String reason, long user) {
    var sale = db.one("SELECT * FROM sales WHERE id=? FOR UPDATE", id);
    if ("CANCELLED".equals(sale.get("status"))) return detail("sales", id);
    if (reason == null || reason.isBlank() || reason.length() > 255) throw new ApiException(
      400,
      "A cancellation reason is required"
    );
    var items = db.list("SELECT * FROM sale_items WHERE sale_id=? ORDER BY batch_id", id);
    // Cancellation can restore inactive medicines; it never makes expired units sellable.
    items
      .stream()
      .map(i -> ((Number) i.get("medicine_id")).longValue())
      .distinct()
      .sorted()
      .forEach(m -> db.one("SELECT id FROM medicines WHERE id=? FOR UPDATE", m));
    for (var item : items) {
      long batch = ((Number) item.get("batch_id")).longValue();
      int quantity = ((Number) item.get("quantity")).intValue();
      db.one("SELECT id FROM batches WHERE id=? FOR UPDATE", batch);
      db.update(
        "UPDATE batches SET current_quantity=current_quantity+?,updated_at=now() WHERE id=?",
        quantity,
        batch
      );
      movement(batch, "SALE_CANCEL_RESTORE", quantity, "SALE", id, user, reason);
    }
    db.update(
      "UPDATE sales SET status='CANCELLED',cancellation_reason=?,cancelled_at=now(),cancelled_by=?,updated_at=now() WHERE id=?",
      reason,
      user,
      id
    );
    event("SALE_CANCELLED", id);
    afterCommitMetric("pharmacy.sales.cancelled");
    return detail("sales", id);
  }

  private void afterCommitMetric(String name) {
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
      new org.springframework.transaction.support.TransactionSynchronization() {
        @Override
        public void afterCommit() {
          metrics.counter(name).increment();
        }
      }
    );
  }

  public Map<String, Object> detail(String table, long id) {
    boolean sale = table.equals("sales");
    var result = new LinkedHashMap<>(db.one("SELECT * FROM " + table + " WHERE id=?", id));
    result.put(
      "items",
      db.list(
        "SELECT i.*,m.name AS medicine_name,b.batch_number,b.expiry_date FROM " +
          (sale ? "sale_items" : "purchase_items") +
          " i JOIN medicines m ON m.id=i.medicine_id JOIN batches b ON b.id=i.batch_id WHERE i." +
          (sale ? "sale_id" : "purchase_id") +
          "=? ORDER BY i.id",
        id
      )
    );
    return result;
  }
}
