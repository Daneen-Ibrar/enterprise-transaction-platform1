package com.enterprise.reliability;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DlqEntryRepository extends JpaRepository<DlqEntry, Long> {
    List<DlqEntry> findByStatus(String status);
}