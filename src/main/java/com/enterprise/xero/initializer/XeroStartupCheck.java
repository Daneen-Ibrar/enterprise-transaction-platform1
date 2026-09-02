package com.enterprise.xero.initializer;

import com.enterprise.feature.FeatureFlagService;
import com.enterprise.tenant.Tenant;
import com.enterprise.tenant.TenantContext;
import com.enterprise.tenant.TenantRepository;
import com.enterprise.xero.service.XeroOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Order(1)  // Run early during startup
public class XeroStartupCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(XeroStartupCheck.class);

    private final XeroOAuthService xeroOAuthService;
    private final FeatureFlagService featureFlagService;
    private final TenantRepository tenantRepository;

    public XeroStartupCheck(XeroOAuthService xeroOAuthService,
                            FeatureFlagService featureFlagService,
                            TenantRepository tenantRepository) {
        this.xeroOAuthService = xeroOAuthService;
        this.featureFlagService = featureFlagService;
        this.tenantRepository = tenantRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("🔍 Starting Xero connection check...");

        try {
            // Get all tenants
            List<Tenant> tenants = tenantRepository.findAll();
            
            if (tenants.isEmpty()) {
                log.warn("⚠️ No tenants found, skipping Xero startup check");
                return;
            }

            boolean anyXeroConnected = false;

            for (Tenant tenant : tenants) {
                TenantContext.setTenantId(tenant.getId());
                try {
                    boolean isConnected = xeroOAuthService.isConnected(tenant.getId());
                    
                    if (isConnected) {
                        anyXeroConnected = true;
                        log.info("✅ Tenant {} is connected to Xero", tenant.getId());
                    } else {
                        // Auto-disable Xero auto-sync if not connected
                        log.info("🔌 Tenant {} is NOT connected to Xero - disabling auto-sync", tenant.getId());
                        try {
                            featureFlagService.setEnabled("XERO_AUTO_SYNC", false);
                            log.info("✅ XERO_AUTO_SYNC disabled for tenant {}", tenant.getId());
                        } catch (Exception e) {
                            log.warn("⚠️ Could not disable XERO_AUTO_SYNC for tenant {}: {}", tenant.getId(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Could not check Xero connection for tenant {}: {}", tenant.getId(), e.getMessage());
                    // On error, disable Xero auto-sync to be safe
                    try {
                        featureFlagService.setEnabled("XERO_AUTO_SYNC", false);
                    } catch (Exception ex) {
                        // Ignore
                    }
                } finally {
                    TenantContext.clear();
                }
            }

            if (anyXeroConnected) {
                log.info("✅ Xero is connected for at least one tenant - Xero features available");
            } else {
                log.info("ℹ️ Xero is NOT connected for any tenant - Xero auto-sync disabled globally");
            }

        } catch (Exception e) {
            log.error("❌ Error during Xero startup check: {}", e.getMessage());
        }
    }
}