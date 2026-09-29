package com.example.pharmacy.common;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class Store {

  private final JdbcTemplate jdbc;

  public Store(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<Map<String, Object>> list(String sql, Object... args) {
    return jdbc.queryForList(sql, args);
  }

  public Map<String, Object> one(String sql, Object... args) {
    var rows = list(sql, args);
    if (rows.isEmpty()) throw new ApiException(404, "Record not found");
    return rows.getFirst();
  }

  public long number(String sql, Object... args) {
    return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
  }

  public int update(String sql, Object... args) {
    return jdbc.update(sql, args);
  }

  public long insert(String sql, Object... args) {
    return number(sql + " RETURNING id", args);
  }

  public static long id(Map<String, Object> row) {
    return ((Number) row.get("id")).longValue();
  }

  public static int qty(Map<String, Object> row) {
    return ((Number) row.get("current_quantity")).intValue();
  }
}
