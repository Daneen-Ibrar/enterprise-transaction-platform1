package com.enterprise.currency;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Service
public class ExchangeRateService {

    private final Map<String, BigDecimal> rates = new HashMap<>();

    public ExchangeRateService() {
        // Base currency: GBP (1 GBP = X)
        rates.put("GBP_USD", BigDecimal.valueOf(1.27));
        rates.put("GBP_EUR", BigDecimal.valueOf(1.17));
        rates.put("USD_GBP", BigDecimal.valueOf(0.79));
        rates.put("USD_EUR", BigDecimal.valueOf(0.92));
        rates.put("EUR_GBP", BigDecimal.valueOf(0.85));
        rates.put("EUR_USD", BigDecimal.valueOf(1.09));
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }
        String key = fromCurrency + "_" + toCurrency;
        BigDecimal rate = rates.get(key);
        if (rate == null) {
            String inverseKey = toCurrency + "_" + fromCurrency;
            BigDecimal inverseRate = rates.get(inverseKey);
            if (inverseRate != null && inverseRate.compareTo(BigDecimal.ZERO) != 0) {
                rate = BigDecimal.ONE.divide(inverseRate, 6, RoundingMode.HALF_UP);
            } else {
                return amount;
            }
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    public String getSymbol(String currency) {
        return switch (currency) {
            case "USD" -> "$";
            case "EUR" -> "€";
            default -> "£";
        };
    }
}