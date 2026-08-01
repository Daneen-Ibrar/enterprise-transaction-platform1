package com.enterprise.api.v1;

import com.enterprise.invoice.InvoiceService;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.PaymentResponse;
import com.enterprise.transaction.TransactionService;
import com.enterprise.validation.ValidIdempotencyKey;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
public class V1PaymentController {

    private static final Logger log = LoggerFactory.getLogger(V1PaymentController.class);

    private final TransactionService transactionService;
    private final InvoiceService invoiceService;

    public V1PaymentController(TransactionService transactionService,
                               InvoiceService invoiceService) {
        this.transactionService = transactionService;
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true)
            @ValidIdempotencyKey String idempotencyKey) {

        log.info("API payment request for invoice {}", request.getInvoiceId());

        PaymentResponse response = transactionService.processPayment(request, idempotencyKey);

        if ("SETTLED".equals(response.getStatus()) && !invoiceService.isInvoicePaid(request.getInvoiceId())) {
            invoiceService.markAsPaid(request.getInvoiceId(), response.getTransactionId());
        }

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}