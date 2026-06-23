package com.enterprise.api;

import com.enterprise.notification.EmailLog;
import com.enterprise.notification.EmailLogRepository;
import com.enterprise.notification.EmailNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/emails")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmailController {

    private final EmailLogRepository emailLogRepository;
    private final EmailNotificationService emailNotificationService;

    public AdminEmailController(EmailLogRepository emailLogRepository,
                                EmailNotificationService emailNotificationService) {
        this.emailLogRepository = emailLogRepository;
        this.emailNotificationService = emailNotificationService;
    }

    @GetMapping
    public Page<EmailLog> getEmails(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return emailLogRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @PostMapping("/{id}/resend")
    public String resendEmail(@PathVariable Long id) {
        emailNotificationService.resendEmail(id);
        return "Email resend initiated.";
    }
}