package com.enterprise.test;

import com.enterprise.identity.AppUser;
import com.enterprise.integration.BaseIntegrationTest;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.PaymentResponse;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionService;
import com.enterprise.util.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ConcurrencyTest extends BaseIntegrationTest {

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

    private Tenant tenant;
    private AppUser customer;
    private AppUser merchant;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        String uniqueId = UUID.randomUUID().toString();
        tenant = testDataBuilder.createTenant("ConcurrencyTest_" + uniqueId);
        customer = testDataBuilder.createUser("customer_" + uniqueId + "@concurrency.com", "CUSTOMER", tenant);
        merchant = testDataBuilder.createUser("merchant_" + uniqueId + "@concurrency.com", "MERCHANT", tenant);
        TenantContext.setTenantId(tenant.getId());

        invoice = invoiceService.createInvoice(
                BigDecimal.valueOf(100.00),
                "Concurrency test invoice",
                customer.getEmail(),
                merchant.getId(),
                false,
                "GBP"
        );
        invoice.setStatus("APPROVED");
        invoice = invoiceRepository.save(invoice);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        testDataBuilder.cleanup();
    }

    @Test
    void simultaneousDuplicateRequestsShouldCreateOnlyOnePayment() throws InterruptedException {
        int threadCount = 10;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        String idempotencyKey = UUID.randomUUID().toString();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                TenantContext.setTenantId(tenant.getId());
                try {
                    PaymentRequest request = new PaymentRequest();
                    request.setInvoiceId(invoice.getId());
                    request.setCustomerId(customer.getId());
                    request.setMerchantId(merchant.getId());
                    request.setAmount(BigDecimal.valueOf(100.00));
                    request.setCurrency("GBP");

                    transactionService.processPayment(request, idempotencyKey);
                    successCount.incrementAndGet();
                } catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    // ignore – only duplicate conflict is expected
                } finally {
                    TenantContext.clear();
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);

        var transactions = transactionRepository.findByIdempotencyKey(idempotencyKey);
        assertThat(transactions).isPresent();
    }

    @Test
    void optimisticLockingShouldPreventConcurrentUpdates() throws InterruptedException {
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("GBP");

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentResponse response = transactionService.processPayment(request, idempotencyKey);
        Long txId = response.getTransactionId();

        Transaction saved = transactionRepository.findById(txId).orElseThrow();
        assertThat(saved.getVersion()).isNotNull();
    }
}