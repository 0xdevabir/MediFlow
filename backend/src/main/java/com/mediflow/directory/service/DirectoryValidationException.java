package com.mediflow.directory.service;

/** Business-rule violation in the directory module; carries a message key (en/bn bundles). */
public class DirectoryValidationException extends RuntimeException {

  private final String messageKey;

  public DirectoryValidationException(String messageKey) {
    super(messageKey);
    this.messageKey = messageKey;
  }

  public String getMessageKey() {
    return messageKey;
  }
}
