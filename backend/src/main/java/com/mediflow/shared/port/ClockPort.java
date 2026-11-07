package com.mediflow.shared.port;

import java.time.Instant;

public interface ClockPort {

  Instant now();
}
