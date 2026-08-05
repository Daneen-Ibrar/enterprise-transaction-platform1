package com.enterprise.api;

import com.enterprise.audit.AuditDiffService;
import com.enterprise.audit.AuditEvent;
import com.enterprise.audit.AuditEventSpecifications;
import com.enterprise.audit.AuditRepository;
import com.enterprise.audit.AuditService;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditController.class);

    private final AuditService auditService;
    private final AuditRepository auditRepository;
    private final AuditDiffService auditDiffService;

    public AdminAuditController(AuditService auditService,
                                AuditRepository auditRepository,
                                AuditDiffService auditDiffService) {
        this.auditService = auditService;
        this.auditRepository = auditRepository;
        this.auditDiffService = auditDiffService;
    }

    @GetMapping
    public String listAuditEvents(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Long tenantId = TenantContext.getTenantId();

        Specification<AuditEvent> spec = Specification
                .where(AuditEventSpecifications.hasUserId(userId))
                .and(AuditEventSpecifications.hasEventType(eventType))
                .and(AuditEventSpecifications.hasEntityType(entityType))
                .and(AuditEventSpecifications.createdBetween(startDate, endDate))
                .and(AuditEventSpecifications.hasTenantId(tenantId));

        Page<AuditEvent> events = auditRepository.findAll(
                spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        boolean chainValid = auditService.verifyChain();

        List<String> eventTypes = auditRepository.findDistinctEventTypesByTenantId(tenantId);

        model.addAttribute("events", events);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", events.getTotalPages());
        model.addAttribute("chainValid", chainValid);
        model.addAttribute("eventTypes", eventTypes);
        model.addAttribute("selectedUserId", userId);
        model.addAttribute("selectedEventType", eventType);
        model.addAttribute("selectedEntityType", entityType);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "admin/audit/list";
    }

    @GetMapping("/verify")
    @ResponseBody
    public Map<String, Object> verifyChainJson() {
        boolean valid = auditService.verifyChain();
        return Map.of(
                "valid", valid,
                "status", valid ? "INTEGRITY_VALID" : "INTEGRITY_BROKEN",
                "timestamp", java.time.Instant.now().toString()
        );
    }

    @GetMapping("/verify-fragment")
    public String verifyChainFragment(Model model) {
        boolean valid = auditService.verifyChain();
        model.addAttribute("valid", valid);
        model.addAttribute("status", valid ? "INTEGRITY_VALID" : "INTEGRITY_BROKEN");
        model.addAttribute("timestamp", java.time.Instant.now().toString());
        return "admin/audit/verify-fragment";
    }

    // ===== FIXED: DIFF VIEW =====
    @GetMapping("/diff/{eventId}")
    public String showDiff(@PathVariable Long eventId, Model model) {
        AuditEvent event = auditService.getEventById(eventId)
                .orElseThrow(() -> new RuntimeException("Audit event not found"));

        List<AuditDiffService.DiffEntry> diffs = new ArrayList<>();
        String errorMessage = null;

        if (event.getPreviousState() != null && event.getCurrentState() != null) {
            try {
                diffs = auditDiffService.diff(event.getPreviousState(), event.getCurrentState());
            } catch (Exception e) {
                log.error("Failed to compute diff for event {}", eventId, e);
                errorMessage = "Could not compute diff: " + e.getMessage();
            }
        }

        model.addAttribute("event", event);
        model.addAttribute("diffs", diffs != null ? diffs : new ArrayList<>());
        model.addAttribute("error", errorMessage);  // This matches the template

        return "admin/audit/diff";
    }
}