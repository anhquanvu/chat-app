package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.config.EncryptionConfig;
import com.revotech.chatapp.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EncryptionServiceImpl implements EncryptionService {

    private final EncryptionConfig.EncryptionService encryptionConfigService;

    @Value("${app.encryption.enabled:false}")
    private boolean encryptionEnabled;

    @Override
    public String encrypt(String plainText) {
        if (!encryptionEnabled || plainText == null) {
            return plainText;
        }

        try {
            return encryptionConfigService.encrypt(plainText);
        } catch (Exception e) {
            log.error("Failed to encrypt message", e);
            return plainText; // Fallback to plain text
        }
    }

    @Override
    public String decrypt(String encryptedText) {
        if (!encryptionEnabled || encryptedText == null) {
            return encryptedText;
        }

        try {
            return encryptionConfigService.decrypt(encryptedText);
        } catch (Exception e) {
            log.error("Failed to decrypt message", e);
            return encryptedText; // Fallback to encrypted text
        }
    }

    @Override
    public String generateRandomKey() {
        return encryptionConfigService.generateRandomKey();
    }

    @Override
    public boolean isEncryptionEnabled() {
        return encryptionEnabled;
    }
}