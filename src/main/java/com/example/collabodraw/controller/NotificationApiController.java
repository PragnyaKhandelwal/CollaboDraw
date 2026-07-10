package com.example.collabodraw.controller;

import com.example.collabodraw.model.entity.Notification;
import com.example.collabodraw.model.entity.User;
import com.example.collabodraw.service.NotificationService;
import com.example.collabodraw.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lightweight REST API backing the notification bell shown in the header of every page.
 * The bell previously had either no handler at all, or a handler that only ever showed a
 * hardcoded "No new notifications" string - this is real data from NotificationRepository.
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationApiController {

    private final NotificationService notificationService;
    private final UserService userService;

    public NotificationApiController(NotificationService notificationService, UserService userService) {
        this.notificationService = notificationService;
        this.userService = userService;
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> recent(Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            List<Notification> notifications = notificationService.recentForUser(currentUser.getUserId());
            List<Map<String, Object>> items = notifications.stream().map(n -> {
                Map<String, Object> m = new java.util.LinkedHashMap<>();
                m.put("id", n.getId());
                m.put("type", n.getType());
                m.put("title", n.getTitle());
                m.put("message", n.getMessage());
                m.put("read", n.isRead());
                m.put("linkUrl", n.getLinkUrl());
                m.put("createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : null);
                return m;
            }).collect(Collectors.toList());

            long unread = notifications.stream().filter(n -> !n.isRead()).count();

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "items", items,
                    "unreadCount", unread
            ));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id, Authentication authentication) {
        try {
            User currentUser = requireCurrentUser(authentication);
            notificationService.markRead(currentUser.getUserId(), id);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (AccessDeniedException ex) {
            return ResponseEntity.status(403).body(Map.of("success", false, "message", ex.getMessage()));
        }
    }

    private User requireCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("User must be authenticated");
        }
        User currentUser = userService.findByUsername(authentication.getName());
        if (currentUser == null) {
            throw new AccessDeniedException("User not found");
        }
        return currentUser;
    }
}
