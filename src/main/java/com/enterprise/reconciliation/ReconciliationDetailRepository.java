package com.enterprise.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ReconciliationDetailRepository extends JpaRepository<ReconciliationDetail, Long> {
    List<ReconciliationDetail> findByReconciliationRecordId(Long recordId);
    List<ReconciliationDetail> findByReconciliationRecordIdAndDiscrepancyType(Long recordId, String discrepancyType);
}