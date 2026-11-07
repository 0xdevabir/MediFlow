package com.mediflow.shared.port;

import java.util.UUID;

public interface NotificationPort {

  void notifyUser(UUID recipientUserId, String channel, String locale, String subject, String body);

  void notifyPatient(UUID patientId, String channel, String locale, String subject, String body);
}
