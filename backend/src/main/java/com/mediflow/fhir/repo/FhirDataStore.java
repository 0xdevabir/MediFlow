package com.mediflow.fhir.repo;

import com.mediflow.shared.domain.Allergy;
import com.mediflow.shared.domain.Appointment;
import com.mediflow.shared.domain.AuditEvent;
import com.mediflow.shared.domain.ConditionDx;
import com.mediflow.shared.domain.Consent;
import com.mediflow.shared.domain.ConsentType;
import com.mediflow.shared.domain.Department;
import com.mediflow.shared.domain.DiagnosticReport;
import com.mediflow.shared.domain.Dispense;
import com.mediflow.shared.domain.DrugBrand;
import com.mediflow.shared.domain.DrugGeneric;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.domain.Hospital;
import com.mediflow.shared.domain.LabOrder;
import com.mediflow.shared.domain.LabTest;
import com.mediflow.shared.domain.Notification;
import com.mediflow.shared.domain.Patient;
import com.mediflow.shared.domain.Practitioner;
import com.mediflow.shared.domain.PractitionerRoleEntity;
import com.mediflow.shared.domain.Prescription;
import com.mediflow.shared.domain.PrescriptionItem;
import com.mediflow.shared.domain.RedAlert;
import com.mediflow.shared.domain.Reminder;
import com.mediflow.shared.domain.Slot;
import com.mediflow.shared.domain.VitalObservation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** Read-side access to shared clinical tables for FHIR mapping (no other-module services). */
@Repository
public class FhirDataStore {

  @PersistenceContext private EntityManager em;

