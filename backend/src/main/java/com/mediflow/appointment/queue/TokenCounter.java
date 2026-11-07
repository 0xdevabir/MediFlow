package com.mediflow.appointment.queue;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Per-doctor daily token sequence (table {@code appointment_token_counter}). */
@Entity
@Table(name = "appointment_token_counter")
@IdClass(TokenCounter.Pk.class)
public class TokenCounter {

  @Id
  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Id
  @Column(name = "queue_date", nullable = false)
  private LocalDate queueDate;

  @Column(name = "last_seq", nullable = false)
  private int lastSeq;

  public UUID getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(UUID doctorId) {
    this.doctorId = doctorId;
  }

  public LocalDate getQueueDate() {
    return queueDate;
  }

  public void setQueueDate(LocalDate queueDate) {
    this.queueDate = queueDate;
  }

  public int getLastSeq() {
    return lastSeq;
  }

  public void setLastSeq(int lastSeq) {
    this.lastSeq = lastSeq;
  }

  public static final class Pk implements Serializable {
    private UUID doctorId;
    private LocalDate queueDate;

    public Pk() {}

    public Pk(UUID doctorId, LocalDate queueDate) {
      this.doctorId = doctorId;
      this.queueDate = queueDate;
    }

    public UUID getDoctorId() {
      return doctorId;
    }

    public void setDoctorId(UUID doctorId) {
      this.doctorId = doctorId;
    }

    public LocalDate getQueueDate() {
      return queueDate;
    }

    public void setQueueDate(LocalDate queueDate) {
      this.queueDate = queueDate;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Pk pk)) {
        return false;
      }
      return Objects.equals(doctorId, pk.doctorId) && Objects.equals(queueDate, pk.queueDate);
    }

    @Override
    public int hashCode() {
      return Objects.hash(doctorId, queueDate);
    }
  }
}
