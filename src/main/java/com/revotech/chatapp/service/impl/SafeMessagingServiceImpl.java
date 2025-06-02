package com.revotech.chatapp.service.impl;

import com.revotech.chatapp.service.SafeMessagingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SafeMessagingServiceImpl implements SafeMessagingService {

    private static final List<String> PROFANITY_WORDS = Arrays.asList(
            // Add your profanity filter words here
            "spam", "badword1", "badword2"
    );

    private static final List<Pattern> MALICIOUS_PATTERNS = Arrays.asList(
            Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE),
            Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onload=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("onerror=", Pattern.CASE_INSENSITIVE)
    );

    @Override
    public String sanitizeMessage(String content) {
        if (content == null) {
            return null;
        }

        // Remove HTML tags
        content = content.replaceAll("<[^>]*>", "");

        // Remove potentially dangerous characters
        content = content.replaceAll("[<>\"'&]", "");

        // Limit length
        if (content.length() > 4000) {
            content = content.substring(0, 4000);
        }

        return content.trim();
    }

    @Override
    public boolean isMessageSafe(String content) {
        if (content == null || content.trim().isEmpty()) {
            return true;
        }

        return !containsMaliciousContent(content) && !containsSpam(content);
    }

    @Override
    public String filterProfanity(String content) {
        if (content == null) {
            return null;
        }

        String filtered = content;
        for (String word : PROFANITY_WORDS) {
            filtered = filtered.replaceAll("(?i)" + Pattern.quote(word), "*".repeat(word.length()));
        }

        return filtered;
    }

    @Override
    public boolean containsSpam(String content) {
        if (content == null) {
            return false;
        }

        String lowerContent = content.toLowerCase();

        // Check for excessive capitalization
        long upperCaseCount = content.chars().filter(Character::isUpperCase).count();
        if (upperCaseCount > content.length() * 0.7) {
            return true;
        }

        // Check for excessive repetition
        if (lowerContent.matches(".*(.{3,})\\1{3,}.*")) {
            return true;
        }

        // Check for spam keywords
        List<String> spamKeywords = Arrays.asList("buy now", "click here", "free money", "guaranteed");
        return spamKeywords.stream().anyMatch(lowerContent::contains);
    }

    @Override
    public boolean containsMaliciousContent(String content) {
        if (content == null) {
            return false;
        }

        return MALICIOUS_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(content).find());
    }
}