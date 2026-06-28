package com.enterprise.transaction;

import com.enterprise.audit.AuditService;
import com.enterprise.identity.UserRepository;
import com.enterprise.ledger.LedgerService;
import com.enterprise.notification.EmailNotificationService;
import com.enterprise.notification.EmailTemplates;
import com.enterprise.notification.NotificationService;
import com.enterprise.reliability.Reliable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final EmailNotificationService emailNotificationService;
    private final UserRepository userRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              LedgerService ledgerService,
                              AuditService auditService,
                              NotificationService notificationService,
                              EmailNotificationService emailNotificationService,
                              UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.notificationService = notificationService;
        this.emailNotificationService = emailNotificationService;
        this.userRepository = userRepository;
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

        // 2. Create transaction with currency
        String currency = request.getCurrency() != null ? request.getCurrency() : "GBP";
        Transaction transaction = new Transaction(
            request.getInvoiceId(),
            request.getCustomerId(),
            request.getMerchantId(),
            request.getAmount(),
            idempotencyKey,
            currency
        );
        transaction = transactionRepository.save(transaction);

        // 3. Authorise
        transaction.transitionTo(TransactionStatus.AUTHORISED);
        transaction = transactionRepository.save(transaction);

        // 4. Settle
        transaction.transitionTo(TransactionStatus.SETTLED);
        transaction = transactionRepository.save(transaction);

        // 5. Ledger entries (amount stays in transaction currency, but ledger stores the numeric amount)
        BigDecimal amount = request.getAmount();
        ledgerService.recordDebit(request.getCustomerId(), amount, transaction.getId());
        ledgerService.recordCredit(request.getMerchantId(), amount, transaction.getId());

        // 6. Audit
        auditService.recordEvent(
            "PAYMENT_SETTLED",
            request.getCustomerId(),
            Map.of(
                "transactionId", transaction.getId(),
                "amount", request.getAmount(),
                "currency", currency,
                "invoiceId", request.getInvoiceId(),
                "merchantId", request.getMerchantId()
            )
        );

        // 7. In-app notifications
        notificationService.createNotification(
            request.getCustomerId(),
            "PAYMENT_SENT",
            "Payment Sent",
            String.format("You paid %.2f %s to merchant %d (Invoice %d)", amount, currency, request.getMerchantId(), request.getInvoiceId()),
            "/transactions/" + transaction.getId()
        );

        notificationService.createNotification(
            request.getMerchantId(),
            "PAYMENT_RECEIVED",
            "Payment Received",
            String.format("Customer %d paid %.2f %s (Invoice %d)", request.getCustomerId(), amount, currency, request.getInvoiceId()),
            "/transactions/" + transaction.getId()
        );

        // 8. Email
        String customerEmail = userRepository.findById(request.getCustomerId())
                .map(u -> u.getEmail())
                .orElse(null);
        if (customerEmail != null) {
            emailNotificationService.sendEmailAsync(
                customerEmail,
                "Payment Confirmation",
                EmailTemplates.paymentConfirmation(
                    "Customer",
                    request.getInvoiceId(),
                    transaction.getId(),
                    amount,
                    currency
                )
            );
        }

        // 9. Return response
        return new PaymentResponse(
            transaction.getId(),
            transaction.getStatus().name(),
            "Payment settled successfully"
        );
    }

    public Optional<Transaction> findById(Long id) {
        return transactionRepository.findById(id);
    }
}