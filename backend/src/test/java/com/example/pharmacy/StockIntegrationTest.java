package com.example.pharmacy;

import static org.junit.jupiter.api.Assertions.*;

import com.example.pharmacy.catalog.MasterService;
import com.example.pharmacy.common.*;
import com.example.pharmacy.inventory.InventoryService;
import com.example.pharmacy.stock.StockService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = {
    "app.seed-demo=false",
    "app.owner-password=Testing-password-2026",
    "app.redis-enabled=false",
    "app.messaging-enabled=false",
  }
)
class StockIntegrationTest {

  static PostgreSQLContainer postgres;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry r) {
    String url = System.getenv("TEST_DB_URL");
    if (url != null) {
      if (!url.endsWith("/pharmacy_test")) throw new IllegalStateException(
        "Tests require a dedicated database named pharmacy_test"
      );
      r.add("spring.datasource.url", () -> url);
      r.add("spring.datasource.username", () -> System.getenv("DB_USER"));
      r.add("spring.datasource.password", () -> System.getenv("DB_PASSWORD"));
    } else {
      postgres = new PostgreSQLContainer("postgres:18.1");
      postgres.start();
      r.add("spring.datasource.url", postgres::getJdbcUrl);
      r.add("spring.datasource.username", postgres::getUsername);
      r.add("spring.datasource.password", postgres::getPassword);
    }
  }

  @Autowired
  Store db;

  @Autowired
  StockService stock;

  @Autowired
  MasterService masters;

  @Autowired
  InventoryService inventory;

  @Autowired
  PasswordEncoder passwords;

  @Autowired
  Environment env;

  @Autowired
  ObjectMapper json;

  long user, supplier, medicine;

  @BeforeEach
  void setup() {
    db.update(
      "TRUNCATE notifications,outbox_events,rate_limits,auth_tokens,stock_movements,sale_items,sales,purchase_items,batches,purchases,suppliers,medicines,users RESTART IDENTITY CASCADE"
    );
    user = db.insert(
      "INSERT INTO users(name,email,password_hash,role) VALUES ('Owner','owner@test.local',?,'OWNER')",
      passwords.encode("Testing-password-2026")
    );
    supplier = Store.id(
      masters.supplier(null, new Inputs.Supplier("Supplier", "", "", "", "", "", "ACTIVE"))
    );
    medicine = newMedicine("Medicine");
  }

  long newMedicine(String name) {
    return Store.id(
      masters.medicine(
        null,
        new Inputs.Medicine(name, "Manufacturer", "Composition", "500mg", "Tablet", "", 5, "ACTIVE")
      )
    );
  }

  Inputs.PurchaseLine line(long med, String batch, int qty, int days) {
    return new Inputs.PurchaseLine(
      med,
      batch,
      LocalDate.now().plusDays(days),
      qty,
      new BigDecimal("2.50"),
      new BigDecimal("4.25")
    );
  }

  Map<String, Object> buy(Inputs.PurchaseLine... lines) {
    return stock.purchase(
      new Inputs.Purchase(supplier, UUID.randomUUID().toString(), LocalDate.now(), List.of(lines)),
      user
    );
  }

  Map<String, Object> sell(long med, int qty) {
    return stock.sale(new Inputs.Sale(List.of(new Inputs.SaleLine(med, qty))), user);
  }

  long quantity() {
    return db.number("SELECT COALESCE(sum(current_quantity),0) FROM batches");
  }

  @Test
  void threeMedicinePurchaseIsAudited() {
    buy(
      line(medicine, "A", 10, 30),
      line(newMedicine("Second"), "B", 20, 40),
      line(newMedicine("Third"), "C", 30, 50)
    );
    assertEquals(60, quantity());
    assertEquals(
      3,
      db.number("SELECT count(*) FROM stock_movements WHERE movement_type='PURCHASE_IN'")
    );
    assertEquals(
      new BigDecimal("150.00"),
      db.one("SELECT total_amount FROM purchases").get("total_amount")
    );
  }

  @Test
  void failureOnSecondLineRollsBackEverything() {
    assertThrows(ApiException.class, () ->
      buy(line(medicine, "A", 10, 30), line(medicine, "B", 20, -1))
    );
    assertEquals(0, quantity());
    assertEquals(0, db.number("SELECT count(*) FROM purchases"));
    assertEquals(0, db.number("SELECT count(*) FROM purchase_items"));
    assertEquals(0, db.number("SELECT count(*) FROM stock_movements"));
  }

  @Test
  void duplicateInvoiceAndBatchMismatchDoNotChangeStock() {
    var input = new Inputs.Purchase(
      supplier,
      "INV-1",
      LocalDate.now(),
      List.of(line(medicine, "A", 5, 20))
    );
    stock.purchase(input, user);
    assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () ->
      stock.purchase(input, user)
    );
    assertThrows(ApiException.class, () -> buy(line(medicine, "A", 5, 21)));
    assertEquals(5, quantity());
  }

  @Test
  void fefoSplitsAcrossBatchesAndComputesExactMoney() {
    buy(line(medicine, "LATE", 20, 60), line(medicine, "EARLY", 5, 30));
    var sale = sell(medicine, 8);
    var items = db.list(
      "SELECT i.quantity,b.batch_number FROM sale_items i JOIN batches b ON b.id=i.batch_id ORDER BY i.id"
    );
    assertEquals(5, items.get(0).get("quantity"));
    assertEquals("EARLY", items.get(0).get("batch_number"));
    assertEquals(3, items.get(1).get("quantity"));
    assertEquals(new BigDecimal("34.00"), sale.get("total_amount"));
    assertEquals(17, quantity());
  }

  @Test
  void expiryDateIsAuthoritativeEvenWhenStatusActive() {
    buy(line(medicine, "A", 5, 0));
    db.update("UPDATE batches SET expiry_date=CURRENT_DATE-1,status='ACTIVE'");
    assertThrows(ApiException.class, () -> sell(medicine, 1));
    assertEquals(5, quantity());
    assertEquals(0, db.number("SELECT count(*) FROM sales"));
  }

  @Test
  void todayExpiryIsSellable() {
    buy(line(medicine, "TODAY", 1, 0));
    sell(medicine, 1);
    assertEquals(0, quantity());
  }

  @Test
  void insufficientSecondSaleLineRollsBackFirst() {
    long second = newMedicine("Second");
    buy(line(medicine, "A", 5, 20));
    assertThrows(ApiException.class, () ->
      stock.sale(
        new Inputs.Sale(List.of(new Inputs.SaleLine(medicine, 2), new Inputs.SaleLine(second, 1))),
        user
      )
    );
    assertEquals(5, quantity());
    assertEquals(0, db.number("SELECT count(*) FROM sale_items"));
    assertEquals(
      0,
      db.number("SELECT count(*) FROM stock_movements WHERE movement_type='SALE_OUT'")
    );
  }

  @Test
  void twoCashiersCannotOversellLastUnit() throws Exception {
    buy(line(medicine, "LAST", 1, 30));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      Callable<Boolean> request = () -> {
        start.await();
        try {
          sell(medicine, 1);
          return true;
        } catch (ApiException e) {
          assertEquals(409, e.status);
          return false;
        }
      };
      var a = pool.submit(request);
      var b = pool.submit(request);
      start.countDown();
      assertNotEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
    }
    assertEquals(0, quantity());
    assertEquals(
      1,
      db.number("SELECT sum(quantity) FROM stock_movements WHERE movement_type='SALE_OUT'")
    );
    assertEquals(1, db.number("SELECT count(*) FROM sales"));
  }

  @Test
  void cancelRestoresExactBatchesOnce() {
    buy(line(medicine, "A", 5, 30), line(medicine, "B", 20, 60));
    long id = Store.id(sell(medicine, 8));
    stock.cancel(id, "Customer cancelled", user);
    stock.cancel(id, "Repeated request", user);
    assertEquals(25, quantity());
    assertEquals(5, db.number("SELECT current_quantity FROM batches WHERE batch_number='A'"));
    assertEquals(20, db.number("SELECT current_quantity FROM batches WHERE batch_number='B'"));
    assertEquals(
      8,
      db.number(
        "SELECT sum(quantity) FROM stock_movements WHERE movement_type='SALE_CANCEL_RESTORE'"
      )
    );
    assertEquals(
      "Customer cancelled",
      db.one("SELECT cancellation_reason FROM sales WHERE id=?", id).get("cancellation_reason")
    );
  }

  @Test
  void concurrentCancellationRestoresOnlyOnce() throws Exception {
    buy(line(medicine, "A", 5, 30));
    long id = Store.id(sell(medicine, 3));
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a = pool.submit(() -> stock.cancel(id, "Duplicate request", user));
      var b = pool.submit(() -> stock.cancel(id, "Duplicate request", user));
      a.get(15, TimeUnit.SECONDS);
      b.get(15, TimeUnit.SECONDS);
    }
    assertEquals(5, quantity());
    assertEquals(
      3,
      db.number(
        "SELECT sum(quantity) FROM stock_movements WHERE movement_type='SALE_CANCEL_RESTORE'"
      )
    );
  }

  @Test
  void inactiveMedicineCannotSell() {
    buy(line(medicine, "A", 10, 30));
    db.update("UPDATE medicines SET status='INACTIVE'");
    assertThrows(ApiException.class, () -> sell(medicine, 1));
    assertEquals(10, quantity());
  }

  @Test
  void inventoryIncludesZeroStockAndExpiryViews() {
    assertEquals(1L, inventory.inventory("out-of-stock", "", 30, 0, 25).get("totalElements"));
    buy(line(medicine, "A", 4, 10));
    assertEquals(1L, inventory.inventory("low-stock", "", 30, 0, 25).get("totalElements"));
    assertEquals(1L, inventory.inventory("near-expiry", "", 30, 0, 25).get("totalElements"));
    db.update("UPDATE batches SET expiry_date=CURRENT_DATE-1");
    assertEquals(1L, inventory.inventory("expired", "", 30, 0, 25).get("totalElements"));
  }

  @Test
  void committedSaleSurvivesUnavailableMessaging() {
    buy(line(medicine, "A", 2, 30));
    sell(medicine, 1);
    assertEquals(1, quantity());
    assertEquals(2, db.number("SELECT count(*) FROM outbox_events WHERE published_at IS NULL"));
  }

  HttpResponse<String> http(String path, String method, String body, String token)
    throws Exception {
    var b = HttpRequest.newBuilder(
      URI.create("http://localhost:" + env.getProperty("local.server.port") + path)
    );
    if (token != null) b.header("Authorization", "Bearer " + token);
    b.header("Content-Type", "application/json");
    return HttpClient.newHttpClient().send(
      b.method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build(),
      HttpResponse.BodyHandlers.ofString()
    );
  }

  String login(String email) throws Exception {
    var response = http(
      "/api/auth/login",
      "POST",
      "{\"email\":\"" + email + "\",\"password\":\"Testing-password-2026\"}",
      null
    );
    assertEquals(200, response.statusCode(), response.body());
    return json.readTree(response.body()).get("token").asText();
  }

  @Test
  void apiAuthValidationPaginationAndLogout() throws Exception {
    assertEquals(401, http("/api/medicines", "GET", null, null).statusCode());
    String token = login("owner@test.local");
    assertEquals(200, http("/api/medicines?page=0&size=1", "GET", null, token).statusCode());
    assertEquals(400, http("/api/medicines?size=10000", "GET", null, token).statusCode());
    assertEquals(
      400,
      http(
        "/api/sales",
        "POST",
        "{\"items\":[{\"medicineId\":1,\"quantity\":0}]}",
        token
      ).statusCode()
    );
    assertEquals(200, http("/api/auth/logout", "POST", null, token).statusCode());
    assertEquals(401, http("/api/auth/me", "GET", null, token).statusCode());
  }

  @Test
  void cashierCannotModifyMastersOrCancel() throws Exception {
    db.update(
      "INSERT INTO users(name,email,password_hash,role) VALUES ('Cashier','cashier@test.local',?,'CASHIER')",
      passwords.encode("Testing-password-2026")
    );
    String token = login("cashier@test.local");
    assertEquals(
      403,
      http(
        "/api/medicines",
        "POST",
        "{\"name\":\"Blocked\",\"status\":\"ACTIVE\",\"lowStockThreshold\":0}",
        token
      ).statusCode()
    );
    assertEquals(
      403,
      http("/api/sales/1/cancel", "POST", "{\"reason\":\"Test\"}", token).statusCode()
    );
  }
}
