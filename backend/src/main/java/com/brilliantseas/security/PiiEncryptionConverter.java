package com.brilliantseas.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

/**
 * JPA AttributeConverter for PII field encryption.
 *
 * SECURITY DESIGN:
 * ──────────────────────────────────────────────────────────────────
 *   Algorithm:  AES-256-GCM (authenticated encryption with associated data)
 *   Key Source: Environment variable PII_ENCRYPTION_KEY (from Vault in production)
 *   Salt:       Random 16-byte salt prepended to ciphertext (unique per value)
 *   IV:         Random 12-byte IV prepended to ciphertext (unique per encryption)
 *   Output:     Base64(salt[16] + iv[12] + ciphertext + tag[16])
 *
 *   Why AES-GCM over pgcrypto pgp_sym_encrypt:
 *   - Application-layer encryption gives us control over key management
 *   - AES-GCM provides authenticated encryption (integrity + confidentiality)
 *   - pgcrypto is still used as defense-in-depth (DB-level encryption for
 *     fields that bypass the application layer)
 *   - Key rotation is simpler at application layer
 *
 * USAGE on JPA Entity:
 *   @Convert(converter = PiiEncryptionConverter.class)
 *   @Column(name = "id_number", columnDefinition = "BYTEA")
 *   private String idNumber;
 *
 * RA 10173 COMPLIANCE:
 *   §20 — Security of Personal Information: encryption at rest
 *   Decryption triggers data_access_log entry (via AuditEvent in service layer)
 *
 * OWASP COVERAGE:
 *   A02 — Cryptographic Failures: AES-256-GCM, unique salt+IV per value
 * ──────────────────────────────────────────────────────────────────
 */
@Slf4j
@Component
@Converter
public class PiiEncryptionConverter implements AttributeConverter<String, byte[]> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12;       // bytes (GCM standard)
    private static final int SALT_LENGTH = 16;     // bytes
    private static final int KEY_LENGTH = 256;     // bits
    private static final int ITERATION_COUNT = 65536;

    private static final String KEY_ENV = "PII_ENCRYPTION_KEY";

    private static String encryptionKey;
    private static Environment environment;

    @Autowired
    public void setEnvironment(Environment environment) {
        PiiEncryptionConverter.environment = environment;
    }

    @Value("${security.encryption.pii-key}")
    public void setEncryptionKey(String key) {
        PiiEncryptionConverter.encryptionKey = key;
    }

    /**
     * Encrypt plaintext PII value for database storage.
     *
     * @param attribute Plaintext PII value (e.g., government ID number)
     * @return Encrypted bytes: salt[16] + iv[12] + ciphertext + gcmTag[16]
     */
    @Override
    public byte[] convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }

        try {
            SecureRandom secureRandom = new SecureRandom();

            // Generate unique salt and IV for this value
            byte[] salt = new byte[SALT_LENGTH];
            secureRandom.nextBytes(salt);

            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            // Derive AES key from passphrase + salt
            SecretKey key = deriveKey(getEncryptionKeyOrThrow(), salt);

            // Encrypt with AES-GCM
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            // Combine: salt + iv + ciphertext (includes GCM auth tag)
            ByteBuffer buffer = ByteBuffer.allocate(SALT_LENGTH + IV_LENGTH + ciphertext.length);
            buffer.put(salt);
            buffer.put(iv);
            buffer.put(ciphertext);

            return buffer.array();

        } catch (Exception e) {
            log.error("PII encryption failed", e);
            throw new RuntimeException("Failed to encrypt PII data", e);
        }
    }

    /**
     * Decrypt PII value from database.
     * NOTE: Service layer must log this access to data_access_log (RA 10173).
     *
     * @param dbData Encrypted bytes from database
     * @return Plaintext PII value
     */
    @Override
    public String convertToEntityAttribute(byte[] dbData) {
        if (dbData == null) {
            return null;
        }

        try {
            ByteBuffer buffer = ByteBuffer.wrap(dbData);

            // Extract salt, IV, and ciphertext
            byte[] salt = new byte[SALT_LENGTH];
            buffer.get(salt);

            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);

            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // Derive same AES key from passphrase + salt
            SecretKey key = deriveKey(getEncryptionKeyOrThrow(), salt);

            // Decrypt with AES-GCM
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("PII decryption failed — possible key mismatch or data corruption", e);
            throw new RuntimeException("Failed to decrypt PII data", e);
        }
    }

    /**
     * Derive AES-256 key from passphrase using PBKDF2-HMAC-SHA256.
     * 65,536 iterations provides adequate brute-force resistance.
     */
    private SecretKey deriveKey(String passphrase, byte[] salt) throws Exception {
        KeySpec spec = new PBEKeySpec(
            passphrase.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH
        );
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    private String getEncryptionKeyOrThrow() {
        String key = System.getenv(KEY_ENV);
        if (key == null || key.isBlank()) {
            key = encryptionKey;
        }

        if (key == null) {
            key = "";
        }

        if (key.isBlank()) {
            if (environment != null && !java.util.Arrays.asList(environment.getActiveProfiles()).contains("dev")) {
                throw new IllegalStateException(KEY_ENV + " must be set outside the dev profile");
            }
            log.warn("{} not set; using empty passphrase for PII encryption in current runtime", KEY_ENV);
        }

        return key;
    }
}
