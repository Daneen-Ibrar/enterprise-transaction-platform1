package com.enterprise.refund;

import com.enterprise.audit.AuditService;
import com.enterprise.events.RefundProcessedEvent;
import com.enterprise.feature.FeatureFlagService;
import com.enterprise.ledger.LedgerService;
import com.enterprise.notification.NotificationService;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRuleRepository ruleRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final FeatureFlagService featureFlagService;
    private final ExpressionParser parser = new SpelExpressionParser();

    public RefundService(RefundRuleRepository ruleRepository,
                         TransactionRepository transactionRepository,
                         LedgerService ledgerService,
                         AuditService auditService,
                         NotificationService notificationService,
                         ApplicationEventPublisher eventPublisher,
                         FeatureFlagService featureFlagService) {
        this.ruleRepository = ruleRepository;
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
        this.featureFlagService = featureFlagService;
    }

    public RefundEligibility evaluate(Transaction transaction) {
        log.info("Evaluating refund eligibility for transaction {}", transaction.getId());
        List<RefundRule> rules = ruleRepository.findByActiveTrueOrderByRulePriorityAsc();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("amount", transaction.getAmount());
        context.setVariable("customerId", transaction.getCustomerId());
        context.setVariable("merchantId", transaction.getMerchantId());

        for (RefundRule rule : rules) {
            try {
                Boolean matches = parser.parseExpression(rule.getConditionExpression())
                        .getValue(context, Boolean.class);
                if (Boolean.TRUE.equals(matches)) {
                    log.info("Rule matched: action={}", rule.getAction());
                    return new RefundEligibility(rule.getAction(), rule.getRequiredPermission());
                }
            } catch (Exception e) {
                log.warn("Rule evaluation failed for expression: {}", rule.getConditionExpression(), e);
            }
        }
        log.warn("No matching rule, defaulting to DENY");
        return new RefundEligibility("DENY", null);
    }

    // ===== SINGLE REFUND =====
    @Transactional
    public Transaction processRefund(Long originalTransactionId, Long adminId, String reason) {
        if (!featureFlagService.isEnabled("REFUNDS")) {
            throw new RuntimeException("Refund feature is currently disabled.");
        }

        log.info("Processing refund for transaction {} by admin {}", originalTransactionId, adminId);
        Transaction original = transactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        if (original.getStatus() != TransactionStatus.SETTLED) {
            throw new IllegalStateException("Only settled transactions can be refunded");
        }

        RefundEligibility eligibility = evaluate(original);
        if ("DENY".equals(eligibility.getAction())) {
            throw new RuntimeException("Refund not allowed by policy");
        }

        Transaction refund = new Transaction(
                original.getInvoiceId(),
                original.getMerchantId(),
                original.getCustomerId(),
                original.getAmount().negate(),
                "refund-" + System.currentTimeMillis()
        );
        // ===== FIX: Set tenant ID from original transaction =====
        refund.setTenantId(original.getTenantId());

        refund.setStatus(TransactionStatus.REFUNDED);
        refund = transactionRepository.save(refund);

        ledgerService.recordCredit(original.getCustomerId(), original.getAmount(), refund.getId());
        ledgerService.recordDebit(original.getMerchantId(), original.getAmount(), refund.getId());

        original.setStatus(TransactionStatus.REFUNDED);
        transactionRepository.save(original);

        // Audit
        Map<String, Object> beforeMap = new HashMap<>();
        beforeMap.put("id", original.getId());
        beforeMap.put("invoiceId", original.getInvoiceId());
        beforeMap.put("customerId", original.getCustomerId());
        beforeMap.put("merchantId", original.getMerchantId());
        beforeMap.put("amount", original.getAmount());
        beforeMap.put("currency", original.getCurrency());
        beforeMap.put("status", original.getStatus().name());
        beforeMap.put("idempotencyKey", original.getIdempotencyKey());
        beforeMap.put("createdAt", original.getCreatedAt());
        beforeMap.put("updatedAt", original.getUpdatedAt());

        Map<String, Object> afterMap = new HashMap<>();
        afterMap.put("id", refund.getId());
        afterMap.put("invoiceId", refund.getInvoiceId());
        afterMap.put("customerId", refund.getCustomerId());
        afterMap.put("merchantId", refund.getMerchantId());
        afterMap.put("amount", refund.getAmount());
        afterMap.put("currency", refund.getCurrency());
        afterMap.put("status", refund.getStatus().name());
        afterMap.put("idempotencyKey", refund.getIdempotencyKey());
        afterMap.put("createdAt", refund.getCreatedAt());
        afterMap.put("updatedAt", refund.getUpdatedAt());

        auditService.recordEvent(
                "REFUND_PROCESSED",
                adminId,
                Map.of(
                        "originalTransactionId", originalTransactionId,
                        "refundTransactionId", refund.getId(),
                        "amount", original.getAmount(),
                        "reason", reason
                ),
                "Transaction",
                refund.getId(),
                beforeMap,
                afterMap
        );

        notificationService.createNotification(original.getCustomerId(),
                "REFUND_RECEIVED", "Refund Processed",
                "Refund of " + original.getAmount() + " for transaction " + originalTransactionId,
                "/transactions/" + refund.getId());
        notificationService.createNotification(original.getMerchantId(),
                "REFUND_ISSUED", "Refund Issued",
                "Refund of " + original.getAmount() + " to customer",
                "/transactions/" + refund.getId());

        eventPublisher.publishEvent(new RefundProcessedEvent(refund, original));
        log.info("Refund completed for transaction {}", originalTransactionId);

        return refund;
    }

    // ===== BULK REFUND (NO @Transactional) =====
    public int bulkRefundTransactions(List<Long> transactionIds, Long adminId, String reason) {
        log.info("Bulk refunding {} transactions by admin {}", transactionIds.size(), adminId);
        if (!featureFlagService.isEnabled("REFUNDS")) {
            throw new RuntimeException("Refund feature is currently disabled.");
        }
        int refundedCount = 0;
        for (Long txId : transactionIds) {
            try {
                log.info("Attempting refund for transaction {}", txId);
                processRefund(txId, adminId, reason);
                refundedCount++;
                log.info("Successfully refunded transaction {}", txId);
                Thread.sleep(50);
            } catch (Exception e) {
                log.error("Failed to refund transaction {}: {}", txId, e.getMessage(), e);
            }
        }
        log.info("Bulk refund completed: {} out of {} successful", refundedCount, transactionIds.size());
        return refundedCount;
    }

    public static class RefundEligibility {
        private final String action;
        private final String requiredPermission;
        public RefundEligibility(String action, String requiredPermission) {
            this.action = action;
            this.requiredPermission = requiredPermission;
        }
        public String getAction() { return action; }
        public String getRequiredPermission() { return requiredPermission; }
    }
}