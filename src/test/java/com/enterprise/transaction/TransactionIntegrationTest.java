package com.enterprise.transaction;

import com.enterprise.TransactionPlatformApplication;
import com.enterprise.apikey.ApiKey;
import com.enterprise.apikey.ApiKeyService;
import com.enterprise.identity.AppUser;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceRepository;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.util.TestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(classes = TransactionPlatformApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Transactional
public class TransactionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataBuilder testDataBuilder;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String apiKeyHeader;
    private Long invoiceId;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @BeforeEach
    void setUp() {
        String uniqueId = UUID.randomUUID().toString();
        Tenant tenant = testDataBuilder.createTenant("TransactionTest_" + uniqueId);
        AppUser merchant = testDataBuilder.createUser("merchant_" + uniqueId + "@test.com", "MERCHANT", tenant);
        AppUser customer = testDataBuilder.createUser("customer_" + uniqueId + "@test.com", "CUSTOMER", tenant);

        TenantContext.setTenantId(tenant.getId());

        ApiKey apiKey = apiKeyService.generateApiKey(merchant, "TestKey");
        apiKeyHeader = apiKey.getKeyValue();

        Invoice invoice = invoiceService.createInvoice(
                BigDecimal.valueOf(100.00),
                "Test invoice",
                customer.getEmail(),
                merchant.getId(),
                false,
                "GBP"
        );
        invoice.setStatus("APPROVED");
        invoice = invoiceRepository.save(invoice);
        invoiceId = invoice.getId();
    }

    @AfterEach
    void cleanUp() {
        Set<String> keys = redisTemplate.keys("idem:*");
        if (keys != null) {
            redisTemplate.delete(keys);
        }
        // Also clear tenant context
        TenantContext.clear();
    }

    @Test
    void shouldProcessPaymentSuccessfully() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoiceId);
        request.setCustomerId(1L);
        request.setMerchantId(1L);
        request.setAmount(BigDecimal.valueOf(100.00));

        mockMvc.perform(post("/api/payments")
                .header("X-API-Key", apiKeyHeader)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.message").value("Payment settled successfully"));
    }

    @Test
    void shouldReturnIdempotentResponseForDuplicateRequest() throws Exception {
        String idempotencyKey = UUID.randomUUID().toString();
        PaymentRequest request = new PaymentRequest();
        request.setInvoiceId(invoiceId);
        request.setCustomerId(1L);
        request.setMerchantId(1L);
        request.setAmount(BigDecimal.valueOf(100.00));

        // First request
        mockMvc.perform(post("/api/payments")
                .header("X-API-Key", apiKeyHeader)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment settled successfully"));

        // Second request – should return cached response
        mockMvc.perform(post("/api/payments")
                .header("X-API-Key", apiKeyHeader)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Duplicate request – original response returned"));
    }
}