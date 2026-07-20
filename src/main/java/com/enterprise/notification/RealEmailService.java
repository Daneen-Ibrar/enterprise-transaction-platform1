package com.enterprise.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.email.mode", havingValue = "smtp")
public class RealEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(RealEmailService.class);
    private final JavaMailSender mailSender;

    public RealEmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("✅ Email sent to: {}", to);
        } catch (Exception e) {
            log.error("❌ Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }
}