package com.revotech.chatapp.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Configuration
@Slf4j
public class EncryptionConfig {

    @Value("${app.encryption.secret-key:}")
    private String encryptionKey;

    @Value("${app.encryption.enabled:false}")
    private boolean encryptionEnabled;

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    @Bean
    public SecretKey encryptionSecretKey() {
        if (encryptionKey == null || encryptionKey.trim().isEmpty()) {
            // Generate a new key if none provided
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
                keyGenerator.init(256);
                SecretKey secretKey = keyGenerator.generateKey();

                String encodedKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
                log.warn("No encryption key provided. Generated new key: {}", encodedKey);
                log.warn("Please save this key and add it to your configuration!");

                return secretKey;
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Failed to generate encryption key", e);
            }
        } else {
            // Use provided key
            byte[] decodedKey = Base64.getDecoder().decode(encryptionKey);
            log.info("Using provided encryption key for message encryption");
            return new SecretKeySpec(decodedKey, ALGORITHM);
        }
    }

    @Bean
    public EncryptionService encryptionService(SecretKey secretKey) {
        return new EncryptionService(secretKey, encryptionEnabled);
    }

    // Inner class for encryption service
    public static class EncryptionService {
        private final SecretKey secretKey;
        private final boolean enabled;

        public EncryptionService(SecretKey secretKey, boolean enabled) {
            this.secretKey = secretKey;
            this.enabled = enabled;
        }

        public String encrypt(String plainText) {
            if (!enabled || plainText == null) {
                return plainText;
            }

            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.ENCRYPT_MODE, secretKey);
                byte[] encrypted = cipher.doFinal(plainText.getBytes());
                return Base64.getEncoder().encodeToString(encrypted);
            } catch (Exception e) {
                log.error("Failed to encrypt text", e);
                throw new RuntimeException("Encryption failed", e);
            }
        }

        public String decrypt(String encryptedText) {
            if (!enabled || encryptedText == null) {
                return encryptedText;
            }

            try {
                Cipher cipher = Cipher.getInstance(TRANSFORMATION);
                cipher.init(Cipher.DECRYPT_MODE, secretKey);
                byte[] decoded = Base64.getDecoder().decode(encryptedText);
                byte[] decrypted = cipher.doFinal(decoded);
                return new String(decrypted);
            } catch (Exception e) {
                log.error("Failed to decrypt text", e);
                throw new RuntimeException("Decryption failed", e);
            }
        }

        public String generateRandomKey() {
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
                keyGenerator.init(256, new SecureRandom());
                SecretKey key = keyGenerator.generateKey();
                return Base64.getEncoder().encodeToString(key.getEncoded());
            } catch (NoSuchAlgorithmException e) {
                throw new RuntimeException("Failed to generate random key", e);
            }
        }

        public boolean isEnabled() {
            return enabled;
        }
    }
}