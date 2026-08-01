package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.job.Job;
import com.enterprise.service.BulkJobService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/bulk")
@PreAuthorize("hasRole('ADMIN')")
public class AdminBulkController {

    private final BulkJobService bulkJobService;
    private final UserRepository userRepository;

    public AdminBulkController(BulkJobService bulkJobService,
                               UserRepository userRepository) {
        this.bulkJobService = bulkJobService;
        this.userRepository = userRepository;
    }

    @PostMapping("/approve-invoices")
    public Job approveInvoices(@RequestBody List<Long> invoiceIds, Authentication authentication) {
        Long adminId = getUserId(authentication);
        return bulkJobService.createJob("BULK_APPROVE", invoiceIds, adminId);
    }

    @PostMapping("/reject-invoices")
    public Job rejectInvoices(@RequestBody List<Long> invoiceIds, Authentication authentication) {
        Long adminId = getUserId(authentication);
        return bulkJobService.createJob("BULK_REJECT", invoiceIds, adminId);
    }

    @PostMapping("/refund-transactions")
    public Job refundTransactions(@RequestBody List<Long> transactionIds, Authentication authentication) {
        Long adminId = getUserId(authentication);
        return bulkJobService.createJob("BULK_REFUND", transactionIds, adminId);
    }

    @GetMapping("/jobs/{jobId}")
    public Job getJobStatus(@PathVariable Long jobId) {
        return bulkJobService.getJob(jobId);
    }

    private Long getUserId(Authentication authentication) {
        String email = authentication.getName();
        AppUser user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }
}