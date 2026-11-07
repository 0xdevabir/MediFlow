package com.mediflow.admin.settings;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Versioned {@code system_setting} access. A key's current value is the row with the highest
 * {@code value_version}; changes append a new version, nothing is updated in place, so the full
 * history stays available.
 *
 * <p>Other modules (e.g. T06 reading {@code ai.confidence.threshold}) should read the same way:
 * {@code SELECT setting_value FROM system_setting WHERE setting_key = ? ORDER BY value_version DESC
 * LIMIT 1}.
 */
@Component
public class SystemSettings {

  public record SettingVersion(
      String key, String value, int valueVersion, String description, Instant createdAt) {}

  private final JdbcTemplate jdbc;

  public SystemSettings(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public Optional<String> current(String key) {
    return jdbc
        .queryForList(
            "SELECT setting_value FROM system_setting WHERE setting_key = ? "
                + "ORDER BY value_version DESC LIMIT 1",
            String.class,
            key)
        .stream()
        .findFirst();
  }

  public int currentInt(String key, int fallback) {
    try {
      return current(key).map(String::trim).map(Integer::parseInt).orElse(fallback);
    } catch (NumberFormatException e) {
      return fallback;
    }
  }

  public Optional<BigDecimal> currentDecimal(String key) {
    return current(key).map(String::trim).map(BigDecimal::new);
  }

  public Optional<SettingVersion> currentVersion(String key) {
    return history(key).stream().findFirst();
  }

  /** Latest version of every key. */
  public List<SettingVersion> latestAll() {
    return jdbc.query(
        """
        SELECT DISTINCT ON (setting_key) setting_key, setting_value, value_version, description, created_at
        FROM system_setting ORDER BY setting_key, value_version DESC
        """,
        (rs, i) -> map(rs));
  }

  /** All versions of one key, newest first. */
  public List<SettingVersion> history(String key) {
    return jdbc.query(
        "SELECT setting_key, setting_value, value_version, description, created_at FROM system_setting "
            + "WHERE setting_key = ? ORDER BY value_version DESC",
        (rs, i) -> map(rs),
        key);
  }

  /**
   * Appends a new version. The unique (setting_key, value_version) constraint makes concurrent
   * writers fail instead of both "winning". Callers must audit and authorise.
   */
  int appendVersion(String key, String value, String description) {
    Integer next =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(value_version), 0) + 1 FROM system_setting WHERE setting_key = ?",
            Integer.class,
            key);
    String desc =
        description != null ? description : currentVersion(key).map(SettingVersion::description).orElse(null);
    jdbc.update(
        "INSERT INTO system_setting (setting_key, setting_value, value_version, description) VALUES (?, ?, ?, ?)",
        key,
        value,
        next,
        desc);
    return next;
  }

  private static SettingVersion map(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new SettingVersion(
        rs.getString("setting_key"),
        rs.getString("setting_value"),
        rs.getInt("value_version"),
        rs.getString("description"),
        rs.getTimestamp("created_at").toInstant());
  }
}
