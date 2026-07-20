package com.enterprise.apikey;

import com.enterprise.identity.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

    private static final String API_KEY_PREFIX = "ep_";
    private static final int KEY_LENGTH = 32;
    private static final int PREFIX_LENGTH = 8;
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @Transactional
    public ApiKey generateApiKey(AppUser user, String name) {
        // Generate full key
        String fullKey = generateSecureKey();

        ApiKey apiKey = new ApiKey();
        // Store the FULL key in key_value (this will be returned to the user)
        apiKey.setKeyValue(fullKey);
        apiKey.setKeyHash(passwordEncoder.encode(fullKey));
        apiKey.setKeyPrefix(fullKey.substring(0, PREFIX_LENGTH));
        apiKey.setName(name);
        apiKey.setUser(user);
        apiKey.setActive(true);
        apiKey.setTenantId(user.getTenantId());

        log.info("Generated API key for user: {}", user.getEmail());

        return apiKeyRepository.save(apiKey);
    }

    @Transactional
    public void revokeApiKey(Long keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("API Key not found"));
        key.setActive(false);
        apiKeyRepository.save(key);
        log.info("Revoked API key: {}", key.getKeyPrefix());
    }

    public Optional<ApiKey> validateKey(String keyValue) {
        if (keyValue == null || keyValue.isEmpty()) {
            return Optional.empty();
        }

        List<ApiKey> activeKeys = apiKeyRepository.findActiveKeys();

        for (ApiKey key : activeKeys) {
            if (passwordEncoder.matches(keyValue, key.getKeyHash())) {
                if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
                    log.debug("API key expired: {}", key.getKeyPrefix());
                    return Optional.empty();
                }
                log.debug("API key validated: {}", key.getKeyPrefix());
                return Optional.of(key);
            }
        }
        log.debug("Invalid API key provided");
        return Optional.empty();
    }

    @Transactional
    public void updateLastUsed(ApiKey apiKey) {
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(apiKey);
        log.debug("Updated last used for API key: {}", apiKey.getKeyPrefix());
    }

    public List<ApiKey> getAllKeys() {
        return apiKeyRepository.findAll();
    }

    private String generateSecureKey() {
        StringBuilder sb = new StringBuilder(KEY_LENGTH);
        for (int i = 0; i < KEY_LENGTH; i++) {
            sb.append(CHARACTERS.charAt(secureRandom.nextInt(CHARACTERS.length())));
        }
        return API_KEY_PREFIX + sb.toString();
    }
}