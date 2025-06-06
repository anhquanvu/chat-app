package com.revotech.chatapp.util;

import com.revotech.chatapp.model.dto.request.MarkMessageReadRequest;
import com.revotech.chatapp.service.MessageService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MessageReadDebouncer {

    private final Map<String, ScheduledFuture<?>> pendingReads = new ConcurrentHashMap<>();
    private final Set<String> processedMessages = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private final MessageService messageService;

    public MessageReadDebouncer(MessageService messageService) {
        this.messageService = messageService;

        // Cleanup processed messages every 5 minutes
        scheduler.scheduleAtFixedRate(this::cleanupProcessedMessages, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Debounced mark as read - prevents duplicate calls for same message
     */
    public void markMessageAsRead(String messageId, Long userId, Long conversationId) {
        String key = messageId + ":" + userId;

        // Skip if already processed recently
        if (processedMessages.contains(key)) {
            log.debug("Message {} already marked as read by user {}, skipping", messageId, userId);
            return;
        }

        // Cancel any pending read operation for this message+user
        ScheduledFuture<?> existingTask = pendingReads.get(key);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(false);
        }

        // Schedule new read operation with 300ms delay for debouncing
        ScheduledFuture<?> task = scheduler.schedule(() -> {
            try {
                MarkMessageReadRequest request = MarkMessageReadRequest.builder()
                        .messageId(messageId)
                        .conversationId(conversationId)
                        .build();

                messageService.markMessageAsRead(request, userId);
                processedMessages.add(key);
                pendingReads.remove(key);

                log.debug("Debounced read completed for message {} by user {}", messageId, userId);

            } catch (Exception e) {
                log.error("Error marking message {} as read: {}", messageId, e.getMessage());
                pendingReads.remove(key);
            }
        }, 300, TimeUnit.MILLISECONDS);

        pendingReads.put(key, task);
    }

    /**
     * Batch mark multiple messages as read (for conversation enter)
     */
    public void batchMarkMessagesAsRead(List<String> messageIds, Long userId, Long conversationId) {
        if (messageIds.isEmpty()) return;

        String batchKey = "batch:" + conversationId + ":" + userId;

        // Cancel any existing batch operation
        ScheduledFuture<?> existingBatch = pendingReads.get(batchKey);
        if (existingBatch != null && !existingBatch.isDone()) {
            existingBatch.cancel(false);
        }

        // Schedule batch operation
        ScheduledFuture<?> batchTask = scheduler.schedule(() -> {
            try {
                // Filter out already processed messages
                List<String> unprocessedMessages = messageIds.stream()
                        .filter(msgId -> !processedMessages.contains(msgId + ":" + userId))
                        .collect(Collectors.toList());

                if (!unprocessedMessages.isEmpty()) {
                    messageService.autoMarkMessagesAsRead(null, conversationId, userId);

                    // Mark as processed
                    unprocessedMessages.forEach(msgId ->
                            processedMessages.add(msgId + ":" + userId));

                    log.debug("Batch marked {} messages as read in conversation {} by user {}",
                            unprocessedMessages.size(), conversationId, userId);
                }

                pendingReads.remove(batchKey);

            } catch (Exception e) {
                log.error("Error in batch mark as read for conversation {}: {}", conversationId, e.getMessage());
                pendingReads.remove(batchKey);
            }
        }, 500, TimeUnit.MILLISECONDS);

        pendingReads.put(batchKey, batchTask);
    }

    /**
     * Cancel all pending operations for a user (on disconnect)
     */
    public void cancelUserOperations(Long userId) {
        String userPattern = ":" + userId;

        pendingReads.entrySet().removeIf(entry -> {
            if (entry.getKey().endsWith(userPattern)) {
                entry.getValue().cancel(false);
                return true;
            }
            return false;
        });

        // Also cleanup processed messages for this user
        processedMessages.removeIf(key -> key.endsWith(userPattern));

        log.debug("Cancelled all pending read operations for user {}", userId);
    }

    private void cleanupProcessedMessages() {
        // Keep only messages processed in last 10 minutes
        int sizeBefore = processedMessages.size();
        // Simple cleanup - in production might want timestamp-based cleanup
        if (sizeBefore > 1000) {
            processedMessages.clear();
            log.debug("Cleaned up processed messages cache, was {} entries", sizeBefore);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
