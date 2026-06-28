package com.enterprise.api;

import com.enterprise.identity.AppUser;
import com.enterprise.identity.UserRepository;
import com.enterprise.transaction.PaymentRequest;
import com.enterprise.transaction.PaymentResponse;
import com.enterprise.transaction.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/public")
@Tag(name = "Public Payment API", description = "Endpoints for external integration")
public class PublicApiController {

    private final TransactionService transactionService;
    private final UserRepository userRepository;

    public PublicApiController(TransactionService transactionService,
                               UserRepository userRepository) {
        this.transactionService = transactionService;
        this.userRepository = userRepository;
    }

    @PostMapping("/payments")
    @Operation(summary = "Process a payment", description = "Accepts invoice details and processes payment")
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestBody PaymentRequest request,
            Authentication authentication) {

        String email = authentication.getName();
        AppUser merchant = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        request.setMerchantId(merchant.getId());

        // Currency defaults to GBP if not provided
        if (request.getCurrency() == null || request.getCurrency().isEmpty()) {
            request.setCurrency("GBP");
        }

        String idempotencyKey = UUID.randomUUID().toString();
        PaymentResponse response = transactionService.processPayment(request, idempotencyKey);
        return ResponseEntity.ok(response);
    }

    // Test endpoint for authentication
    @GetMapping("/test")
    public String test() {
        return "API key works!";
    }
}