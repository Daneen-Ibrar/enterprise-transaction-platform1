package com.enterprise.currency;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

@Service
public class CurrencyService {

    private final ExchangeRateService exchangeRateService;

    public CurrencyService(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    public String format(BigDecimal amount, String currencyCode) {
        Locale locale = switch (currencyCode.toUpperCase()) {
            case "USD" -> Locale.US;
            case "EUR" -> Locale.GERMANY;
            case "GBP" -> Locale.UK;
            default -> Locale.UK; // fallback
        };
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        return format.format(amount);
    }

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        return exchangeRateService.convert(amount, fromCurrency, toCurrency);
    }

    public String getSymbol(String currencyCode) {
        return exchangeRateService.getSymbol(currencyCode);
    }
}