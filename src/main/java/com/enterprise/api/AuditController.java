package com.enterprise.api;

import com.enterprise.audit.AuditEventDTO;
import com.enterprise.audit.AuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/events")
    @PreAuthorize("hasPermission(null, 'audit:view')")
    public List<AuditEventDTO> getEvents() {
        return auditService.getAllEvents().stream()
            .map(AuditEventDTO::new)
            .collect(Collectors.toList());
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