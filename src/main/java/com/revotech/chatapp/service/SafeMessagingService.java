package com.revotech.chatapp.service;

public interface SafeMessagingService {
    String sanitizeMessage(String content);
    boolean isMessageSafe(String content);
    String filterProfanity(String content);
    boolean containsSpam(String content);
    boolean containsMaliciousContent(String content);
}