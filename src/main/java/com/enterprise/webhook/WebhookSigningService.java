package com.enterprise.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class WebhookSigningService {

    @Value("${app.webhook.signing-secret:}")
    private String signingSecret;

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    public String generateSignature(String payload) {
        if (signingSecret == null || signingSecret.isEmpty()) {
            return ""; // No signing if secret not configured
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    signingSecret.getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(signature);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate webhook signature", e);
        }
    }
}