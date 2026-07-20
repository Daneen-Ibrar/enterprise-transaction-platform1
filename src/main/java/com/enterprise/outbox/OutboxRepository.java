package com.enterprise.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' AND o.nextAttemptAt <= CURRENT_TIMESTAMP ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEvents(Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent o SET o.status = 'PROCESSING', o.updatedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void markAsProcessing(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent o SET o.status = 'DELIVERED', o.completedAt = CURRENT_TIMESTAMP, o.updatedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void markAsDelivered(@Param("id") Long id);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent o SET o.status = 'FAILED', o.lastError = :error, o.updatedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void markAsFailed(@Param("id") Long id, @Param("error") String error);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEvent o SET o.retryCount = o.retryCount + 1, o.nextAttemptAt = :nextAttemptAt, o.updatedAt = CURRENT_TIMESTAMP WHERE o.id = :id")
    void incrementRetry(@Param("id") Long id, @Param("nextAttemptAt") LocalDateTime nextAttemptAt);

    @Query("SELECT COUNT(o) FROM OutboxEvent o WHERE o.status = 'PENDING' AND o.nextAttemptAt <= CURRENT_TIMESTAMP")
    long countPendingEvents();
}