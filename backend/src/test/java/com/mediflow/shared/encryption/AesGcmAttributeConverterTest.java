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
  void randomIvGivesDifferentCiphertexts() {
    AesGcmAttributeConverter converter = new AesGcmAttributeConverter();
    assertThat(converter.convertToDatabaseColumn("01711000000"))
        .isNotEqualTo(converter.convertToDatabaseColumn("01711000000"));
  }

  @Test
  void tamperedCiphertextIsRejected() {
    AesGcmAttributeConverter converter = new AesGcmAttributeConverter();
    byte[] raw = java.util.Base64.getDecoder().decode(converter.convertToDatabaseColumn("secret"));
    raw[raw.length - 1] ^= 0x01;
    String tampered = java.util.Base64.getEncoder().encodeToString(raw);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> converter.convertToEntityAttribute(tampered))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void nullSafe() {
    AesGcmAttributeConverter converter = new AesGcmAttributeConverter();
    assertThat(converter.convertToDatabaseColumn(null)).isNull();
    assertThat(converter.convertToEntityAttribute(null)).isNull();
  }
}
