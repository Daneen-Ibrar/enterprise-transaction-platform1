package com.enterprise.api;

import com.enterprise.currency.ExchangeRateService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class CurrencyControllerAdvice {

    private final ExchangeRateService exchangeRateService;

    public CurrencyControllerAdvice(ExchangeRateService exchangeRateService) {
        this.exchangeRateService = exchangeRateService;
    }

    @ModelAttribute("currencyService")
    public ExchangeRateService getCurrencyService() {
        return exchangeRateService;
    }
}