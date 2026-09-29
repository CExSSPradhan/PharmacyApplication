package com.example.pharmacy.inventory;

import com.example.pharmacy.common.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {

  private final Store db;

  public InventoryService(Store db) {
    this.db = db;
  }

  public static final String STOCK =
    "SELECT m.id,m.name,m.manufacturer,m.strength,m.status,m.low_stock_threshold,COALESCE(SUM(b.current_quantity) FILTER (WHERE b.status='ACTIVE' AND b.expiry_date>=CURRENT_DATE),0) AS available_quantity,COALESCE(SUM(b.current_quantity) FILTER (WHERE b.expiry_date<CURRENT_DATE),0) AS expired_quantity,COALESCE(SUM(b.current_quantity*b.purchase_price),0) AS stock_value FROM medicines m LEFT JOIN batches b ON b.medicine_id=m.id GROUP BY m.id";

  public Map<String, Object> inventory(String view, String name, int days, int page, int size) {
    String base;
    var args = new ArrayList<Object>();
    if (view.equals("near-expiry") || view.equals("expired")) {
      base =
        "SELECT b.*,m.name,m.strength FROM batches b JOIN medicines m ON m.id=b.medicine_id WHERE b.current_quantity>0 AND m.name ILIKE ? AND " +
        (view.equals("expired")
          ? "b.expiry_date<CURRENT_DATE"
          : "b.status='ACTIVE' AND b.expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE+?");
      args.add("%" + name + "%");
      if (view.equals("near-expiry")) args.add(days);
    } else {
      base = "SELECT * FROM (" + STOCK + ") s WHERE name ILIKE ?";
      args.add("%" + name + "%");
      base += switch (view) {
        case "low-stock" -> " AND status='ACTIVE' AND available_quantity<=low_stock_threshold";
        case "out-of-stock" -> " AND status='ACTIVE' AND available_quantity=0";
        case "current" -> "";
        default -> throw new ApiException(400, "Unknown inventory view");
      };
    }
    long count = db.number("SELECT count(*) FROM (" + base + ") counted", args.toArray());
    args.add(size);
    args.add(page * size);
    return Map.of(
      "content",
      db.list(base + " ORDER BY id DESC LIMIT ? OFFSET ?", args.toArray()),
      "totalElements",
      count,
      "page",
      page,
      "size",
      size
    );
  }

  public List<Map<String, Object>> batches(long medicine) {
    return db.list("SELECT * FROM batches WHERE medicine_id=? ORDER BY expiry_date,id", medicine);
  }

  public List<Map<String, Object>> movements(long batch) {
    return db.list(
      "SELECT s.*,u.name AS user_name FROM stock_movements s JOIN users u ON u.id=s.created_by WHERE batch_id=? ORDER BY s.id DESC LIMIT 500",
      batch
    );
  }

  public Map<String, Object> summary() {
    var result = new LinkedHashMap<>(
      db.one(
        "SELECT COALESCE(sum(total_amount),0) AS today_sales,count(*) AS bill_count FROM sales WHERE status='COMPLETED' AND sale_date>=CURRENT_DATE AND sale_date<CURRENT_DATE+interval '1 day'"
      )
    );
    result.put(
      "low_stock_count",
      db.number(
        "SELECT count(*) FROM (" +
          STOCK +
          ") s WHERE status='ACTIVE' AND available_quantity<=low_stock_threshold"
      )
    );
    result.put(
      "near_expiry_count",
      db.number(
        "SELECT count(*) FROM batches WHERE status='ACTIVE' AND current_quantity>0 AND expiry_date BETWEEN CURRENT_DATE AND CURRENT_DATE+30"
      )
    );
    result.put(
      "expired_stock_count",
      db.number(
        "SELECT count(*) FROM batches WHERE current_quantity>0 AND expiry_date<CURRENT_DATE"
      )
    );
    result.put("medicine_count", db.number("SELECT count(*) FROM medicines WHERE status='ACTIVE'"));
    result.put("recent_sales", db.list("SELECT * FROM sales ORDER BY id DESC LIMIT 5"));
    return result;
  }

  public Map<String, Object> history(
    String type,
    LocalDate from,
    LocalDate to,
    int page,
    int size
  ) {
    if (from != null && to != null && from.isAfter(to)) throw new ApiException(
      400,
      "From date must precede to date"
    );
    if (type.equals("stock")) return inventory("current", "", 30, page, size);
    if (!Set.of("sales", "purchases").contains(type)) throw new ApiException(400, "Unknown report");
    String date = type.equals("sales") ? "sale_date" : "purchase_date";
    String where = " WHERE " + date + ">=? AND " + date + "<?";
    LocalDate start = from == null ? LocalDate.of(1900, 1, 1) : from;
    LocalDate end = to == null ? LocalDate.of(9999, 1, 1) : to.plusDays(1);
    return Map.of(
      "content",
      db.list(
        "SELECT * FROM " + type + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
        start,
        end,
        size,
        page * size
      ),
      "totalElements",
      db.number("SELECT count(*) FROM " + type + where, start, end),
      "totalAmount",
      db
        .one(
          "SELECT COALESCE(sum(total_amount),0) AS total FROM " +
            type +
            where +
            " AND status='COMPLETED'",
          start,
          end
        )
        .get("total"),
      "page",
      page,
      "size",
      size
    );
  }
}
