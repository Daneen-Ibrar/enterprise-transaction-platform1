package com.enterprise.reliability;

import com.enterprise.audit.AuditService;
import com.enterprise.reconciliation.ReconciliationRecord;
import com.enterprise.reconciliation.ReconciliationRecordRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class ReliabilityDashboardService {

    private final DlqEntryRepository dlqEntryRepository;
    private final CircuitBreakerStateRepository circuitBreakerStateRepository;
    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final AuditService auditService;

    public ReliabilityDashboardService(DlqEntryRepository dlqEntryRepository,
                                       CircuitBreakerStateRepository circuitBreakerStateRepository,
                                       ReconciliationRecordRepository reconciliationRecordRepository,
                                       AuditService auditService) {
        this.dlqEntryRepository = dlqEntryRepository;
        this.circuitBreakerStateRepository = circuitBreakerStateRepository;
        this.reconciliationRecordRepository = reconciliationRecordRepository;
        this.auditService = auditService;
    }

    public Map<String, Long> getDlqSummary() {
        return Map.of(
            "pending", dlqEntryRepository.countByStatus("PENDING"),
            "retrying", dlqEntryRepository.countByStatus("RETRYING"),
            "resolved", dlqEntryRepository.countByStatus("RESOLVED"),
            "failed", dlqEntryRepository.countByStatus("FAILED")
        );
    }

    public List<CircuitBreakerState> getCircuitBreakerStates() {
        return circuitBreakerStateRepository.findAll();
    }

    public ReconciliationRecord getLastReconciliation() {
        return reconciliationRecordRepository.findTopByOrderByCreatedAtDesc().orElse(null);
    }

    public boolean isAuditChainValid() {
        try {
            return auditService.verifyChain();
        } catch (Exception e) {
            return false;
        }
    }

    public long getTotalOpenCircuits() {
        return circuitBreakerStateRepository.findAll().stream()
                .filter(s -> "OPEN".equals(s.getState()))
                .count();
    }
}