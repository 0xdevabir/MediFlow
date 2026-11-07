package com.mediflow.shared.encryption;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AesGcmAttributeConverterTest {

  @BeforeEach
  void setKey() {
    FieldEncryptionKeyHolder.setKey(new byte[32]);
  }

  @Test
  void roundTrip() {
    AesGcmAttributeConverter converter = new AesGcmAttributeConverter();
    String cipher = converter.convertToDatabaseColumn("nid-123456");
    assertThat(cipher).isNotEqualTo("nid-123456");
    assertThat(converter.convertToEntityAttribute(cipher)).isEqualTo("nid-123456");
  }

  @Test
  void nullSafe() {
    AesGcmAttributeConverter converter = new AesGcmAttributeConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }
}
