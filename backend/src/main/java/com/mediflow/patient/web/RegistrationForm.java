package com.mediflow.patient.web;

import com.mediflow.patient.service.NewPatient;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** Shared fields for self sign-up and front-desk walk-in. */
public class RegistrationForm {

  private String givenName;
  private String familyName;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
  private LocalDate dateOfBirth;

  private String sex;
  private String phone;
  private String email;
  private String nationalId;
  private String address;
  private String username;
  private String password;
  private boolean consent;

  public NewPatient toNewPatient() {
    return new NewPatient(givenName, familyName, dateOfBirth, sex, phone, email, nationalId, address);
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

  public String getNationalId() {
    return nationalId;
  }

  public void setNationalId(String nationalId) {
    this.nationalId = nationalId;
  }

  public String getAddress() {
    return address;
  }

  public void setAddress(String address) {
    this.address = address;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public boolean isConsent() {
    return consent;
  }

  public void setConsent(boolean consent) {
    this.consent = consent;
  }
}