  public <T> Optional<T> find(Class<T> type, UUID id) {
    if (id == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(em.find(type, id));
  }

  public Optional<Patient> patientByUserId(UUID userId) {
    List<Patient> list =
        em.createQuery("select p from Patient p where p.userId = :u", Patient.class)
            .setParameter("u", userId)
            .setMaxResults(1)
            .getResultList();
    return list.stream().findFirst();
  }

  public Optional<Practitioner> practitionerByUserId(UUID userId) {
    List<Practitioner> list =
        em.createQuery("select p from Practitioner p where p.userId = :u", Practitioner.class)
            .setParameter("u", userId)
            .setMaxResults(1)
            .getResultList();
    return list.stream().findFirst();
  }

  public List<PractitionerRoleEntity> rolesForPractitioner(UUID practitionerId) {
    return em.createQuery(
            "select r from PractitionerRoleEntity r where r.practitionerId = :id",
            PractitionerRoleEntity.class)
        .setParameter("id", practitionerId)
        .getResultList();
  }

  public List<Slot> slotsForRole(UUID practitionerRoleId) {
    return em.createQuery(
            "select s from Slot s where s.practitionerRoleId = :id order by s.startsAt", Slot.class)
        .setParameter("id", practitionerRoleId)
        .getResultList();
  }

  public List<Appointment> appointmentsForPatient(UUID patientId) {
    return em.createQuery(
            "select a from Appointment a where a.patientId = :id order by a.bookedAt desc",
            Appointment.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<Encounter> encountersForPatient(UUID patientId) {
    return em.createQuery(
            "select e from Encounter e where e.patientId = :id order by e.startedAt desc",
            Encounter.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public Optional<RedAlert> redAlertForEncounter(UUID encounterId) {
    List<RedAlert> list =
        em.createQuery(
                "select r from RedAlert r where r.encounterId = :id order by r.raisedAt desc",
                RedAlert.class)
            .setParameter("id", encounterId)
            .setMaxResults(1)
            .getResultList();
    return list.stream().findFirst();
  }

  public List<RedAlert> redAlertsForPatient(UUID patientId) {
    return em.createQuery(
            "select r from RedAlert r where r.patientId = :id order by r.raisedAt desc",
            RedAlert.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<VitalObservation> vitalsForPatient(UUID patientId) {
    return em.createQuery(
            "select v from VitalObservation v where v.patientId = :id order by v.recordedAt desc",
            VitalObservation.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<ConditionDx> conditionsForPatient(UUID patientId) {
    return em.createQuery(
            "select c from ConditionDx c where c.patientId = :id order by c.recordedAt desc",
            ConditionDx.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<Allergy> allergiesForPatient(UUID patientId) {
    return em.createQuery(
            "select a from Allergy a where a.patientId = :id", Allergy.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<Prescription> prescriptionsForPatient(UUID patientId) {
    return em.createQuery(
            "select p from Prescription p where p.patientId = :id order by p.createdAt desc",
            Prescription.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<PrescriptionItem> itemsForPrescription(UUID prescriptionId) {
    return em.createQuery(
            "select i from PrescriptionItem i where i.prescriptionId = :id", PrescriptionItem.class)
        .setParameter("id", prescriptionId)
        .getResultList();
  }

  public List<Dispense> dispensesForPatient(UUID patientId) {
    return em.createQuery(
            """
            select d from Dispense d
            where d.prescriptionId in (
              select p.id from Prescription p where p.patientId = :id
            )
            order by d.dispensedAt desc
            """,
            Dispense.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<LabOrder> labOrdersForPatient(UUID patientId) {
    return em.createQuery(
            "select o from LabOrder o where o.patientId = :id order by o.orderedAt desc",
            LabOrder.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public Optional<DiagnosticReport> reportForLabOrder(UUID labOrderId) {
    List<DiagnosticReport> list =
        em.createQuery(
                "select r from DiagnosticReport r where r.labOrderId = :id", DiagnosticReport.class)
            .setParameter("id", labOrderId)
            .setMaxResults(1)
            .getResultList();
    return list.stream().findFirst();
  }

  public List<DiagnosticReport> reportsForPatient(UUID patientId) {
    return em.createQuery(
            """
            select r from DiagnosticReport r
            where r.labOrderId in (
              select o.id from LabOrder o where o.patientId = :id
            )
            """,
            DiagnosticReport.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<Consent> consentsForPatient(UUID patientId) {
    return em.createQuery(
            "select c from Consent c where c.patientId = :id order by c.grantedAt desc",
            Consent.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public boolean hasActiveConsent(UUID patientId, ConsentType type) {
    Long count =
        em.createQuery(
                """
                select count(c) from Consent c
                where c.patientId = :id and c.consentType = :type
                  and c.granted = true and c.revokedAt is null
                """,
                Long.class)
            .setParameter("id", patientId)
            .setParameter("type", type)
            .getSingleResult();
    return count != null && count > 0;
  }

  public List<AuditEvent> auditForPatient(UUID patientId) {
    return em.createQuery(
            "select a from AuditEvent a where a.patientId = :id order by a.occurredAt desc",
            AuditEvent.class)
        .setParameter("id", patientId)
        .setMaxResults(50)
        .getResultList();
  }

  public List<Notification> notificationsForPatient(UUID patientId) {
    return em.createQuery(
            "select n from Notification n where n.patientId = :id order by n.createdAt desc",
            Notification.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public List<Reminder> remindersForPatient(UUID patientId) {
    return em.createQuery(
            "select r from Reminder r where r.patientId = :id order by r.scheduledAt asc",
            Reminder.class)
        .setParameter("id", patientId)
        .getResultList();
  }

  public Optional<Hospital> hospital(UUID id) {
    return find(Hospital.class, id);
  }

  public Optional<Department> department(UUID id) {
    return find(Department.class, id);
  }

  public Optional<LabTest> labTest(UUID id) {
    return find(LabTest.class, id);
  }

  public Optional<DrugGeneric> generic(UUID id) {
    return find(DrugGeneric.class, id);
  }

  public Optional<DrugBrand> brand(UUID id) {
    return find(DrugBrand.class, id);
  }

  public Optional<Prescription> prescription(UUID id) {
    return find(Prescription.class, id);
  }

  public Optional<LabOrder> labOrder(UUID id) {
    return find(LabOrder.class, id);
  }
}
