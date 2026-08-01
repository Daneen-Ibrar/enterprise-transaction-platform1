package com.enterprise.feature;

import com.enterprise.events.SuspicionEnabledEvent;
import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeatureFlagService {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);
    private final FeatureFlagRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public FeatureFlagService(FeatureFlagRepository repository,
                              ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Cacheable(value = "featureFlags", key = "#name")
    public boolean isEnabled(String name) {
        log.debug("Checking feature flag: {}", name);
        return repository.findByName(name)
                .map(FeatureFlag::isEnabled)
                .orElse(false);
    }

    @CacheEvict(value = "featureFlags", key = "#name")
    @Transactional
    public void setEnabled(String name, boolean enabled) {
        FeatureFlag flag = repository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Feature flag not found: " + name));
        flag.setEnabled(enabled);
        flag.setUpdatedAt(LocalDateTime.now());
        repository.save(flag);
        log.info("Feature flag {} set to {}", name, enabled);

        if ("SUSPICION_DETECTION".equals(name) && enabled) {
            log.info("Suspicion detection enabled – publishing re-evaluation event");
            eventPublisher.publishEvent(new SuspicionEnabledEvent());
        }
    }

    @CacheEvict(value = "featureFlags", key = "#name")
    @Transactional
    public FeatureFlag createFlag(String name, String description, boolean enabled) {
        FeatureFlag flag = new FeatureFlag();
        flag.setName(name);
        flag.setDescription(description);
        flag.setEnabled(enabled);
        flag.setUpdatedAt(LocalDateTime.now());
        // ✅ FIX: Set tenant ID – fail closed
        flag.setTenantId(TenantContext.getRequiredTenantId());
        return repository.save(flag);
    }

    @Transactional
    public void deleteFlag(Long id) {
        repository.deleteById(id);
    }

    public List<FeatureFlag> findAll() {
        return repository.findAll();
    }
}