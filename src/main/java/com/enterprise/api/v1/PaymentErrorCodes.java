package com.enterprise.api.v1;

public final class PaymentErrorCodes {

    private PaymentErrorCodes() {}

    public static final String INVALID_REQUEST = "PAY-001";
    public static final String INVOICE_NOT_FOUND = "PAY-002";
    public static final String INVOICE_NOT_PAYABLE = "PAY-003";
    public static final String INVOICE_ALREADY_PAID = "PAY-004";
    public static final String INSUFFICIENT_FUNDS = "PAY-005";
    public static final String AMOUNT_MISMATCH = "PAY-006";
    public static final String CURRENCY_MISMATCH = "PAY-007";
    public static final String IDEMPOTENCY_CONFLICT = "PAY-008";
    public static final String MERCHANT_NOT_FOUND = "PAY-009";
    public static final String CUSTOMER_NOT_FOUND = "PAY-010";
    public static final String UNAUTHORIZED = "AUTH-001";
    public static final String FORBIDDEN = "AUTH-002";
    public static final String RATE_LIMITED = "RATE-001";
    public static final String INTERNAL_ERROR = "SYS-001";
}
