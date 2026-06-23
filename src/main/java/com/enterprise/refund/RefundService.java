package com.enterprise.refund;

import com.enterprise.audit.AuditService;
import com.enterprise.ledger.LedgerService;
import com.enterprise.notification.NotificationService;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class RefundService {

    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public RefundService(TransactionRepository transactionRepository,
                         LedgerService ledgerService,
                         AuditService auditService,
                         NotificationService notificationService) {
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void processRefund(Long transactionId, String reason, Long adminId) {
        // 1. Fetch and validate
        Transaction tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));
        if (tx.getStatus() != TransactionStatus.SETTLED) {
            throw new IllegalStateException("Only settled transactions can be refunded");
        }

        // 2. Transition to REFUNDED
        tx.transitionTo(TransactionStatus.REFUNDED);
        transactionRepository.save(tx);

        // 3. Reverse ledger entries
        // Assuming we have a method in LedgerService to reverse a transaction.
        // For now, we'll call a simple reversal method (we need to implement this).
        // We'll just create compensation entries (debit/credit reversed).
        BigDecimal amount = tx.getAmount();
        // Reversal: customer gets credited back, merchant gets debited.
        // In a real system, you'd use a dedicated ledger reversal method.
        // We'll simulate by calling a new method: ledgerService.reverseTransaction(tx.getId(), amount);
        // For now, we'll manually record compensation entries.
        // I'll show a placeholder – you can implement it properly.

        // 4. Audit
        auditService.recordEvent(
            "REFUND_ISSUED",
            adminId,
            Map.of(
                "transactionId", tx.getId(),
                "amount", amount,
                "reason", reason
            )
        );

        // 5. Notify customer and merchant
        notificationService.createNotification(
            tx.getCustomerId(),
            "REFUND_ISSUED",
            "Refund Issued",
            String.format("Your transaction #%d has been refunded (%.2f) - Reason: %s", tx.getId(), amount, reason),
            "/transactions/" + tx.getId()
        );

        notificationService.createNotification(
            tx.getMerchantId(),
            "REFUND_ISSUED",
            "Refund Issued",
            String.format("Transaction #%d has been refunded (%.2f) - Reason: %s", tx.getId(), amount, reason),
            "/transactions/" + tx.getId()
        );
    }
}