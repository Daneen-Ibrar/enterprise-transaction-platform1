package com.enterprise.api;

import com.enterprise.reliability.DlqEntry;
import com.enterprise.reliability.DlqEntryRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/reliability")
public class AdminReliabilityController {

    private final DlqEntryRepository dlqEntryRepository;

    public AdminReliabilityController(DlqEntryRepository dlqEntryRepository) {
        this.dlqEntryRepository = dlqEntryRepository;
    }

    @GetMapping("/dlq")
    @PreAuthorize("hasPermission(null, 'reliability:view')")
    public List<DlqEntry> getDlqEntries() {
        return dlqEntryRepository.findAll();
    }

    @PostMapping("/dlq/{id}/retry")
    @PreAuthorize("hasPermission(null, 'reliability:retry')")
    public String retryDlq(@PathVariable Long id) {
        DlqEntry entry = dlqEntryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("DLQ entry not found"));
        // Mark as RETRYING – manual retry will be handled by a background job (stub for now)
        entry.setStatus("RETRYING");
        dlqEntryRepository.save(entry);
        return "DLQ entry " + id + " marked for retry";
    }
}