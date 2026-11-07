package com.mediflow.shared.domain;

import com.mediflow.shared.encryption.AesGcmAttributeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "patient")
public class Patient extends BaseEntity {

  @Column(name = "user_id", unique = true)
  private UUID userId;

  @Column(name = "mrn", unique = true)
  private String mrn;

  @Column(name = "given_name", nullable = false)
  private String givenName;

  @Column(name = "family_name", nullable = false)
  private String familyName;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Column(name = "sex")
  private String sex;

  @Convert(converter = AesGcmAttributeConverter.class)
  @Column(name = "national_id")
  private String nationalId;

  @Convert(converter = AesGcmAttributeConverter.class)
  @Column(name = "phone")
  private String phone;

  @Convert(converter = AesGcmAttributeConverter.class)
  @Column(name = "email")
  private String email;

  @Column(name = "address", columnDefinition = "TEXT")
  private String address;

  @Column(name = "pregnancy_status")
  private String pregnancyStatus;

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public String getMrn() {
    return mrn;
  }

  public void setMrn(String mrn) {
    this.mrn = mrn;
  }

  public String getGivenName() {
    return givenName;
  }

  public void setGivenName(String givenName) {
    this.givenName = givenName;
  }

  public String getFamilyName() {
    return familyName;
  }

  public void setFamilyName(String familyName) {
    this.familyName = familyName;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public void setDateOfBirth(LocalDate dateOfBirth) {
    this.dateOfBirth = dateOfBirth;
  }

  public String getSex() {
    return sex;
  }

  public void setSex(String sex) {
    this.sex = sex;
  }

  public String getNationalId() {
    return nationalId;
  }

  public void setNationalId(String nationalId) {
    this.nationalId = nationalId;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String phone) {
    this.phone = phone;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getPregnancyStatus() {
    return pregnancyStatus;
  }

  public void setPregnancyStatus(String pregnancyStatus) {
    this.pregnancyStatus = pregnancyStatus;
  }

}
