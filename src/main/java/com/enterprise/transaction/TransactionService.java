package com.enterprise.transaction;

import com.enterprise.audit.AuditService;
import com.enterprise.currency.ExchangeRateService;
import com.enterprise.events.TransactionSettledEvent;
import com.enterprise.exception.DuplicateRequestConflictException;
import com.enterprise.idempotency.IdempotencyKey;
import com.enterprise.idempotency.IdempotencyKeyRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.ledger.LedgerService;
import com.enterprise.notification.NotificationService;
import com.enterprise.reliability.Reliable;
import com.enterprise.simulator.SimulatorService;
import com.enterprise.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final InvoiceService invoiceService;
    private final SimulatorService simulatorService;
    private final ExchangeRateService exchangeRateService;
    private final long idempotencyTtl = 2592000; // 30 days

    public TransactionService(TransactionRepository transactionRepository,
                              LedgerService ledgerService,
                              AuditService auditService,
                              NotificationService notificationService,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper,
                              ApplicationEventPublisher eventPublisher,
                              IdempotencyKeyRepository idempotencyKeyRepository,
                              InvoiceService invoiceService,
                              SimulatorService simulatorService,
                              ExchangeRateService exchangeRateService) {
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.invoiceService = invoiceService;
        this.simulatorService = simulatorService;
        this.exchangeRateService = exchangeRateService;
    }

    private String computeFingerprint(PaymentRequest request) {
        String raw = request.getInvoiceId() + "|" +
                     request.getCustomerId() + "|" +
                     request.getMerchantId() + "|" +
                     request.getAmount().toPlainString() + "|" +
                     request.getCurrency();
        return DigestUtils.sha256Hex(raw);
    }

    private void recordTransition(Transaction transaction, TransactionStatus fromStatus, TransactionStatus toStatus) {
        auditService.recordEvent(
            "TRANSACTION_" + toStatus.name(),
            transaction.getCustomerId(),
            Map.of(
                "transactionId", transaction.getId(),
                "invoiceId", transaction.getInvoiceId(),
                "amount", transaction.getAmount(),
                "currency", transaction.getCurrency(),
                "fromStatus", fromStatus.name(),
                "toStatus", toStatus.name()
            ),
            "Transaction",
            transaction.getId(),
            Map.of("status", fromStatus.name()),
            Map.of("status", toStatus.name())
        );
    }

    @Reliable
    @Transactional
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        // ===== SIMULATOR CHECK =====
        SimulatorService.SimulatorResult simResult = simulatorService.simulate(request.getDescription());
        if (simResult != null) {
            if ("DUPLICATE".equals(simResult.status())) {
                return new PaymentResponse(999L, "SETTLED", simResult.message());
            } else if ("FAILED".equals(simResult.status()) || "DECLINED".equals(simResult.status())) {
                throw new RuntimeException(simResult.message());
            } else {
                return new PaymentResponse(888L, simResult.status(), simResult.message());
            }
        }

        // 1. Check Redis cache
        String cacheKey = "idem:" + idempotencyKey;
        String cached = null;
        try {
            cached = redisTemplate.opsForValue().get(cacheKey);
        } catch (Exception e) {
            log.warn("Redis unavailable, skipping cache", e);
        }
        if (cached != null) {
            try {
                PaymentResponse cachedResponse = objectMapper.readValue(cached, PaymentResponse.class);
                log.info("Returning cached response for idempotency key {}", idempotencyKey);
                return cachedResponse;
            } catch (Exception e) {
                log.warn("Failed to deserialize cached response, falling back to DB", e);
            }
        }

        // 2. Check DB for existing idempotency key
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Optional<IdempotencyKey> idemKeyOpt = idempotencyKeyRepository.findById(idempotencyKey);
            if (idemKeyOpt.isPresent()) {
                String storedFingerprint = idemKeyOpt.get().getRequestFingerprint();
                String currentFingerprint = computeFingerprint(request);
                if (!currentFingerprint.equals(storedFingerprint)) {
                    throw new DuplicateRequestConflictException("Idempotency key used with different request parameters");
                }
            }
            Transaction tx = existing.get();
            PaymentResponse response = new PaymentResponse(tx.getId(), tx.getStatus().name(), "Duplicate request – original response returned");
            cacheResponse(cacheKey, response);
            return response;
        }

        // 3. Validate invoice exists
        Invoice invoice = invoiceService.findById(request.getInvoiceId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));
        if (!"APPROVED".equals(invoice.getStatus())) {
            throw new IllegalStateException("Invoice is not approved");
        }

        // ===== MULTI-CURRENCY CONVERSION =====
        BigDecimal finalAmount = request.getAmount();
        String finalCurrency = request.getCurrency();

        // If payment currency differs from invoice currency, convert the amount
        if (!request.getCurrency().equals(invoice.getCurrency())) {
            try {
                finalAmount = exchangeRateService.convert(request.getAmount(), request.getCurrency(), invoice.getCurrency());
                finalCurrency = invoice.getCurrency();
                log.info("Converted payment amount from {} {} to {} {} using exchange rate",
                        request.getAmount(), request.getCurrency(), finalAmount, finalCurrency);
            } catch (Exception e) {
                log.error("Currency conversion failed for {} -> {}: {}",
                        request.getCurrency(), invoice.getCurrency(), e.getMessage());
                throw new IllegalArgumentException("Currency conversion failed: " + e.getMessage());
            }
        }

        // Also validate that the converted amount matches the invoice amount (within tolerance)
        if (finalAmount.compareTo(invoice.getAmount()) != 0) {
            throw new IllegalArgumentException(
                String.format("Payment amount (%s %s) does not match invoice amount (%s %s) after conversion",
                    finalAmount, finalCurrency, invoice.getAmount(), invoice.getCurrency())
            );
        }

        // 4. Create and save transaction (with converted amount)
        Transaction transaction = new Transaction(
            request.getInvoiceId(),
            request.getCustomerId(),
            request.getMerchantId(),
            finalAmount,
            idempotencyKey,
            finalCurrency  // Store in invoice currency
        );
        transaction.setTenantId(TenantContext.getRequiredTenantId());

        Transaction savedTransaction;
        try {
            savedTransaction = transactionRepository.save(transaction);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate idempotency key detected (race condition), retrieving existing transaction: {}", idempotencyKey);
            Optional<Transaction> existingTx = transactionRepository.findByIdempotencyKey(idempotencyKey);
            if (existingTx.isPresent()) {
                Transaction tx = existingTx.get();
                PaymentResponse response = new PaymentResponse(tx.getId(), tx.getStatus().name(), "Duplicate request – original response returned");
                cacheResponse(cacheKey, response);
                return response;
            } else {
                log.error("DataIntegrityViolationException but no existing transaction found for key: {}", idempotencyKey);
                throw new RuntimeException("Idempotency conflict, but no existing transaction found", e);
            }
        }

        // 5. Proceed with normal processing
        recordTransition(savedTransaction, TransactionStatus.PENDING, TransactionStatus.PENDING);

        TransactionStatus from = savedTransaction.getStatus();
        savedTransaction.transitionTo(TransactionStatus.AUTHORISED);
        savedTransaction = transactionRepository.save(savedTransaction);
        recordTransition(savedTransaction, from, TransactionStatus.AUTHORISED);

        from = savedTransaction.getStatus();
        savedTransaction.transitionTo(TransactionStatus.SETTLED);
        savedTransaction = transactionRepository.save(savedTransaction);
        recordTransition(savedTransaction, from, TransactionStatus.SETTLED);

        // Ledger entries (using the converted amount)
        ledgerService.recordDebit(request.getCustomerId(), finalAmount, savedTransaction.getId());
        ledgerService.recordCredit(request.getMerchantId(), finalAmount, savedTransaction.getId());

        ledgerService.validateTransactionBalance(savedTransaction.getId());

        // Audit final event
        Map<String, Object> beforeMap = new HashMap<>();
        beforeMap.put("id", null);
        beforeMap.put("invoiceId", null);
        beforeMap.put("customerId", null);
        beforeMap.put("merchantId", null);
        beforeMap.put("amount", null);
        beforeMap.put("currency", null);
        beforeMap.put("status", null);
        beforeMap.put("idempotencyKey", null);
        beforeMap.put("createdAt", null);
        beforeMap.put("updatedAt", null);

        Map<String, Object> afterMap = new HashMap<>();
        afterMap.put("id", savedTransaction.getId());
        afterMap.put("invoiceId", savedTransaction.getInvoiceId());
        afterMap.put("customerId", savedTransaction.getCustomerId());
        afterMap.put("merchantId", savedTransaction.getMerchantId());
        afterMap.put("amount", savedTransaction.getAmount().toPlainString());
        afterMap.put("currency", savedTransaction.getCurrency());
        afterMap.put("status", savedTransaction.getStatus().name());
        afterMap.put("idempotencyKey", savedTransaction.getIdempotencyKey());
        afterMap.put("createdAt", savedTransaction.getCreatedAt());
        afterMap.put("updatedAt", savedTransaction.getUpdatedAt());

        auditService.recordEvent(
            "PAYMENT_SETTLED",
            request.getCustomerId(),
            Map.of(
                "transactionId", savedTransaction.getId(),
                "originalAmount", request.getAmount().toPlainString(),
                "originalCurrency", request.getCurrency(),
                "convertedAmount", finalAmount.toPlainString(),
                "convertedCurrency", finalCurrency,
                "invoiceId", request.getInvoiceId(),
                "merchantId", request.getMerchantId()
            ),
            "Transaction",
            savedTransaction.getId(),
            beforeMap,
            afterMap
        );

        // Notifications
        notificationService.createNotification(
            request.getCustomerId(),
            "PAYMENT_SENT",
            "Payment Sent",
            String.format("You paid %.2f %s to merchant %d (Invoice %d)", finalAmount, finalCurrency, request.getMerchantId(), request.getInvoiceId()),
            "/transactions/" + savedTransaction.getId()
        );
        notificationService.createNotification(
            request.getMerchantId(),
            "PAYMENT_RECEIVED",
            "Payment Received",
            String.format("Customer %d paid %.2f %s (Invoice %d)", request.getCustomerId(), finalAmount, finalCurrency, request.getInvoiceId()),
            "/transactions/" + savedTransaction.getId()
        );

        eventPublisher.publishEvent(new TransactionSettledEvent(savedTransaction));

        PaymentResponse response = new PaymentResponse(
            savedTransaction.getId(),
            savedTransaction.getStatus().name(),
            "Payment settled successfully"
        );

        try {
            cacheResponse(cacheKey, response);
        } catch (Exception e) {
            log.warn("Failed to cache idempotency response", e);
        }

        try {
            IdempotencyKey idemKey = new IdempotencyKey();
            idemKey.setKey(idempotencyKey);
            idemKey.setResponse(objectMapper.writeValueAsString(response));
            idemKey.setExpiresAt(LocalDateTime.now().plusSeconds(idempotencyTtl));
            idemKey.setRequestFingerprint(computeFingerprint(request));
            idemKey.setTenantId(TenantContext.getRequiredTenantId());
            idempotencyKeyRepository.save(idemKey);
        } catch (Exception e) {
            log.warn("Failed to save idempotency key record", e);
        }

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