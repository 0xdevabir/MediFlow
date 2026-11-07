package com.mediflow.shared.port;

import java.io.InputStream;
import java.util.Optional;

public interface ObjectStoragePort {

  void put(String objectKey, InputStream data, long contentLength, String contentType);

  Optional<InputStream> get(String objectKey);

  void delete(String objectKey);
}
