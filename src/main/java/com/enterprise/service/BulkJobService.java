package com.enterprise.service;

import com.enterprise.invoice.InvoiceService;
import com.enterprise.job.Job;
import com.enterprise.job.JobRepository;
import com.enterprise.refund.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BulkJobService {

    private static final Logger log = LoggerFactory.getLogger(BulkJobService.class);
    private final JobRepository jobRepository;
    private final InvoiceService invoiceService;
    private final RefundService refundService;

    public BulkJobService(JobRepository jobRepository,
                          InvoiceService invoiceService,
                          RefundService refundService) {
        this.jobRepository = jobRepository;
        this.invoiceService = invoiceService;
        this.refundService = refundService;
    }

    public Job createJob(String type, List<Long> ids, Long adminId) {
        Job job = new Job();
        job.setType(type);
        job.setStatus("PENDING");
        job.setTotalItems(ids.size());
        job.setCreatedBy(adminId);
        job = jobRepository.save(job);

        // Process asynchronously
        processJob(job.getId(), ids, adminId);
        return job;
    }

    @Async
    @Transactional
    public void processJob(Long jobId, List<Long> ids, Long adminId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus("RUNNING");
        jobRepository.save(job);

        int success = 0;
        int failure = 0;
        StringBuilder errors = new StringBuilder();

        try {
            for (int i = 0; i < ids.size(); i++) {
                Long id = ids.get(i);
                try {
                    switch (job.getType()) {
                        case "BULK_APPROVE" -> invoiceService.approveInvoice(id, adminId);
                        case "BULK_REJECT" -> invoiceService.rejectInvoice(id, adminId);
                        case "BULK_REFUND" -> refundService.processRefund(id, adminId, "Bulk refund");
                        default -> throw new IllegalStateException("Unknown job type: " + job.getType());
                    }
                    success++;
                } catch (Exception e) {
                    failure++;
                    errors.append("ID ").append(id).append(": ").append(e.getMessage()).append("\n");
                    log.error("Bulk job {} failed for ID {}", jobId, id, e);
                }
                job.setProcessedItems(i + 1);
                job.setSuccessCount(success);
                job.setFailureCount(failure);
                jobRepository.save(job);
            }

            job.setStatus("COMPLETED");
            job.setResultSummary(String.format("Success: %d, Failed: %d", success, failure));
            if (errors.length() > 0) {
                job.setErrorDetails(errors.toString());
            }
        } catch (Exception e) {
            job.setStatus("FAILED");
            job.setErrorDetails(e.getMessage());
            log.error("Bulk job {} failed", jobId, e);
        } finally {
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
        }
    }

    public Job getJob(Long jobId) {
        return jobRepository.findById(jobId).orElseThrow();
    }

    public Page<Job> getUserJobs(Long userId, Pageable pageable) {
        return jobRepository.findByCreatedByOrderByCreatedAtDesc(userId, pageable);
    }
}