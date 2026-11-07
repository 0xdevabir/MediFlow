package com.mediflow.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Table(name = "ai_job")
public class AiJob extends BaseEntity {

  @Column(name = "job_id", nullable = false, unique = true)
  private UUID jobId;

  @Column(name = "encounter_id", nullable = false)
  private UUID encounterId;

  @Column(name = "prescription_id")
  private UUID prescriptionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "input_type", nullable = false)
  private AiInputType inputType;

  @Column(name = "object_key")
  private String objectKey;

  @Column(name = "text_input", columnDefinition = "TEXT")
  private String textInput;

  @Column(name = "locale", nullable = false)
  private String locale;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AiJobStatus status;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "result_json", columnDefinition = "jsonb")
  private String resultJson;

  @Column(name = "model_version")
  private String modelVersion;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  public UUID getJobId() {
    return jobId;
  }

  public void setJobId(UUID jobId) {
    this.jobId = jobId;
  }

  public UUID getEncounterId() {
    return encounterId;
  }

  public void setEncounterId(UUID encounterId) {
    this.encounterId = encounterId;
  }

  public UUID getPrescriptionId() {
    return prescriptionId;
  }

  public void setPrescriptionId(UUID prescriptionId) {
    this.prescriptionId = prescriptionId;
  }

  public AiInputType getInputType() {
    return inputType;
  }

  public void setInputType(AiInputType inputType) {
    this.inputType = inputType;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public String getTextInput() {
    return textInput;
  }

  public void setTextInput(String textInput) {
    this.textInput = textInput;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public AiJobStatus getStatus() {
    return status;
  }

  public void setStatus(AiJobStatus status) {
    this.status = status;
  }

  public String getResultJson() {
    return resultJson;
  }

  public void setResultJson(String resultJson) {
    this.resultJson = resultJson;
  }

  public String getModelVersion() {
    return modelVersion;
  }

  public void setModelVersion(String modelVersion) {
    this.modelVersion = modelVersion;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

}
