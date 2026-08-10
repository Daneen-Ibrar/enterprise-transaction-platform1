package com.enterprise.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin/webhooks/simulator")
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'MERCHANT_ADMIN')")
public class WebhookSimulatorController {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public WebhookSimulatorController(RestTemplate restTemplate,
                                      ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String simulatorPage(Model model) {
        // Pre-fill with a sample payload
        Map<String, Object> samplePayload = new HashMap<>();
        samplePayload.put("eventType", "TRANSACTION_SETTLED");
        samplePayload.put("timestamp", Instant.now().toString());
        samplePayload.put("transactionId", 12345);
        samplePayload.put("amount", "100.00");
        samplePayload.put("currency", "GBP");
        samplePayload.put("invoiceId", 6789);
        samplePayload.put("merchantId", 1);

        try {
            String sampleJson = objectMapper.writeValueAsString(samplePayload);
            model.addAttribute("samplePayload", sampleJson);
        } catch (Exception e) {
            model.addAttribute("samplePayload", "{\"eventType\":\"TRANSACTION_SETTLED\"}");
        }

        model.addAttribute("eventTypes", new String[]{"TRANSACTION_SETTLED", "REFUND_PROCESSED"});
        return "admin/webhooks/simulator";
    }

    @PostMapping("/send")
    @ResponseBody
    public Map<String, Object> sendTestWebhook(@RequestParam String url,
                                               @RequestParam(required = false, defaultValue = "TRANSACTION_SETTLED") String eventType,
                                               @RequestParam(required = false) String payload) {
        Map<String, Object> response = new HashMap<>();

        try {
            // Build payload
            Map<String, Object> payloadMap;
            if (payload != null && !payload.trim().isEmpty()) {
                payloadMap = objectMapper.readValue(payload, Map.class);
            } else {
                payloadMap = new HashMap<>();
                payloadMap.put("eventType", eventType);
                payloadMap.put("timestamp", Instant.now().toString());
                payloadMap.put("test", true);
                payloadMap.put("message", "This is a test webhook from the simulator.");
            }

            String payloadJson = objectMapper.writeValueAsString(payloadMap);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> request = new HttpEntity<>(payloadJson, headers);

            long startTime = System.currentTimeMillis();
            ResponseEntity<String> httpResponse = restTemplate.postForEntity(url, request, String.class);
            long duration = System.currentTimeMillis() - startTime;

            // ✅ Fix: get reason phrase safely
            String reasonPhrase = "";
            try {
                HttpStatus status = HttpStatus.valueOf(httpResponse.getStatusCode().value());
                reasonPhrase = status.getReasonPhrase();
            } catch (IllegalArgumentException e) {
                reasonPhrase = "Unknown";
            }

            response.put("success", true);
            response.put("statusCode", httpResponse.getStatusCode().value());
            response.put("statusText", reasonPhrase);
            response.put("headers", httpResponse.getHeaders().toSingleValueMap());
            response.put("body", httpResponse.getBody());
            response.put("durationMs", duration);
            response.put("sentPayload", payloadMap);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }
}