package com.enterprise.apikey;

import com.enterprise.identity.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    @Transactional
    public ApiKey generateApiKey(AppUser user, String name) {
        ApiKey apiKey = new ApiKey();
        apiKey.setKeyValue(UUID.randomUUID().toString().replace("-", ""));
        apiKey.setName(name);
        apiKey.setUser(user);
        apiKey.setActive(true);
        return apiKeyRepository.save(apiKey);
    }

    @Transactional
    public void revokeApiKey(Long keyId) {
        ApiKey key = apiKeyRepository.findById(keyId)
                .orElseThrow(() -> new RuntimeException("API Key not found"));
        key.setActive(false);
        apiKeyRepository.save(key);
    }

    // Use the explicit query method
    public Optional<ApiKey> validateKey(String keyValue) {
        return apiKeyRepository.findActiveByKeyValue(keyValue);
        // fallback to native if needed: return apiKeyRepository.findActiveByKeyValueNative(keyValue);
    }

    @Transactional
    public void updateLastUsed(ApiKey apiKey) {
        apiKey.setLastUsedAt(LocalDateTime.now());
        apiKeyRepository.save(apiKey);
    }

    public List<ApiKey> getAllKeys() {
        return apiKeyRepository.findAll();
    }
}