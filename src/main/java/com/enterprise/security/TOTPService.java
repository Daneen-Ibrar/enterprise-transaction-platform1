package com.enterprise.security;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.code.HashingAlgorithm;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
public class TOTPService {

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final CodeVerifier verifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());

    public String generateSecretKey() {
        return secretGenerator.generate();
    }

    public String getOtpAuthUrl(String secretKey, String email) {
        QrData data = new QrData.Builder()
                .label(email)
                .secret(secretKey)
                .issuer("EnterprisePlatform")
                .algorithm(HashingAlgorithm.SHA1)
                .digits(6)
                .period(30)
                .build();
        try {
            QrGenerator qrGenerator = new ZxingPngQrGenerator();
            byte[] imageData = qrGenerator.generate(data);
            String base64 = Base64.getEncoder().encodeToString(imageData);
            return "data:image/png;base64," + base64;
        } catch (QrGenerationException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    public boolean verifyCode(String secretKey, int code) {
        return verifier.isValidCode(secretKey, String.valueOf(code));
    }
}