package com.mediflow.appointment.queue;

import com.mediflow.shared.domain.QueueStatus;
import com.mediflow.shared.domain.TriageLevel;
import com.mediflow.shared.domain.VitalClass;
import java.time.Instant;
import java.util.UUID;

/** One row on the OPD queue board / patient status payload. */
public record QueueBoardRow(
    UUID queueEntryId,
    UUID appointmentId,
    UUID patientId,
    String tokenNo,
    int tokenSeq,
    TriageLevel level,
    QueueStatus status,
    VitalClass vitals,
    Instant arrivedAt,
    int position,
    int ahead,
    boolean emergencyFlag,
    String patientLabel) {}
