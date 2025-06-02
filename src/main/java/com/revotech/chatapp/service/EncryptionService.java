package com.revotech.chatapp.service;

public interface EncryptionService {
    String encrypt(String plainText);
    String decrypt(String encryptedText);
    String generateRandomKey();
    boolean isEncryptionEnabled();
}