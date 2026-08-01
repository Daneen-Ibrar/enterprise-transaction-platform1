package com.enterprise.job;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface JobRepository extends JpaRepository<Job, Long> {
    Page<Job> findByCreatedByOrderByCreatedAtDesc(Long userId, Pageable pageable);
}