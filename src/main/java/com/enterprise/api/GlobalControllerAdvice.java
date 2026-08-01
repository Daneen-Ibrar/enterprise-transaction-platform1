package com.enterprise.api;

import com.enterprise.currency.CurrencyHelper;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    private final CurrencyHelper currencyHelper;

    public GlobalControllerAdvice(CurrencyHelper currencyHelper) {
        this.currencyHelper = currencyHelper;
    }

    @ModelAttribute("currencyHelper")
    public CurrencyHelper currencyHelper() {
        return currencyHelper;
    }
}