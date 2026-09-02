package com.enterprise.subscription;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRetryRepository extends JpaRepository<PaymentRetry, Long> {

    List<PaymentRetry> findByStatusAndNextAttemptAtBefore(PaymentRetry.RetryStatus status, LocalDateTime date);

    List<PaymentRetry> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
}