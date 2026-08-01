package com.enterprise.api;

import com.enterprise.audit.AuditEvent;
import com.enterprise.audit.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/events")
    @PreAuthorize("hasPermission(null, 'audit:view')")
    public List<AuditEvent> getEvents() {
        return auditService.getAllEvents();
    }

    @GetMapping("/verify")
    @PreAuthorize("hasPermission(null, 'audit:verify')")
    public Map<String, Object> verifyChain() {
        boolean valid = auditService.verifyChain();
        return Map.of(
            "valid", valid,
            "status", valid ? "INTEGRITY_VALID" : "INTEGRITY_BROKEN"
        );
    }
}