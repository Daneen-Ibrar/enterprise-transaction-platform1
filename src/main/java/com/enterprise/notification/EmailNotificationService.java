package com.enterprise.notification;

import com.enterprise.tenant.TenantContext;
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
        // For plain text, we just pass the body as is
        sendHtmlEmailAsync(to, subject, body);
    }

    @Async
    @Transactional
    public void sendHtmlEmailAsync(String to, String subject, String htmlBody) {
        EmailLog logEntry = new EmailLog(to, subject, htmlBody);
        logEntry.setTenantId(TenantContext.getRequiredTenantId());
        emailLogRepository.save(logEntry);
        log.info("📧 Sending email to: {}", to);

        try {
            // Use the appropriate service (RealEmailService will send HTML)
            emailService.sendEmail(to, subject, htmlBody);
            logEntry.setStatus("SENT");
            logEntry.setSentAt(LocalDateTime.now());
        } catch (Exception e) {
            logEntry.setStatus("FAILED");
            logEntry.setErrorMessage(e.getMessage());
            log.error("❌ Email failed for {}: {}", to, e.getMessage());
        }
        emailLogRepository.save(logEntry);
    }

    @Transactional
    public void resendEmail(Long logId) {
        EmailLog logEntry = emailLogRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Email log not found"));
        sendHtmlEmailAsync(logEntry.getRecipient(), logEntry.getSubject(), logEntry.getBody());
    }
}