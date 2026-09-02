package com.enterprise.tax;

import com.enterprise.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
public class TaxService {

    private static final Logger log = LoggerFactory.getLogger(TaxService.class);

    private final TaxRuleRepository taxRuleRepository;
    private final VatLayerService vatLayerService;

    public TaxService(TaxRuleRepository taxRuleRepository,
                      VatLayerService vatLayerService) {
        this.taxRuleRepository = taxRuleRepository;
        this.vatLayerService = vatLayerService;
        log.info("✅ TaxService initialized with VatLayer API integration");
    }

    /**
     * Calculate tax for a given amount and country
     * Uses local tax rules if available, otherwise fetches from live API
     */
    public TaxCalculation calculateTax(BigDecimal amount, String countryCode, boolean isB2B) {
        Long tenantId = TenantContext.getRequiredTenantId();

        if (countryCode == null || countryCode.isEmpty()) {
            log.debug("No country code provided - no tax applied");
            return new TaxCalculation(amount, BigDecimal.ZERO, "No Tax", BigDecimal.ZERO);
        }

        String code = countryCode.toUpperCase();
        log.info("🧮 Calculating tax for {} amount {} in country {}", 
                isB2B ? "B2B" : "B2C", amount, code);

        // ✅ 1. Try local tax rule (admin override)
        Optional<TaxRule> ruleOpt = taxRuleRepository
                .findByTenantIdAndCountryCodeAndIsActiveTrue(tenantId, code);

        if (ruleOpt.isPresent()) {
            TaxRule rule = ruleOpt.get();
            log.info("📋 Found local tax rule for {}: {}% ({})", code, rule.getTaxRate(), rule.getTaxName());

            // Check B2B exemption
            if (isB2B && rule.isB2BExempt()) {
                log.info("✅ B2B exemption applied for country: {}", code);
                return new TaxCalculation(amount, BigDecimal.ZERO, "B2B Exempt", BigDecimal.ZERO);
            }

            BigDecimal taxRate = rule.getTaxRate();
            BigDecimal taxAmount = amount.multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal totalWithTax = amount.add(taxAmount);

            log.info("✅ Tax calculated using local rule for {}: {}% = {}", code, taxRate, taxAmount);
            return new TaxCalculation(totalWithTax, taxAmount, rule.getTaxName(), taxRate);
        }

        // ✅ 2. No local rule - use live API
        log.info("🌐 No local tax rule found for {}, fetching from VatLayer API", code);
        
        try {
            BigDecimal taxRate = vatLayerService.getVatRate(code);
            String taxName = getTaxNameByCountry(code);
            
            log.info("📡 VatLayer returned {}% for {}", taxRate, code);

            // If rate is 0 or null, no tax
            if (taxRate == null || taxRate.compareTo(BigDecimal.ZERO) == 0) {
                log.info("ℹ️ No tax rate found for {} - no tax applied", code);
                return new TaxCalculation(amount, BigDecimal.ZERO, "No Tax", BigDecimal.ZERO);
            }

            BigDecimal taxAmount = amount.multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal totalWithTax = amount.add(taxAmount);

            log.info("✅ Tax calculated using VatLayer API for {}: {}% = {} ({})", 
                    code, taxRate, taxAmount, taxName);
            return new TaxCalculation(totalWithTax, taxAmount, taxName, taxRate);
            
        } catch (Exception e) {
            log.error("❌ VatLayer API failed for {}: {}", code, e.getMessage());
            // Fallback to default rates
            BigDecimal fallbackRate = getFallbackRate(code);
            if (fallbackRate.compareTo(BigDecimal.ZERO) > 0) {
                String taxName = getTaxNameByCountry(code);
                BigDecimal taxAmount = amount.multiply(fallbackRate)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal totalWithTax = amount.add(taxAmount);
                log.warn("⚠️ Using fallback rate {}% for {} due to API failure", fallbackRate, code);
                return new TaxCalculation(totalWithTax, taxAmount, taxName, fallbackRate);
            }
            return new TaxCalculation(amount, BigDecimal.ZERO, "No Tax", BigDecimal.ZERO);
        }
    }

    private String getTaxNameByCountry(String countryCode) {
        return switch (countryCode.toUpperCase()) {
            case "US" -> "Sales Tax";
            case "AU", "NZ" -> "GST";
            case "CA" -> "HST";
            default -> "VAT";
        };
    }

    private BigDecimal getFallbackRate(String countryCode) {
        return switch (countryCode.toUpperCase()) {
            case "GB" -> BigDecimal.valueOf(20.0);
            case "DE" -> BigDecimal.valueOf(19.0);
            case "FR" -> BigDecimal.valueOf(20.0);
            case "IT" -> BigDecimal.valueOf(22.0);
            case "ES" -> BigDecimal.valueOf(21.0);
            case "NL" -> BigDecimal.valueOf(21.0);
            case "BE" -> BigDecimal.valueOf(21.0);
            case "PL" -> BigDecimal.valueOf(23.0);
            case "PT" -> BigDecimal.valueOf(23.0);
            case "IE" -> BigDecimal.valueOf(23.0);
            case "AT" -> BigDecimal.valueOf(20.0);
            case "SE" -> BigDecimal.valueOf(25.0);
            case "FI" -> BigDecimal.valueOf(24.0);
            case "DK" -> BigDecimal.valueOf(25.0);
            case "NO" -> BigDecimal.valueOf(25.0);
            case "CH" -> BigDecimal.valueOf(7.7);
            case "US" -> BigDecimal.ZERO;
            case "AU", "NZ" -> BigDecimal.valueOf(10.0);
            case "CA" -> BigDecimal.valueOf(13.0);
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Tax Calculation result
     */
    public static class TaxCalculation {
        private final BigDecimal totalWithTax;
        private final BigDecimal taxAmount;
        private final String taxName;
        private final BigDecimal taxRate;

        public TaxCalculation(BigDecimal totalWithTax, BigDecimal taxAmount, String taxName, BigDecimal taxRate) {
            this.totalWithTax = totalWithTax;
            this.taxAmount = taxAmount;
            this.taxName = taxName;
            this.taxRate = taxRate;
        }

        public BigDecimal getTotalWithTax() { return totalWithTax; }
        public BigDecimal getTaxAmount() { return taxAmount; }
        public String getTaxName() { return taxName; }
        public BigDecimal getTaxRate() { return taxRate; }

        public BigDecimal getSubtotal() {
            return totalWithTax.subtract(taxAmount);
        }
    }
}