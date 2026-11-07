package com.mediflow.directory.schedule;

import com.mediflow.directory.service.DirectoryValidationException;

/** Schedule template is malformed; {@link #getMessageKey()} is a user-facing message key. */
public class InvalidScheduleException extends DirectoryValidationException {

  public InvalidScheduleException(String messageKey) {
    super(messageKey);
  }
}
