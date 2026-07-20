package com.enterprise.reconciliation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    List<ReconciliationRecord> findTop10ByOrderByCreatedAtDesc();
}