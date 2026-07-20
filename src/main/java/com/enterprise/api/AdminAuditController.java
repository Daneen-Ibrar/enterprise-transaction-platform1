package com.enterprise.api;

import com.enterprise.audit.AuditDiffService;
import com.enterprise.audit.AuditEvent;
import com.enterprise.audit.AuditRepository;
import com.enterprise.audit.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuditController {

    private static final Logger log = LoggerFactory.getLogger(AdminAuditController.class);

    private final AuditService auditService;
    private final AuditDiffService auditDiffService;
    private final AuditRepository auditRepository;

    public AdminAuditController(AuditService auditService,
                                AuditDiffService auditDiffService,
                                AuditRepository auditRepository) {
        this.auditService = auditService;
        this.auditDiffService = auditDiffService;
        this.auditRepository = auditRepository;
    }

    @GetMapping
    public String listAuditEvents(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  Model model) {
        Page<AuditEvent> events = auditRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        model.addAttribute("events", events);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", events.getTotalPages());
        return "admin/audit";
    }

    @GetMapping("/diff/{eventId}")
    public String showDiff(@PathVariable Long eventId, Model model) {
        log.info("=== Loading diff for event ID: {} ===", eventId);
        try {
            AuditEvent event = auditService.getEventById(eventId)
                    .orElseThrow(() -> new RuntimeException("Audit event not found"));
            log.info("Event found: type={}, entity={}, id={}", event.getEventType(), event.getEntityType(), event.getEntityId());

            List<AuditDiffService.DiffEntry> diffs = new ArrayList<>();
            String message = null;

            if (event.getPreviousState() != null && event.getCurrentState() != null) {
                try {
                    diffs = auditDiffService.diff(event.getPreviousState(), event.getCurrentState());
                    log.info("Diff computed: {} changes", diffs.size());
                    if (diffs.isEmpty()) {
                        message = "No changes detected.";
                    }
                } catch (Exception e) {
                    log.error("Diff computation failed", e);
                    message = "Could not compute diff: " + e.getMessage();
                }
            } else {
                log.info("Missing snapshot data for event {}", eventId);
                message = "No snapshot data available for this event.";
            }

            model.addAttribute("event", event);
            model.addAttribute("diffs", diffs);
            model.addAttribute("message", message);
            return "admin/audit-diff";
        } catch (Exception e) {
            log.error("Error loading diff for event {}", eventId, e);
            model.addAttribute("error", "Failed to load diff: " + e.getMessage());
            return "admin/audit-diff";
        }
    }
}