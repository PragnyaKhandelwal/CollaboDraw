package com.example.collabodraw.service;

import com.example.collabodraw.model.entity.Notification;
import com.example.collabodraw.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> recentForUser(Long userId) {
        return notificationRepository.findRecentByUser(userId, 20);
    }

    public void markRead(Long userId, Long notificationId) {
        notificationRepository.markRead(notificationId, userId);
    }

    public void create(Long userId, String type, String title, String message) {
        notificationRepository.create(userId, type, title, message, null, null, LocalDateTime.now());
    }

    // Overload with link and board context
    public void create(Long userId, String type, String title, String message, String linkUrl, Long boardId) {
        notificationRepository.create(userId, type, title, message, linkUrl, boardId, LocalDateTime.now());
    }

    // Full overload allowing custom timestamp
    public void create(Long userId, String type, String title, String message, String linkUrl, Long boardId, LocalDateTime createdAt) {
        notificationRepository.create(userId, type, title, message, linkUrl, boardId, createdAt);
    }
}
