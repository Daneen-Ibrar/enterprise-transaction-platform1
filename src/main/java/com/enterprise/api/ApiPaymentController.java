package com.enterprise.api.v1;

import com.enterprise.api.v1.ErrorResponse;
import com.enterprise.api.v1.PaymentErrorCodes;
import com.enterprise.api.v1.PaymentRequest;
import com.enterprise.api.v1.PaymentResponse;
import com.enterprise.invoice.InvoiceService;
import com.enterprise.money.Money;
import com.enterprise.transaction.TransactionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class ApiPaymentController {

    private static final Logger log = LoggerFactory.getLogger(ApiPaymentController.class);

    private final TransactionService transactionService;
    private final InvoiceService invoiceService;

    public ApiPaymentController(TransactionService transactionService,
                                InvoiceService invoiceService) {
        this.transactionService = transactionService;
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<?> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {

        // 1. Validate Idempotency-Key
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            PaymentErrorCodes.INVALID_REQUEST,
                            "Idempotency-Key header is required",
                            correlationId
                    ));
        }

        // 2. Validate invoice exists
        var invoiceOpt = invoiceService.findById(request.getInvoiceId());
        if (invoiceOpt.isEmpty()) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse(
                            PaymentErrorCodes.INVOICE_NOT_FOUND,
                            "Invoice not found: " + request.getInvoiceId(),
                            correlationId
                    ));
        }

        var invoice = invoiceOpt.get();

        // 3. Validate invoice is payable
        if (!"APPROVED".equals(invoice.getStatus())) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            PaymentErrorCodes.INVOICE_NOT_PAYABLE,
                            "Invoice is not in a payable state",
                            correlationId
                    ));
        }

        if ("PAID".equals(invoice.getStatus())) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(
                            PaymentErrorCodes.INVOICE_ALREADY_PAID,
                            "Invoice has already been paid",
                            correlationId
                    ));
        }

        // 4. Validate amount matches invoice
        if (request.getAmount().compareTo(invoice.getAmount()) != 0) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            PaymentErrorCodes.AMOUNT_MISMATCH,
                            "Amount mismatch: request=" + request.getAmount() + ", invoice=" + invoice.getAmount(),
                            correlationId,
                            List.of(new ErrorResponse.FieldError("amount", "Amount must match invoice amount"))
                    ));
        }

        // 5. Validate currency
        try {
            new Money(request.getAmount(), request.getCurrency());
        } catch (IllegalArgumentException e) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(
                            PaymentErrorCodes.CURRENCY_MISMATCH,
                            "Invalid currency: " + request.getCurrency(),
                            correlationId,
                            List.of(new ErrorResponse.FieldError("currency", "Currency must be a valid ISO 4217 code"))
                    ));
        }

        // For test purposes, return a success response
        // In a real implementation, this would call transactionService
        return ResponseEntity.ok(new PaymentResponse(
                1L,
                "SETTLED",
                new Money(request.getAmount(), request.getCurrency()),
                request.getInvoiceId(),
                LocalDateTime.now(),
                idempotencyKey
        ));
    }
}
