package com.enterprise.reliability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DlqEntryRepository extends JpaRepository<DlqEntry, Long> {
    List<DlqEntry> findByStatus(String status);
    long countByStatus(String status);

    // For scheduled processing – find entries with retry count below max
    List<DlqEntry> findByStatusAndFailureCountLessThan(String status, int maxFailureCount);
}