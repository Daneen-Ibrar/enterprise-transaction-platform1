package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequestMapping("/api/notifications")
public class NotificationSSEController {

    private static final Logger log = LoggerFactory.getLogger(NotificationSSEController.class);
    private static final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public NotificationSSEController(NotificationService notificationService,
                                     UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping("/stream")
    public SseEmitter stream(Authentication authentication) {
        AppUser user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Long userId = user.getId();

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> {
            log.info("SSE connection completed for user {}", userId);
            emitters.remove(userId);
        });
        emitter.onTimeout(() -> {
            log.info("SSE connection timed out for user {}", userId);
            emitters.remove(userId);
        });
        emitter.onError((e) -> {
            log.error("SSE error for user {}: {}", userId, e.getMessage());
            emitters.remove(userId);
        });

        // Send initial count
        try {
            long count = notificationService.countUnread(userId);
            emitter.send(SseEmitter.event()
                    .name("notificationCount")
                    .data(String.valueOf(count)));
        } catch (IOException e) {
            emitters.remove(userId);
        }

        return emitter;
    }

    // Called from NotificationService to broadcast updates
    public static void broadcast(Long userId, long count) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notificationCount")
                        .data(String.valueOf(count)));
                log.debug("Broadcast notification count {} to user {}", count, userId);
            } catch (IOException e) {
                emitters.remove(userId);
                log.warn("Removed stale emitter for user {}", userId);
            }
        }
    }
}