package com.enterprise.api;

import com.enterprise.audit.AuditDiffService;
import com.enterprise.audit.AuditEvent;
import com.enterprise.audit.AuditRepository;
import com.enterprise.audit.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/audit")
@PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
public class AuditViewController {

    private final AuditRepository auditRepository;
    private final AuditService auditService;
    private final AuditDiffService auditDiffService;

    public AuditViewController(AuditRepository auditRepository,
                               AuditService auditService,
                               AuditDiffService auditDiffService) {
        this.auditRepository = auditRepository;
        this.auditService = auditService;
        this.auditDiffService = auditDiffService;
    }

    @GetMapping("/events")
    public String events(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
        Page<AuditEvent> events = auditRepository.findAll(
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        model.addAttribute("events", events);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", events.getTotalPages());
        return "audit/events";
    }

    @GetMapping("/verify")
    @ResponseBody
    public Map<String, Object> verifyChain() {
        boolean valid = auditService.verifyChain();
        return Map.of(
            "valid", valid,
            "status", valid ? "INTEGRITY_VALID" : "INTEGRITY_BROKEN"
        );
    }

    // ===== DIFF VIEW =====
    @GetMapping("/diff/{eventId}")
    public String showDiff(@PathVariable Long eventId, Model model) {
        AuditEvent event = auditService.getEventById(eventId)
                .orElseThrow(() -> new RuntimeException("Audit event not found"));

        List<AuditDiffService.DiffEntry> diffs = new ArrayList<>();
        if (event.getPreviousState() != null && event.getCurrentState() != null) {
            try {
                diffs = auditDiffService.diff(event.getPreviousState(), event.getCurrentState());
            } catch (Exception e) {
                model.addAttribute("error", "Could not compute diff: " + e.getMessage());
            }
        }

        model.addAttribute("event", event);
        model.addAttribute("diffs", diffs);
        return "audit/diff";  // This will render audit/diff.html
    }
}