package com.enterprise.api;

import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.PaymentResponse;
import com.enterprise.transaction.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private final TransactionService transactionService;

    public PaymentController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/api/payments")
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = true) String idempotencyKey) {

        PaymentResponse response = transactionService.processPayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}