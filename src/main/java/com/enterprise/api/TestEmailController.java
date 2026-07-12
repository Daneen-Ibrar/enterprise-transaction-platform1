package com.enterprise.api;

import com.enterprise.notification.EmailNotificationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/test/email")
public class TestEmailController {

    private final EmailNotificationService emailNotificationService;

    public TestEmailController(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    @PostMapping
    public String sendTestEmail(@RequestParam String to,
                                @RequestParam(defaultValue = "Test Email") String subject,
                                @RequestParam(defaultValue = "This is a test email from the Enterprise Transaction Platform.") String body) {
        emailNotificationService.sendEmailAsync(to, subject, body);
        return "Test email sent to " + to;
    }
}