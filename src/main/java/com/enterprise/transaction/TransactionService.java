package com.enterprise.transaction;

import com.enterprise.audit.AuditService;
import com.enterprise.events.TransactionSettledEvent;
import com.enterprise.ledger.LedgerService;
import com.enterprise.notification.NotificationService;
import com.enterprise.reliability.Reliable;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final long idempotencyTtl = 2592000; // 30 days

    public TransactionService(TransactionRepository transactionRepository,
                              LedgerService ledgerService,
                              AuditService auditService,
                              NotificationService notificationService,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              ApplicationEventPublisher eventPublisher) {
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Reliable
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        // 1. Check Redis cache
        String cacheKey = "idem:" + idempotencyKey;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                PaymentResponse cachedResponse = objectMapper.readValue(cached, PaymentResponse.class);
                log.info("Returning cached response for idempotency key {}", idempotencyKey);
                return cachedResponse;
            } catch (Exception e) {
                log.warn("Failed to deserialize cached response, falling back to DB", e);
            }
        }

        // 2. Check DB
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            PaymentResponse response = new PaymentResponse(tx.getId(), tx.getStatus().name(), "Duplicate request – original response returned");
            cacheResponse(cacheKey, response);
            return response;
        }

        // 3. Create new transaction
        Transaction transaction = new Transaction(
            request.getInvoiceId(),
            request.getCustomerId(),
            request.getMerchantId(),
            request.getAmount(),
            idempotencyKey
        );
        transaction = transactionRepository.save(transaction);

        // 4. Authorise
        transaction.transitionTo(TransactionStatus.AUTHORISED);
        transaction = transactionRepository.save(transaction);

        // 5. Settle
        transaction.transitionTo(TransactionStatus.SETTLED);
        transaction = transactionRepository.save(transaction);

        // 6. Ledger entries
        ledgerService.recordDebit(request.getCustomerId(), request.getAmount(), transaction.getId());
        ledgerService.recordCredit(request.getMerchantId(), request.getAmount(), transaction.getId());

        // 7. Audit with diff snapshots (before = null fields, after = actual transaction)
        Map<String, Object> afterMap = objectMapper.convertValue(transaction, Map.class);
        Map<String, Object> beforeMap = new HashMap<>();
        for (String key : afterMap.keySet()) {
            beforeMap.put(key, null);
        }

        auditService.recordEvent(
            "PAYMENT_SETTLED",
            request.getCustomerId(),
            Map.of(
                "transactionId", transaction.getId(),
                "amount", request.getAmount(),
                "invoiceId", request.getInvoiceId(),
                "merchantId", request.getMerchantId()
            ),
            "Transaction",
            transaction.getId(),
            beforeMap,
            afterMap
        );

        // 8. Notifications
        notificationService.createNotification(
            request.getCustomerId(),
            "PAYMENT_SENT",
            "Payment Sent",
            String.format("You paid %.2f to merchant %d (Invoice %d)", request.getAmount(), request.getMerchantId(), request.getInvoiceId()),
            "/transactions/" + transaction.getId()
        );
        notificationService.createNotification(
            request.getMerchantId(),
            "PAYMENT_RECEIVED",
            "Payment Received",
            String.format("Customer %d paid %.2f (Invoice %d)", request.getCustomerId(), request.getAmount(), request.getInvoiceId()),
            "/transactions/" + transaction.getId()
        );

        // 9. Publish event for webhooks
        eventPublisher.publishEvent(new TransactionSettledEvent(transaction));

        // 10. Build response
        PaymentResponse response = new PaymentResponse(
            transaction.getId(),
            transaction.getStatus().name(),
            "Payment settled successfully"
        );

        // 11. Cache response
        cacheResponse(cacheKey, response);

        return response;
    }

    private void cacheResponse(String key, PaymentResponse response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(idempotencyTtl));
        } catch (Exception e) {
            log.warn("Failed to cache idempotency response", e);
        }
    }

    public Optional<Transaction> findById(Long id) {
        return transactionRepository.findById(id);
    }
}