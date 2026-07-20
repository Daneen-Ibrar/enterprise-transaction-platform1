package com.enterprise.integration;

import com.enterprise.identity.AppUser;
import com.enterprise.invoice.Invoice;
import com.enterprise.ledger.LedgerEntry;
import com.enterprise.ledger.LedgerRepository;
import com.enterprise.refund.RefundRule;
import com.enterprise.refund.RefundRuleRepository;
import com.enterprise.refund.RefundService;
import com.enterprise.tenant.Tenant;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionService;
import com.enterprise.util.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Transactional
public class RefundIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder testDataBuilder;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private RefundService refundService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private RefundRuleRepository refundRuleRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Tenant tenant;
    private AppUser customer;
    private AppUser merchant;
    private AppUser admin;
    private Invoice invoice;
    private Transaction settledTransaction;

    @BeforeEach
    void setUp() {
        tenant = testDataBuilder.createTenant("RefundTest");
        customer = testDataBuilder.createUser("customer@refund.com", "CUSTOMER", tenant);
        merchant = testDataBuilder.createUser("merchant@refund.com", "MERCHANT", tenant);
        admin = testDataBuilder.createUser("admin@refund.com", "ADMIN", tenant);

        refundRuleRepository.deleteAll();
        RefundRule allowRule = new RefundRule();
        allowRule.setRulePriority(1);
        allowRule.setConditionExpression("#amount <= 1000");
        allowRule.setAction("ALLOW");
        allowRule.setActive(true);
        allowRule.setTenantId(tenant.getId());
        refundRuleRepository.save(allowRule);

        invoice = testDataBuilder.createInvoiceApproved(
                merchant.getId(),
                customer.getEmail(),
                BigDecimal.valueOf(100.00),
                "Refund test invoice"
        );

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(invoice.getAmount());
        transactionService.processPayment(request, idempotencyKey);

        settledTransaction = transactionRepository.findAll().stream()
                .filter(tx -> "SETTLED".equals(tx.getStatus().name()))
                .findFirst()
                .orElseThrow();
    }

    @AfterEach
    void tearDown() {
        redisTemplate.keys("idem:*").forEach(key -> redisTemplate.delete(key));
        refundRuleRepository.deleteAll();
        testDataBuilder.cleanup();
    }

    @Test
    void shouldProcessRefundSuccessfully() {
        String reason = "Customer requested refund";

        Transaction refund = refundService.processRefund(settledTransaction.getId(), admin.getId(), reason);

        assertThat(refund).isNotNull();
        assertThat(refund.getStatus().name()).isEqualTo("REFUNDED");
        assertThat(refund.getAmount()).isNegative();

        Transaction updatedOriginal = transactionRepository.findById(settledTransaction.getId()).orElseThrow();
        assertThat(updatedOriginal.getStatus().name()).isEqualTo("REFUNDED");

        List<LedgerEntry> refundLedgerEntries = ledgerRepository.findByTransactionId(refund.getId());
        assertThat(refundLedgerEntries).hasSize(2);
    }

    @Test
    void shouldNotRefundNonSettledTransaction() {
        Invoice pendingInvoice = testDataBuilder.createInvoiceApproved(
                merchant.getId(),
                customer.getEmail(),
                BigDecimal.valueOf(50.00),
                "Pending refund test"
        );
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(pendingInvoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(pendingInvoice.getAmount());
        transactionService.processPayment(request, idempotencyKey);

        Transaction pendingTx = transactionRepository.findAll().stream()
                .filter(tx -> "PENDING".equals(tx.getStatus().name()))
                .findFirst()
                .orElseThrow();

        assertThrows(IllegalStateException.class, () -> {
            refundService.processRefund(pendingTx.getId(), admin.getId(), "Invalid refund");
        });
    }

    @Test
    void shouldRejectRefundWhenEligibilityFails() {
        refundRuleRepository.deleteAll();
        RefundRule denyRule = new RefundRule();
        denyRule.setRulePriority(1);
        denyRule.setConditionExpression("#amount > 0");
        denyRule.setAction("DENY");
        denyRule.setActive(true);
        denyRule.setTenantId(tenant.getId());
        refundRuleRepository.save(denyRule);

        assertThrows(RuntimeException.class, () -> {
            refundService.processRefund(settledTransaction.getId(), admin.getId(), "Should fail");
        });
    }
}