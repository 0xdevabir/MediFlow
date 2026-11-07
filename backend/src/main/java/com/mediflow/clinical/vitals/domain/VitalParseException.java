package com.mediflow.clinical.vitals.domain;

/**
 * Raw vital input that cannot be read. {@link #messageKey()} is a {@code clinical_*} message key;
 * {@link #args()} fill its placeholders.
 */
public class VitalParseException extends RuntimeException {

  public static final String EMPTY = "clinical.vitals.error.empty";
  public static final String NOT_A_NUMBER = "clinical.vitals.error.notNumber";
  public static final String OUT_OF_RANGE = "clinical.vitals.error.implausible";
  public static final String BP_FORMAT = "clinical.vitals.error.bpFormat";
  public static final String BP_ORDER = "clinical.vitals.error.bpOrder";
  public static final String BP_RANGE = "clinical.vitals.error.bpRange";

  private final String vitalCode;
  private final String messageKey;
  private final transient Object[] args;

  public VitalParseException(String vitalCode, String messageKey, Object... args) {
    super(vitalCode + ": " + messageKey);
    this.vitalCode = vitalCode;
    this.messageKey = messageKey;
    this.args = args == null ? new Object[0] : args.clone();
  }

  public String vitalCode() {
    return vitalCode;
  }

  public String messageKey() {
    return messageKey;
  }

  public Object[] args() {
    return args.clone();
  }
}
