package com.enterprise.web;

import com.enterprise.currency.CurrencyService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component("currencyUtil")
public class CurrencyUtil {

    private final CurrencyService currencyService;

    public CurrencyUtil(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

    public String format(BigDecimal amount, String currency) {
        return currencyService.format(amount, currency);
    }

    public String symbol(String currency) {
        return currencyService.getSymbol(currency);
    }
}