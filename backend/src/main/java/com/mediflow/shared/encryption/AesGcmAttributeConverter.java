package com.mediflow.shared.encryption;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;

/**
 * AES-256-GCM attribute converter. Ciphertext: Base64(iv || ciphertext+tag).
 * Key from {@link FieldEncryptionKeyHolder} (loaded from {@code MEDIFLOW_FIELD_ENCRYPTION_KEY}).
 */
@Converter
public class AesGcmAttributeConverter implements AttributeConverter<String, String> {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_LENGTH = 12;
  private static final int TAG_BITS = 128;

  private final SecureRandom secureRandom = new SecureRandom();

  @Override
  public String convertToDatabaseColumn(String attribute) {
    if (attribute == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_LENGTH];
      secureRandom.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.ENCRYPT_MODE,
          FieldEncryptionKeyHolder.getKey(),
          new GCMParameterSpec(TAG_BITS, iv));
      byte[] cipherText = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));
      ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
      buffer.put(iv);
      buffer.put(cipherText);
      return Base64.getEncoder().encodeToString(buffer.array());
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Failed to encrypt attribute", e);
    }
  }

  @Override
  public String convertToEntityAttribute(String dbData) {
    if (dbData == null) {
      return null;
    }
    try {
      byte[] decoded = Base64.getDecoder().decode(dbData);
      ByteBuffer buffer = ByteBuffer.wrap(decoded);
      byte[] iv = new byte[IV_LENGTH];
      buffer.get(iv);
      byte[] cipherText = new byte[buffer.remaining()];
      buffer.get(cipherText);
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(
          Cipher.DECRYPT_MODE,
          FieldEncryptionKeyHolder.getKey(),
          new GCMParameterSpec(TAG_BITS, iv));
      return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("Failed to decrypt attribute", e);
    }
  }
}
