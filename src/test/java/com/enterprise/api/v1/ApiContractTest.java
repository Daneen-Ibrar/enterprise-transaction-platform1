package com.enterprise.api.v1;

import com.enterprise.api.v1.PaymentRequest;
import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.invoice.Invoice;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.util.TestDataBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@Import(TestDataBuilder.class)
@Transactional
public class ApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestDataBuilder testDataBuilder;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private UserRepository userRepository;

    private Tenant tenant;
    private AppUser customer;
    private AppUser merchant;
    private Invoice invoice;

    @BeforeEach
    void setUp() {
        tenant = testDataBuilder.createTenant("ApiContractTest");
        customer = testDataBuilder.createUser("customer@apicontract.com", "CUSTOMER", tenant);
        merchant = testDataBuilder.createUser("merchant@apicontract.com", "MERCHANT", tenant);
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
    }

    @Test
    void shouldReturn400WhenIdempotencyKeyMissing() throws Exception {
        PaymentRequest request = new PaymentRequest(
                invoice.getId(),
                BigDecimal.valueOf(100.00),
                "GBP",
                "Test payment"
        );

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAY-001"))
                .andExpect(jsonPath("$.message").value("Idempotency-Key header is required"));
    }

    @Test
    void shouldReturn400WhenAmountDoesNotMatchInvoice() throws Exception {
        PaymentRequest request = new PaymentRequest(
                invoice.getId(),
                BigDecimal.valueOf(200.00),
                "GBP",
                "Test payment"
        );
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAY-006"));
    }

    @Test
    void shouldReturn200WhenPaymentSuccessful() throws Exception {
        PaymentRequest request = new PaymentRequest(
                invoice.getId(),
                BigDecimal.valueOf(100.00),
                "GBP",
                "Test payment"
        );
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.status").value("SETTLED"));
    }

    @Test
    void shouldReturn404WhenInvoiceNotFound() throws Exception {
        PaymentRequest request = new PaymentRequest(
                99999L,
                BigDecimal.valueOf(100.00),
                "GBP",
                "Test payment"
        );
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAY-002"));
    }

    @Test
    void shouldReturnBadRequestWhenCurrencyInvalid() throws Exception {
        PaymentRequest request = new PaymentRequest(
                invoice.getId(),
                BigDecimal.valueOf(100.00),
                "XYZ",
                "Test payment"
        );
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", idempotencyKey)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PAY-007"));
    }
}
