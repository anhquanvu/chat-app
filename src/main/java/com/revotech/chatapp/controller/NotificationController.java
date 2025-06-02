package com.revotech.chatapp.controller;

import com.revotech.chatapp.model.dto.NotificationDTO;
import com.revotech.chatapp.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    // This would require NotificationService implementation
    // private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<Page<NotificationDTO>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        // Placeholder implementation
        // Page<NotificationDTO> notifications = notificationService.getUserNotifications(currentUser.getId(), page, size);
        // return ResponseEntity.ok(notifications);

        return ResponseEntity.ok().build();
    }

    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        // Placeholder implementation
        // Long count = notificationService.getUnreadNotificationCount(currentUser.getId());
        // return ResponseEntity.ok(Map.of("count", count));

        return ResponseEntity.ok(Map.of("count", 0L));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal UserPrincipal currentUser) {

        // Placeholder implementation
        // notificationService.markAsRead(notificationId, currentUser.getId());

        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser) {

        // Placeholder implementation
        // notificationService.markAllAsRead(currentUser.getId());

        return ResponseEntity.ok().build();
    }
}