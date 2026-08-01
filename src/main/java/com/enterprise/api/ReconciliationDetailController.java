package com.enterprise.api;

import com.enterprise.reconciliation.ReconciliationDetail;
import com.enterprise.reconciliation.ReconciliationRecord;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import com.enterprise.reconciliation.ReconciliationDetailRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationDetailController {

    private final ReconciliationRecordRepository recordRepository;
    private final ReconciliationDetailRepository detailRepository;

    public ReconciliationDetailController(ReconciliationRecordRepository recordRepository,
                                          ReconciliationDetailRepository detailRepository) {
        this.recordRepository = recordRepository;
        this.detailRepository = detailRepository;
    }

    // List all reconciliation records (matches your template)
    @GetMapping
    public String listRecords(Model model) {
        List<ReconciliationRecord> records = recordRepository.findTop10ByOrderByCreatedAtDesc();
        model.addAttribute("records", records);
        return "admin/reconciliation/list"; // your list template
    }

    // Detail view for a specific record (matches your detail template)
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ReconciliationRecord record = recordRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reconciliation record not found"));

        List<ReconciliationDetail> details = detailRepository.findByReconciliationRecordId(id);

        model.addAttribute("record", record);
        model.addAttribute("details", details);
        return "admin/reconciliation/detail"; // your detail template
    }
}