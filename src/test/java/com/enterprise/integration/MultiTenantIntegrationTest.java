package com.enterprise.integration;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.tenant.TenantRepository;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.Transaction;
import com.enterprise.transaction.TransactionRepository;
import com.enterprise.transaction.TransactionService;
import com.enterprise.util.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Transactional
public class MultiTenantIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder testDataBuilder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TransactionRepository transactionRepository;

    private Tenant tenantA;
    private Tenant tenantB;
    private AppUser merchantA;
    private AppUser merchantB;
    private AppUser customerA;
    private AppUser customerB;

    @BeforeEach
    void setUp() {
        tenantA = testDataBuilder.createTenant("TenantA");
        tenantB = testDataBuilder.createTenant("TenantB");

        merchantA = testDataBuilder.createUser("merchantA@test.com", "MERCHANT", tenantA);
        merchantB = testDataBuilder.createUser("merchantB@test.com", "MERCHANT", tenantB);
        customerA = testDataBuilder.createUser("customerA@test.com", "CUSTOMER", tenantA);
        customerB = testDataBuilder.createUser("customerB@test.com", "CUSTOMER", tenantB);
    }

    @AfterEach
    void tearDown() {
        tenantRepository.deleteAll();
        userRepository.deleteAll();
        invoiceRepository.deleteAll();
        transactionRepository.deleteAll();
        TenantContext.clear();
    }

    @Test
    void shouldIsolateInvoicesByTenant() {
        TenantContext.setTenantId(tenantA.getId());
        invoiceService.createInvoice(BigDecimal.valueOf(100), "Invoice A", customerA.getEmail(), merchantA.getId(), false, "GBP");

        TenantContext.setTenantId(tenantB.getId());
        invoiceService.createInvoice(BigDecimal.valueOf(200), "Invoice B", customerB.getEmail(), merchantB.getId(), false, "GBP");

        TenantContext.clear();

        TenantContext.setTenantId(tenantA.getId());
        List<Invoice> invoicesA = invoiceRepository.findAll();
        assertThat(invoicesA).hasSize(1);
        assertThat(invoicesA.get(0).getAmount()).isEqualTo(BigDecimal.valueOf(100));

        TenantContext.setTenantId(tenantB.getId());
        List<Invoice> invoicesB = invoiceRepository.findAll();
        assertThat(invoicesB).hasSize(1);
        assertThat(invoicesB.get(0).getAmount()).isEqualTo(BigDecimal.valueOf(200));

        TenantContext.clear();
    }

    @Test
    void shouldIsolateTransactionsByTenant() {
        TenantContext.setTenantId(tenantA.getId());
        Invoice invoiceA = invoiceService.createInvoice(BigDecimal.valueOf(100), "Invoice A", customerA.getEmail(), merchantA.getId(), false, "GBP");
        invoiceA.setStatus("APPROVED");
        invoiceRepository.save(invoiceA);

        String keyA = UUID.randomUUID().toString();
        PaymentRequest requestA = new PaymentRequest();
        requestA.setInvoiceId(invoiceA.getId());
        requestA.setCustomerId(customerA.getId());
        requestA.setMerchantId(merchantA.getId());
        requestA.setAmount(invoiceA.getAmount());
        transactionService.processPayment(requestA, keyA);

        TenantContext.setTenantId(tenantB.getId());
        Invoice invoiceB = invoiceService.createInvoice(BigDecimal.valueOf(200), "Invoice B", customerB.getEmail(), merchantB.getId(), false, "GBP");
        invoiceB.setStatus("APPROVED");
        invoiceRepository.save(invoiceB);

        String keyB = UUID.randomUUID().toString();
        PaymentRequest requestB = new PaymentRequest();
        requestB.setInvoiceId(invoiceB.getId());
        requestB.setCustomerId(customerB.getId());
        requestB.setMerchantId(merchantB.getId());
        requestB.setAmount(invoiceB.getAmount());
        transactionService.processPayment(requestB, keyB);

        TenantContext.clear();

        TenantContext.setTenantId(tenantA.getId());
        List<Transaction> transactionsA = transactionRepository.findAll();
        assertThat(transactionsA).hasSize(1);
        assertThat(transactionsA.get(0).getInvoiceId()).isEqualTo(invoiceA.getId());

        TenantContext.setTenantId(tenantB.getId());
        List<Transaction> transactionsB = transactionRepository.findAll();
        assertThat(transactionsB).hasSize(1);
        assertThat(transactionsB.get(0).getInvoiceId()).isEqualTo(invoiceB.getId());

        TenantContext.clear();
    }

    @Test
    void shouldIsolateUsersByTenant() {
        TenantContext.setTenantId(tenantA.getId());
        List<AppUser> usersA = userRepository.findAll();
        assertThat(usersA).allMatch(u -> u.getTenantId().equals(tenantA.getId()));
        assertThat(usersA).hasSize(2);

        TenantContext.setTenantId(tenantB.getId());
        List<AppUser> usersB = userRepository.findAll();
        assertThat(usersB).allMatch(u -> u.getTenantId().equals(tenantB.getId()));
        assertThat(usersB).hasSize(2);

        TenantContext.clear();
    }
}