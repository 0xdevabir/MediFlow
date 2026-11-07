package com.mediflow.notification.domain;

/**
 * One message template per value: keys {@code notification.tpl.<NAME>.subject} / {@code .body} in
 * {@code messages/notification_{en,bn}.properties}.
 */
public enum NotificationEventType {
  DIRECT,
  WELCOME,
  APPOINTMENT_BOOKED,
  APPOINTMENT_BOOKED_DOCTOR,
  APPOINTMENT_CANCELLED,
  APPOINTMENT_CANCELLED_DOCTOR,
  APPOINTMENT_CHECKED_IN,
  APPOINTMENT_REMINDER_24H,
  APPOINTMENT_REMINDER_2H,
  QUEUE_NEXT,
  TRIAGE_PRIORITY,
  RED_ALERT,
  LAB_ORDER_CREATED,
  REPORT_READY,
  REPORT_READY_DOCTOR,
  SEE_DOCTOR,
  LAB_CRITICAL_PATIENT,
  LAB_CRITICAL_DOCTOR,
  RX_SIGNED,
  RX_REVOKED,
  RX_AMENDED,
  MEDICATION_DISPENSED,
  MEDICATION_REMINDER,
  FOLLOW_UP_REMINDER,
  SCHEDULE_CHANGED;

  public String subjectKey() {
    return "notification.tpl." + name() + ".subject";
  }

  public String bodyKey() {
    return "notification.tpl." + name() + ".body";
  }
}
