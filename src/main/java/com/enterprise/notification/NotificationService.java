package com.enterprise.notification;

import com.enterprise.api.NotificationSSEController;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification createNotification(Long userId, String type, String title, String message, String link) {
        Notification notification = new Notification(userId, type, title, message, link);
        Notification saved = notificationRepository.save(notification);

        // Broadcast the new count via SSE
        long unreadCount = countUnread(userId);
        NotificationSSEController.broadcast(userId, unreadCount);

        return saved;
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    // New method: returns only unread, limited by count
    public List<Notification> getRecentUnreadNotifications(Long userId, int limit) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .limit(limit)
                .toList();
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    // Kept for backward compatibility, but you can remove if not used elsewhere
    @Deprecated
    public List<Notification> getRecentNotifications(Long userId, int limit) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(limit)
                .toList();
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsRead(userId);
    }
}