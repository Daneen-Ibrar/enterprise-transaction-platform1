package com.enterprise.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final EmailService emailService;
    private final EmailLogRepository emailLogRepository;

    public EmailNotificationService(EmailService emailService,
                                    EmailLogRepository emailLogRepository) {
        this.emailService = emailService;
        this.emailLogRepository = emailLogRepository;
    }

    @Async
    @Transactional
    public void sendEmailAsync(String to, String subject, String body) {
        // Save log entry first
        EmailLog logEntry = new EmailLog(to, subject, body);
        emailLogRepository.save(logEntry);
        log.info("📧 sendEmailAsync called for: {}", to);

        try {
            emailService.sendEmail(to, subject, body);
            logEntry.setStatus("SENT");
            logEntry.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            logEntry.setStatus("FAILED");
            logEntry.setErrorMessage(e.getMessage());
            log.error("Email failed for {}: {}", to, e.getMessage());
        }
        emailLogRepository.save(logEntry);
    }

    @Transactional
    public void resendEmail(Long logId) {
        EmailLog logEntry = emailLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Email log not found"));
        sendEmailAsync(logEntry.getRecipient(), logEntry.getSubject(), logEntry.getBody());
    }
}