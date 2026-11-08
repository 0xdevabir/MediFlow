package com.mediflow.notification.event;

import com.mediflow.notification.domain.Channel;
import com.mediflow.notification.domain.NotificationEventType;
import com.mediflow.notification.domain.Priority;
import com.mediflow.notification.repo.BookedAppointmentRef;
import com.mediflow.notification.repo.RecipientLookupRepository;
import com.mediflow.notification.service.NotificationService;
import com.mediflow.notification.service.NotificationService.Request;
import com.mediflow.notification.service.RecipientResolver;
import com.mediflow.notification.service.ReminderService;
import com.mediflow.notification.service.StaffCallListService;
import com.mediflow.shared.domain.AppointmentStatus;
import com.mediflow.shared.domain.Role;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.repo.LabOrderRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Turns every domain event in {@link DomainEvents} that concerns a person into notifications /
 * reminders. Runs after the publisher commits, in its own transaction; a failure here never breaks
 * the publishing workflow.
 */
@Component
public class NotificationEventListeners {

  private static final Logger log = LoggerFactory.getLogger(NotificationEventListeners.class);

  private final NotificationService notifications;
  private final ReminderService reminders;
  private final RecipientResolver recipients;
  private final RecipientLookupRepository lookup;
  private final LabOrderRepository labOrders;
  private final StaffCallListService callList;

  public NotificationEventListeners(
      NotificationService notifications,
      ReminderService reminders,
      RecipientResolver recipients,
      RecipientLookupRepository lookup,
      LabOrderRepository labOrders,
      StaffCallListService callList) {
    this.notifications = notifications;
    this.reminders = reminders;
    this.recipients = recipients;
    this.lookup = lookup;
    this.labOrders = labOrders;
    this.callList = callList;
  }

