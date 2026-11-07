package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "system_setting")
public class SystemSetting extends BaseEntity {

  @Column(name = "setting_key", nullable = false)
  private String settingKey;

  @Column(name = "setting_value", nullable = false, columnDefinition = "TEXT")
  private String settingValue;

  @Column(name = "value_version", nullable = false)
  private int valueVersion;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  public String getSettingKey() {
    return settingKey;
  }

  public void setSettingKey(String settingKey) {
    this.settingKey = settingKey;
  }

  public String getSettingValue() {
    return settingValue;
  }

  public void setSettingValue(String settingValue) {
    this.settingValue = settingValue;
  }

  public int getValueVersion() {
    return valueVersion;
  }

  public void setValueVersion(int valueVersion) {
    this.valueVersion = valueVersion;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

}
