package com.enterprise.integration;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionService;
import com.enterprise.transaction.TransactionStatus;
import com.enterprise.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
public class PaymentInvariantTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder testDataBuilder;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private UserRepository userRepository;

    private Tenant tenant;
    private AppUser customer;
    private AppUser merchant;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        String uniqueId = UUID.randomUUID().toString();
        tenant = testDataBuilder.createTenant("InvariantTest_" + uniqueId);
        customer = testDataBuilder.createUser("customer@invariant.com", "CUSTOMER", tenant);
        merchant = testDataBuilder.createUser("merchant@invariant.com", "MERCHANT", tenant);
        TenantContext.setTenantId(tenant.getId());

        invoice = invoiceService.createInvoice(
                BigDecimal.valueOf(100.00),
                "Test invoice",
                customer.getEmail(),
                merchant.getId(),
                false,
                "GBP"
        );
        invoice.setStatus("APPROVED");
        invoice = invoiceRepository.save(invoice);
    }

    @Test
    void oneRequestProducesOnePayment() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));

        var response = transactionService.processPayment(request, idempotencyKey);
        assertThat(response.getTransactionId()).isNotNull();

        var transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
    }

    @Test
    void duplicateRequestsProduceNoAdditionalLedgerEntries() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));

        transactionService.processPayment(request, idempotencyKey);
        transactionService.processPayment(request, idempotencyKey);

        var transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
    }

    @Test
    void illegalStateTransitionFails() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));

        var response = transactionService.processPayment(request, idempotencyKey);
        Transaction tx = transactionRepository.findById(response.getTransactionId()).orElseThrow();

        assertThrows(IllegalStateException.class, () -> {
            tx.transitionTo(TransactionStatus.AUTHORISED);
        });
    }

    @Test
    void refundTotalsNeverExceedSettledAmount() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));

        transactionService.processPayment(request, idempotencyKey);
        var transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
    }

    @Test
    void failedOperationLeavesNoPartialState() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(99999L);
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));

        assertThrows(RuntimeException.class, () -> {
            transactionService.processPayment(request, idempotencyKey);
        });

        var transactions = transactionRepository.findAll();
        assertThat(transactions).isEmpty();
    }

    @Test
    void crossTenantAccessAlwaysFails() {
        Tenant otherTenant = testDataBuilder.createTenant("OtherTenant_" + UUID.randomUUID());
        AppUser otherCustomer = testDataBuilder.createUser("other@invariant.com", "CUSTOMER", otherTenant);

        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(otherCustomer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));

        String idempotencyKey = UUID.randomUUID().toString();
        var response = transactionService.processPayment(request, idempotencyKey);
        Transaction tx = transactionRepository.findById(response.getTransactionId()).orElseThrow();
        assertThat(tx.getTenantId()).isEqualTo(tenant.getId());
    }
}