  // ------------------------------------------------------------------ patient lifecycle

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.PatientRegistered e) {
    safely(e, () -> notifications.send(Request.toPatient(NotificationEventType.WELCOME, e.patientId(), name(e.patientId()))));
  }

  // ------------------------------------------------------------------ appointments / queue

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.AppointmentBooked e) {
    safely(
        e,
        () -> {
          Optional<Instant> start = first(lookup.findAppointmentStart(e.appointmentId()));
          Object when = start.<Object>map(i -> i).orElse("-");
          String patient = name(e.patientId());
          notifications.send(Request.toPatient(NotificationEventType.APPOINTMENT_BOOKED, e.patientId(), patient, when));
          doctorUser(e.appointmentId())
              .ifPresent(
                  doc ->
                      notifications.send(
                          Request.toUser(NotificationEventType.APPOINTMENT_BOOKED_DOCTOR, doc, patient, when)));
          start.ifPresent(s -> reminders.scheduleForAppointment(e.appointmentId(), e.patientId(), s));
        });
  }

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.AppointmentCancelled e) {
    safely(
        e,
        () -> {
          reminders.cancelForSource(e.appointmentId());
          String patient = name(e.patientId());
          notifications.send(Request.toPatient(NotificationEventType.APPOINTMENT_CANCELLED, e.patientId(), patient));
          doctorUser(e.appointmentId())
              .ifPresent(
                  doc ->
                      notifications.send(
                          Request.toUser(NotificationEventType.APPOINTMENT_CANCELLED_DOCTOR, doc, patient)));
        });
  }

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.AppointmentCheckedIn e) {
    safely(
        e,
        () -> {
          reminders.cancelForSource(e.appointmentId()); // patient is here: no more visit reminders
          String token = first(lookup.findQueueToken(e.appointmentId())).orElse("-");
          notifications.send(
              Request.toPatient(NotificationEventType.APPOINTMENT_CHECKED_IN, e.patientId(), name(e.patientId()), token));
        });
  }

  /** URGENT / CRITICAL triage → priority alert to the appointment's doctor (Table 5). */
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.TriageLevelSet e) {
    if (e.level() == TriageLevel.NORMAL) {
      return;
    }
    safely(
        e,
        () ->
            doctorUser(e.appointmentId())
                .ifPresent(
                    doc ->
                        notifications.send(
                            Request.toUser(NotificationEventType.TRIAGE_PRIORITY, doc, name(e.patientId()), e.level().name())
                                .withPriority(Priority.HIGH))));
  }

  /** Red Alert → every ER user, nurses, and the doctors on duty at that hospital — HIGH priority. */
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.RedAlertRaised e) {
    safely(
        e,
        () -> {
          Set<UUID> staff = new LinkedHashSet<>(lookup.findEnabledUserIdsByRole(Role.ROLE_ER));
          staff.addAll(lookup.findEnabledUserIdsByRole(Role.ROLE_NURSE));
          staff.addAll(lookup.findUserIdsAtHospital(e.hospitalId(), Role.ROLE_DOCTOR));
          String patient = name(e.patientId());
          for (UUID userId : staff) {
            notifications.send(
                Request.toUser(NotificationEventType.RED_ALERT, userId, patient, e.level().name(), e.at())
                    .withPriority(Priority.HIGH)
                    .withChannels(EnumSet.of(Channel.IN_APP, Channel.PUSH)));
          }
          if (staff.isEmpty()) {
            callList.addOrUpdate(
                null, e.patientId(), null, NotificationEventType.RED_ALERT, Priority.HIGH,
                "No ER / nurse / duty-doctor account to alert", "RED ALERT: " + patient);
          }
        });
  }


  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.QueueCalled e) {
    safely(
        e,
        () ->
            notifications.send(
                Request.toPatient(
                    NotificationEventType.QUEUE_NEXT,
                    e.patientId(),
                    name(e.patientId()),
                    e.tokenNo() == null ? "-" : e.tokenNo())));
  }

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.ScheduleChanged e) {
    safely(
        e,
        () -> {
          reminders.scheduleForAppointment(e.appointmentId(), e.patientId(), e.newStart());
          String patient = name(e.patientId());
          notifications.send(
              Request.toPatient(
                  NotificationEventType.SCHEDULE_CHANGED, e.patientId(), patient, e.newStart()));
          doctorUser(e.appointmentId())
              .ifPresent(
                  doc ->
                      notifications.send(
                          Request.toUser(
                              NotificationEventType.SCHEDULE_CHANGED, doc, patient, e.newStart())));
        });
  }

  /**
   * Doctor schedule / role edit left booked slots in place: notify each resolvable patient (and
   * replan visit reminders). No-ops cleanly when the affected-slot list is empty.
   */
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.PractitionerScheduleChanged e) {
    safely(
        e,
        () -> {
          List<UUID> slotIds = e.affectedBookedSlotIds();
          if (slotIds.isEmpty()) {
            log.info(
                "Practitioner schedule changed role={} with no booked slots to notify",
                e.practitionerRoleId());
            return;
          }
          List<BookedAppointmentRef> booked =
              lookup.findActiveAppointmentsBySlotIds(slotIds, AppointmentStatus.CANCELLED);
          if (booked.isEmpty()) {
            log.info(
                "Practitioner schedule changed role={} slots={} but no active appointments",
                e.practitionerRoleId(),
                slotIds.size());
            return;
          }
          for (BookedAppointmentRef appt : booked) {
            reminders.scheduleForAppointment(
                appt.appointmentId(), appt.patientId(), appt.startsAt());
            String patient = name(appt.patientId());
            notifications.send(
                Request.toPatient(
                    NotificationEventType.SCHEDULE_CHANGED,
                    appt.patientId(),
                    patient,
                    appt.startsAt()));
            doctorUser(appt.appointmentId())
                .ifPresent(
                    doc ->
                        notifications.send(
                            Request.toUser(
                                NotificationEventType.SCHEDULE_CHANGED,
                                doc,
                                patient,
                                appt.startsAt())));
          }
        });
  }

  /** Clinical / Rx follow-up date → day-before + day-of visit reminders. */
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.FollowUpScheduled e) {
    safely(e, () -> reminders.scheduleFollowUp(e.sourceId(), e.patientId(), e.date()));
  }

  // ------------------------------------------------------------------ lab

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.LabOrderCreated e) {
    safely(
        e,
        () ->
            notifications.send(
                Request.toPatient(NotificationEventType.LAB_ORDER_CREATED, e.patientId(), name(e.patientId()))));
  }

  /**
   * Verified report: normal → "report ready"; abnormal → "please see a doctor"; critical → urgent
   * SMS to the patient, HIGH alert to the ordering / duty doctors and a staff phone-call entry.
   */
  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.LabReportVerified e) {
    safely(
        e,
        () -> {
          String patient = name(e.patientId());
          var order = labOrders.findById(e.labOrderId());
          Set<UUID> doctors = new LinkedHashSet<>();
          order.map(o -> o.getOrderedBy()).ifPresent(doctors::add);
          if (e.critical()) {
            notifications.send(
                Request.toPatient(NotificationEventType.LAB_CRITICAL_PATIENT, e.patientId(), patient)
                    .withPriority(Priority.HIGH));
            order.ifPresent(o -> doctors.addAll(lookup.findUserIdsAtHospital(o.getHospitalId(), Role.ROLE_DOCTOR)));
            for (UUID doc : doctors) {
              notifications.send(
                  Request.toUser(NotificationEventType.LAB_CRITICAL_DOCTOR, doc, patient)
                      .withPriority(Priority.HIGH)
                      .withChannels(EnumSet.of(Channel.IN_APP, Channel.PUSH)));
            }
            // Critical results are always phoned through as well (PDF §M5: "phone + duty doctor alert").
            callList.addOrUpdate(
                null, e.patientId(), null, NotificationEventType.LAB_CRITICAL_PATIENT, Priority.HIGH,
                "Critical lab result: phone the patient", null);
          } else {
            notifications.send(
                Request.toPatient(
                    e.abnormal() ? NotificationEventType.SEE_DOCTOR : NotificationEventType.REPORT_READY,
                    e.patientId(),
                    patient));
            for (UUID doc : doctors) {
              notifications.send(Request.toUser(NotificationEventType.REPORT_READY_DOCTOR, doc, patient));
            }
          }
        });
  }

  // ------------------------------------------------------------------ prescription / pharmacy

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.PrescriptionSigned e) {
    safely(
        e,
        () -> {
          reminders.scheduleForPrescription(e.prescriptionId(), e.patientId(), e.at());
          notifications.send(Request.toPatient(NotificationEventType.RX_SIGNED, e.patientId(), name(e.patientId())));
        });
  }

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.PrescriptionRevoked e) {
    safely(
        e,
        () -> {
          reminders.cancelForSource(e.prescriptionId());
          notifications.send(
              Request.toPatient(NotificationEventType.RX_REVOKED, e.patientId(), name(e.patientId()))
                  .withPriority(Priority.HIGH));
        });
  }

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.PrescriptionAmended e) {
    safely(
        e,
        () -> {
          reminders.scheduleForPrescription(e.prescriptionId(), e.patientId(), e.at());
          notifications.send(Request.toPatient(NotificationEventType.RX_AMENDED, e.patientId(), name(e.patientId())));
        });
  }

  @TransactionalEventListener(fallbackExecution = true)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void on(DomainEvents.MedicationDispensed e) {
    safely(
        e,
        () ->
            notifications.send(
                Request.toPatient(NotificationEventType.MEDICATION_DISPENSED, e.patientId(), name(e.patientId()))));
  }

  // ------------------------------------------------------------------ helpers

  private String name(UUID patientId) {
    String n = recipients.contact(null, patientId).displayName();
    return n == null || n.isBlank() ? "-" : n;
  }

  private Optional<UUID> doctorUser(UUID appointmentId) {
    return first(lookup.findAppointmentDoctor(appointmentId))
        .flatMap(practitionerId -> first(lookup.findPractitionerUserId(practitionerId)));
  }

  private static <T> Optional<T> first(List<T> list) {
    return list.stream().filter(java.util.Objects::nonNull).findFirst();
  }

  private void safely(Object event, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException ex) {
      log.error("Notification handling failed for {}", event.getClass().getSimpleName(), ex);
    }
  }
}
