package com.revotech.chatapp.task;

import com.revotech.chatapp.service.UserSessionService;
import com.revotech.chatapp.service.impl.UserSessionServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class SessionCleanupTask {

    private final UserSessionService userSessionService;

    /**
     * Cleanup expired sessions every 5 minutes
     * This helps maintain accurate online/offline status
     */
    @Scheduled(fixedRate = 300000) // 5 minutes = 300,000 milliseconds
    public void cleanupExpiredSessions() {
        try {
            log.debug("Starting session cleanup task...");

            if (userSessionService instanceof UserSessionServiceImpl) {
                ((UserSessionServiceImpl) userSessionService).cleanupExpiredSessions();
                log.debug("Session cleanup completed successfully");
            } else {
                log.warn("UserSessionService is not of type UserSessionServiceImpl, skipping cleanup");
            }

        } catch (Exception e) {
            log.error("Error during session cleanup", e);
        }
    }

    /**
     * Cleanup orphaned online statuses every 10 minutes
     * This is a more thorough cleanup that checks database consistency
     */
    @Scheduled(fixedRate = 600000) // 10 minutes = 600,000 milliseconds
    public void cleanupOrphanedStatuses() {
        try {
            log.debug("Starting orphaned status cleanup...");

            if (userSessionService instanceof UserSessionServiceImpl) {
                long onlineCount = userSessionService.getOnlineUserCount();
                log.info("Current online users: {}", onlineCount);
            }

        } catch (Exception e) {
            log.error("Error during orphaned status cleanup", e);
        }
    }
}