package com.enterprise.notification;

import com.enterprise.api.NotificationSSEController;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Notification createNotification(Long userId, String type, String title, String message, String link) {
        // Fetch user to get tenant ID
        Long tenantId = 1L; // default fallback
        if (userId != null) {
            AppUser user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getTenantId() != null) {
                tenantId = user.getTenantId();
            }
        }

        Notification notification = new Notification(userId, type, title, message, link);
        notification.setTenantId(tenantId);

        // Save first
        Notification saved = notificationRepository.save(notification);

        // ----- FIX: Recalculate unread count after saving -----
        long unreadCount = countUnread(userId);
        NotificationSSEController.broadcast(userId, unreadCount);

        return saved;
    }

    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
    }

    public List<Notification> getRecentUnreadNotifications(Long userId, int limit) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId)
                .stream()
                .limit(limit)
                .toList();
    }

    public long countUnread(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

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