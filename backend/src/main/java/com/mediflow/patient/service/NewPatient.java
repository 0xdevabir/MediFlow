package com.mediflow.patient.service;

import java.time.LocalDate;

/** Demographics captured at registration (self sign-up and front-desk walk-in use the same set). */
public record NewPatient(
    String givenName,
    String familyName,
    LocalDate dateOfBirth,
    String sex,
    String phone,
    String email,
    String nationalId,
    String address) {}
