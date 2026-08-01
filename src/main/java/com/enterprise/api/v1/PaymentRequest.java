package com.enterprise.api.v1;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class PaymentRequest {

    @NotNull(message = "invoiceId is required")
    private final Long invoiceId;

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be at least 0.01")
    private final BigDecimal amount;

    @NotNull(message = "currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "currency must be a valid ISO 4217 code")
    private final String currency;

    @Size(max = 255, message = "description cannot exceed 255 characters")
    private final String description;

    public PaymentRequest(Long invoiceId, BigDecimal amount, String currency, String description) {
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.currency = currency;
        this.description = description;
    }

    public Long getInvoiceId() { return invoiceId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDescription() { return description; }
}
