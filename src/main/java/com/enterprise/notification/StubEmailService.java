package com.enterprise.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StubEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(StubEmailService.class);

    @Override
    public void sendEmail(String to, String subject, String body) {
        log.info("📧 [STUB] Email would be sent to: {}", to);
        log.info("   Subject: {}", subject);
        log.info("   Body: \n{}", body);
    }
}