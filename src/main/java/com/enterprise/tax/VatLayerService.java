package com.enterprise.tax;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VatLayerService {

    private static final Logger log = LoggerFactory.getLogger(VatLayerService.class);

    @Value("${vatlayer.api-key:b1f7e06a189a7383c8abef5908f032c8}")
    private String apiKey;

    @Value("${vatlayer.base-url:http://apilayer.net/api}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private Map<String, VatRate> vatRates = new HashMap<>();
    private Map<String, BigDecimal> countryTaxRates = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            log.info("🔄 Loading tax rates from VatLayer API...");
            vatRates = fetchLiveRates();
            log.info("✅ Loaded {} tax rates from VatLayer API", vatRates.size());
        } catch (Exception e) {
            log.warn("⚠️ Using default VAT rates - API fetch failed: {}", e.getMessage());
            vatRates = getDefaultRates();
        }
        // Log loaded rates for debugging
        for (Map.Entry<String, VatRate> entry : vatRates.entrySet()) {
            log.info("   {} -> {}%", entry.getKey(), entry.getValue().getStandardRate());
        }
    }

    /**
     * Fetch live VAT rates from VatLayer API
     */
    public Map<String, VatRate> fetchLiveRates() {
        // Check if API key is valid (not the default placeholder)
        if (apiKey == null || apiKey.isEmpty() ) {
            log.warn("⚠️ No valid VatLayer API key configured. Using default rates.");
            log.warn("   To use live rates, get a free API key from: https://vatlayer.com/");
            return getDefaultRates();
        }

        try {
            String url = baseUrl + "/rates?access_key=" + apiKey;
            log.info("📡 Fetching live VAT rates from: {}", url.replace(apiKey, "****"));
            String response = restTemplate.getForObject(url, String.class);
            
            VatLayerResponse vatResponse = objectMapper.readValue(response, VatLayerResponse.class);

            if (vatResponse.isSuccess()) {
                Map<String, VatRate> rates = new HashMap<>();
                for (Map.Entry<String, VatRate> entry : vatResponse.getRates().entrySet()) {
                    String countryCode = entry.getKey();
                    VatRate rate = entry.getValue();
                    rate.setCountryCode(countryCode);

                    // Calculate standard rate from periods
                    if (rate.getPeriods() != null && !rate.getPeriods().isEmpty()) {
                        double avgRate = rate.getPeriods().stream()
                                .mapToDouble(VatPeriod::getRate)
                                .average()
                                .orElse(0.0);
                        rate.setStandardRate(BigDecimal.valueOf(avgRate).setScale(2, RoundingMode.HALF_UP));
                        countryTaxRates.put(countryCode, BigDecimal.valueOf(avgRate).setScale(2, RoundingMode.HALF_UP));
                    }
                    rates.put(countryCode, rate);
                }
                this.vatRates = rates;
                log.info("✅ Fetched {} live VAT rates from VatLayer", rates.size());
                
                // Log the rates for debugging
                for (Map.Entry<String, VatRate> entry : rates.entrySet()) {
                    log.debug("   {} -> {}%", entry.getKey(), entry.getValue().getStandardRate());
                }
                
                return rates;
            } else {
                String errorMsg = vatResponse.getError() != null ? vatResponse.getError().getInfo() : "Unknown error";
                log.warn("⚠️ VatLayer API returned error: {}", errorMsg);
                return getDefaultRates();
            }

        } catch (Exception e) {
            log.error("❌ Failed to fetch VAT rates from VatLayer: {}", e.getMessage());
            return getDefaultRates();
        }
    }

    /**
     * Get standard VAT rate for a country
     */
    public BigDecimal getVatRate(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) {
            return BigDecimal.ZERO;
        }

        String code = countryCode.toUpperCase();

        // Check cached rates from API
        if (countryTaxRates.containsKey(code)) {
            return countryTaxRates.get(code);
        }

        // Check vatRates
        if (vatRates.containsKey(code)) {
            VatRate rate = vatRates.get(code);
            if (rate.getStandardRate() != null) {
                return rate.getStandardRate();
            }
            if (rate.getPeriods() != null && !rate.getPeriods().isEmpty()) {
                double avg = rate.getPeriods().stream()
                        .mapToDouble(VatPeriod::getRate)
                        .average()
                        .orElse(0.0);
                BigDecimal result = BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
                countryTaxRates.put(code, result);
                return result;
            }
        }

        // Fallback to default rates
        BigDecimal defaultRate = getDefaultRate(code);
        countryTaxRates.put(code, defaultRate);
        return defaultRate;
    }

    /**
     * Get country name from code
     */
    public String getCountryName(String countryCode) {
        if (countryCode == null) return "Unknown";
        String code = countryCode.toUpperCase();
        if (vatRates.containsKey(code)) {
            VatRate rate = vatRates.get(code);
            if (rate.getName() != null && rate.getName().containsKey("en")) {
                return rate.getName().get("en");
            }
        }
        return code;
    }

    private BigDecimal getDefaultRate(String countryCode) {
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
            case "US" -> BigDecimal.valueOf(0.0);
            case "AU" -> BigDecimal.valueOf(10.0);
            case "NZ" -> BigDecimal.valueOf(15.0);
            case "CA" -> BigDecimal.valueOf(13.0);
            default -> BigDecimal.ZERO;
        };
    }

    private Map<String, VatRate> getDefaultRates() {
        Map<String, VatRate> rates = new HashMap<>();
        String[] countries = {"GB", "DE", "FR", "IT", "ES", "NL", "BE", "PL", "PT", "IE",
                "AT", "SE", "FI", "DK", "NO", "CH", "US", "AU", "NZ", "CA"};

        for (String code : countries) {
            VatRate rate = new VatRate();
            rate.setCountryCode(code);
            rate.setStandardRate(getDefaultRate(code));
            rates.put(code, rate);
            countryTaxRates.put(code, getDefaultRate(code));
        }
        return rates;
    }

    // ============================================================
    // DTO Classes
    // ============================================================

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VatLayerResponse {
        private boolean success;
        private Map<String, VatRate> rates;
        private VatError error;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public Map<String, VatRate> getRates() { return rates; }
        public void setRates(Map<String, VatRate> rates) { this.rates = rates; }
        public VatError getError() { return error; }
        public void setError(VatError error) { this.error = error; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VatError {
        private String code;
        private String info;

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getInfo() { return info; }
        public void setInfo(String info) { this.info = info; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VatRate {
        private String countryCode;
        private String countryName;
        private BigDecimal standardRate;
        private Map<String, String> name;
        private List<VatPeriod> periods;

        public String getCountryCode() { return countryCode; }
        public void setCountryCode(String countryCode) { this.countryCode = countryCode; }
        public String getCountryName() { return countryName; }
        public void setCountryName(String countryName) { this.countryName = countryName; }
        public BigDecimal getStandardRate() { return standardRate; }
        public void setStandardRate(BigDecimal standardRate) { this.standardRate = standardRate; }
        public Map<String, String> getName() { return name; }
        public void setName(Map<String, String> name) { this.name = name; }
        public List<VatPeriod> getPeriods() { return periods; }
        public void setPeriods(List<VatPeriod> periods) { this.periods = periods; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VatPeriod {
        private String effectiveFrom;
        private String effectiveTo;
        private double rate;

        public String getEffectiveFrom() { return effectiveFrom; }
        public void setEffectiveFrom(String effectiveFrom) { this.effectiveFrom = effectiveFrom; }
        public String getEffectiveTo() { return effectiveTo; }
        public void setEffectiveTo(String effectiveTo) { this.effectiveTo = effectiveTo; }
        public double getRate() { return rate; }
        public void setRate(double rate) { this.rate = rate; }
    }
}