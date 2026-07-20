package com.enterprise.integration;

import com.enterprise.identity.AppUser;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.ledger.LedgerEntry;
import com.enterprise.ledger.LedgerRepository;
import com.enterprise.tenant.Tenant;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.PaymentResponse;
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
public class PaymentIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder testDataBuilder;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private LedgerRepository ledgerRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private Tenant tenant;
    private AppUser customer;
    private AppUser merchant;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        tenant = testDataBuilder.createTenant("PaymentTest");
        customer = testDataBuilder.createUser("customer@payment.com", "CUSTOMER", tenant);
        merchant = testDataBuilder.createUser("merchant@payment.com", "MERCHANT", tenant);
        invoice = testDataBuilder.createInvoiceApproved(
                merchant.getId(),
                customer.getEmail(),
                BigDecimal.valueOf(100.00),
                "Integration test invoice"
        );
    }

    @AfterEach
    void tearDown() {
        redisTemplate.keys("idem:*").forEach(key -> redisTemplate.delete(key));
        testDataBuilder.cleanup();
    }

    @Test
    void shouldProcessPaymentSuccessfully() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(invoice.getAmount());

        PaymentResponse response = transactionService.processPayment(request, idempotencyKey);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SETTLED");
        assertThat(response.getTransactionId()).isNotNull();

        var transaction = transactionRepository.findById(response.getTransactionId()).orElseThrow();
        assertThat(transaction.getStatus().name()).isEqualTo("SETTLED");
        assertThat(transaction.getAmount()).isEqualTo(invoice.getAmount());

        Invoice updatedInvoice = invoiceRepository.findById(invoice.getId()).orElseThrow();
        assertThat(updatedInvoice.getStatus()).isEqualTo("PAID");

        List<LedgerEntry> ledgerEntries = ledgerRepository.findByTransactionId(transaction.getId());
        assertThat(ledgerEntries).hasSize(2);
        assertThat(ledgerEntries).extracting("entryType").containsExactly("DEBIT", "CREDIT");
        assertThat(ledgerEntries.get(0).getAmount()).isEqualTo(invoice.getAmount());
        assertThat(ledgerEntries.get(1).getAmount()).isEqualTo(invoice.getAmount());
    }

    @Test
    void shouldBeIdempotentWhenSameKeyUsed() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(invoice.getAmount());

        PaymentResponse firstResponse = transactionService.processPayment(request, idempotencyKey);
        PaymentResponse secondResponse = transactionService.processPayment(request, idempotencyKey);

        assertThat(secondResponse.getTransactionId()).isEqualTo(firstResponse.getTransactionId());
        assertThat(secondResponse.getStatus()).isEqualTo(firstResponse.getStatus());

        var transactions = transactionRepository.findAll();
        assertThat(transactions).hasSize(1);
    }

    @Test
    void shouldRejectInvalidInvoice() {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(99999L);
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));

        assertThrows(RuntimeException.class, () -> {
            transactionService.processPayment(request, idempotencyKey);
        });
    }

    @Test
    void shouldNotCreateDuplicateTransactionsWithDifferentKeys() {
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(invoice.getAmount());

        String key1 = UUID.randomUUID().toString();
        String key2 = UUID.randomUUID().toString();

        PaymentResponse response1 = transactionService.processPayment(request, key1);
        PaymentResponse response2 = transactionService.processPayment(request, key2);

        assertThat(response2.getTransactionId()).isEqualTo(response1.getTransactionId());
    }
}