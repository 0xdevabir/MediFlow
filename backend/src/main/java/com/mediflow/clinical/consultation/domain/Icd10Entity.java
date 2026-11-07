package com.mediflow.clinical.consultation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clinical_icd10")
public class Icd10Entity {

  @Id
  @Column(name = "code", length = 16)
  private String code;

  @Column(name = "display_en", nullable = false)
  private String displayEn;

  @Column(name = "display_bn")
  private String displayBn;

  @Column(name = "chapter")
  private String chapter;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getDisplayEn() {
    return displayEn;
  }

  public void setDisplayEn(String displayEn) {
    this.displayEn = displayEn;
  }

  public String getDisplayBn() {
    return displayBn;
  }

  public void setDisplayBn(String displayBn) {
    this.displayBn = displayBn;
  }

  public String getChapter() {
    return chapter;
  }

  public void setChapter(String chapter) {
    this.chapter = chapter;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
