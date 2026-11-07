package com.mediflow.shared.port;

import java.util.Map;
import java.util.UUID;

public interface AuditPort {

  void record(
      UUID actorUserId,
      UUID patientId,
      String action,
      String resourceType,
      UUID resourceId,
      Map<String, Object> details);
}
