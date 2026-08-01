package com.enterprise.test;

import com.enterprise.identity.AppUser;
import com.enterprise.integration.BaseIntegrationTest;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.TransactionService;
import com.enterprise.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Transactional
public class FailureInjectionTest extends BaseIntegrationTest {

    @Autowired
    private TestDataBuilder testDataBuilder;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @MockBean
    private StringRedisTemplate redisTemplate;

    private Tenant tenant;
    private AppUser customer;
    private AppUser merchant;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        String uniqueId = UUID.randomUUID().toString();
        tenant = testDataBuilder.createTenant("FailureTest_" + uniqueId);
        customer = testDataBuilder.createUser("customer@failure.com", "CUSTOMER", tenant);
        merchant = testDataBuilder.createUser("merchant@failure.com", "MERCHANT", tenant);
        TenantContext.setTenantId(tenant.getId());

        invoice = invoiceService.createInvoice(
                BigDecimal.valueOf(100.00),
                "Failure test invoice",
                customer.getEmail(),
                merchant.getId(),
                false,
                "GBP"
        );
        invoice.setStatus("APPROVED");
        invoice = invoiceRepository.save(invoice);
    }

    @Test
    void whenRedisIsUnavailable_shouldFallbackToDatabase() {
        // Simulate Redis failure
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis connection failed"));

        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoice.getId());
        request.setCustomerId(customer.getId());
        request.setMerchantId(merchant.getId());
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setCurrency("GBP");

        String idempotencyKey = UUID.randomUUID().toString();

        // Should still succeed (fallback to DB)
        var response = transactionService.processPayment(request, idempotencyKey);
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SETTLED");
    }
}