package com.mediflow.clinical.consultation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mediflow.clinical.consultation.domain.FollowUpEntity;
import com.mediflow.clinical.consultation.port.ClinicalAccess;
import com.mediflow.clinical.consultation.repo.AllergyConfirmRepository;
import com.mediflow.clinical.consultation.repo.ClinicalAllergyRepository;
import com.mediflow.clinical.consultation.repo.ClinicalAppointmentRepository;
import com.mediflow.clinical.consultation.repo.ClinicalConditionRepository;
import com.mediflow.clinical.consultation.repo.ClinicalDiagnosticReportRepository;
import com.mediflow.clinical.consultation.repo.ClinicalEncounterRepository;
import com.mediflow.clinical.consultation.repo.ClinicalLabOrderRepository;
import com.mediflow.clinical.consultation.repo.ClinicalNoteQueryRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPractitionerRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPrescriptionItemRepository;
import com.mediflow.clinical.consultation.repo.ClinicalPrescriptionRepository;
import com.mediflow.clinical.consultation.repo.FollowUpRepository;
import com.mediflow.clinical.consultation.repo.Icd10Repository;
import com.mediflow.clinical.consultation.service.ConsultationService;
import com.mediflow.clinical.consultation.service.LabReportBadgeListener;
import com.mediflow.clinical.vitals.repo.ClinicalVitalObservationRepository;
import com.mediflow.shared.domain.Encounter;
import com.mediflow.shared.event.DomainEvents;
import com.mediflow.shared.port.AuditPort;
import com.mediflow.shared.port.ClockPort;
import com.mediflow.shared.port.LabOrderPort;
import com.mediflow.shared.repo.DrugGenericRepository;
import com.mediflow.shared.repo.PatientRepository;
import com.mediflow.shared.web.CurrentUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class FollowUpScheduleTest {

  @Test
  void scheduleFollowUpPublishesSharedEvent() {
    ClinicalEncounterRepository encounters = mock(ClinicalEncounterRepository.class);
    FollowUpRepository followUps = mock(FollowUpRepository.class);
    ClinicalAccess access = mock(ClinicalAccess.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    AuditPort audit = mock(AuditPort.class);
    ClockPort clock = mock(ClockPort.class);
    CurrentUser currentUser = mock(CurrentUser.class);
    ClinicalNoteQueryRepository notes = mock(ClinicalNoteQueryRepository.class);

    UUID encounterId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    LocalDate date = LocalDate.of(2026, 12, 1);
    Instant now = Instant.parse("2026-11-08T02:00:00Z");

    Encounter enc = new Encounter();
    enc.setPatientId(patientId);
    // BaseEntity id is package-private setter; use reflection-free approach via mock save
    when(encounters.findById(encounterId)).thenReturn(Optional.of(enc));
    when(access.canConsult(patientId)).thenReturn(true);
    when(currentUser.requireUserId()).thenReturn(userId);
    when(clock.now()).thenReturn(now);
    when(notes.findFirstByEncounterIdOrderByNoteVersionDesc(encounterId))
        .thenReturn(Optional.empty());
    when(followUps.findByEncounterId(encounterId)).thenReturn(Optional.empty());
    when(followUps.save(any(FollowUpEntity.class)))
        .thenAnswer(
            inv -> {
              FollowUpEntity row = inv.getArgument(0);
              row.setId(UUID.randomUUID());
              return row;
            });

    // Encounter.getId() returns null unless set — force findById result to carry id
    enc.setId(encounterId);

    ConsultationService service =
        new ConsultationService(
            encounters,
            mock(ClinicalAppointmentRepository.class),
            mock(ClinicalPractitionerRepository.class),
            mock(PatientRepository.class),
            mock(ClinicalAllergyRepository.class),
            mock(AllergyConfirmRepository.class),
            notes,
            mock(ClinicalConditionRepository.class),
            mock(Icd10Repository.class),
            mock(ClinicalPrescriptionRepository.class),
            mock(ClinicalPrescriptionItemRepository.class),
            mock(DrugGenericRepository.class),
            mock(ClinicalLabOrderRepository.class),
            mock(ClinicalDiagnosticReportRepository.class),
            mock(ClinicalVitalObservationRepository.class),
            followUps,
            events,
            mock(LabOrderPort.class),
            access,
            mock(LabReportBadgeListener.class),
            audit,
            clock,
            currentUser);

    FollowUpEntity saved = service.scheduleFollowUp(encounterId, date);
    assertEquals(date, saved.getFollowUpDate());

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(events).publishEvent(captor.capture());
    DomainEvents.FollowUpScheduled event = (DomainEvents.FollowUpScheduled) captor.getValue();
    assertEquals(encounterId, event.sourceId());
    assertEquals(patientId, event.patientId());
    assertEquals(date, event.date());
    assertEquals(now, event.at());

    verify(audit)
        .record(
            eq(userId),
            eq(patientId),
            eq("FOLLOW_UP_SCHEDULED"),
            eq("Encounter"),
            eq(encounterId),
            any());
  }
}
