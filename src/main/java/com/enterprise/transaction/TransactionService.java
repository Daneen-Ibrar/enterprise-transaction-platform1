package com.enterprise.transaction;

import com.enterprise.audit.AuditService;
import com.enterprise.ledger.LedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.enterprise.reliability.Reliable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;


@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;

    public TransactionService(TransactionRepository transactionRepository,
                              LedgerService ledgerService,
                              AuditService auditService) {
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
    }

    @Transactional
    @Reliable
    public PaymentResponse processPayment(PaymentRequest request, String idempotencyKey) {
        // 1. Check idempotency
        Optional<Transaction> existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            Transaction tx = existing.get();
            return new PaymentResponse(
                tx.getId(),
                tx.getStatus().name(),
                "Duplicate request – original response returned"
            );
        }

        // 2. Create new transaction (PENDING)
        Transaction transaction = new Transaction(
            request.getInvoiceId(),
            request.getCustomerId(),
            request.getMerchantId(),
            request.getAmount(),
            idempotencyKey
        );
        transaction = transactionRepository.save(transaction);

        // 3. Authorise (transition to AUTHORISED)
        transaction.transitionTo(TransactionStatus.AUTHORISED);
        transaction = transactionRepository.save(transaction);

        // 4. Simulate settlement (transition to SETTLED)
        transaction.transitionTo(TransactionStatus.SETTLED);
        transaction = transactionRepository.save(transaction);

        // 5. Create ledger entries
        BigDecimal amount = request.getAmount();
        ledgerService.recordDebit(request.getCustomerId(), amount, transaction.getId());
        ledgerService.recordCredit(request.getMerchantId(), amount, transaction.getId());

        // 6. Audit – single call with structured details
        auditService.recordEvent(
            "PAYMENT_SETTLED",
            request.getCustomerId(),
            Map.of(
                "transactionId", transaction.getId(),
                "amount", request.getAmount(),
                "invoiceId", request.getInvoiceId(),
                "merchantId", request.getMerchantId()
            )
        );

        // 7. Return response
        return new PaymentResponse(
            transaction.getId(),
            transaction.getStatus().name(),
            "Payment settled successfully"
        );
    }

    // For future use – retrieve transaction by ID
    public Optional<Transaction> findById(Long id) {
        return transactionRepository.findById(id);
    }
}