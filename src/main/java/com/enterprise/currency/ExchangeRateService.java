package com.enterprise.currency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final int SCALE = 6;

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateService(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Cacheable(value = "exchangeRates", key = "#fromCurrency + ':' + #toCurrency")
    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }

        Optional<ExchangeRate> rateOpt = exchangeRateRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
        if (rateOpt.isPresent()) {
            BigDecimal rate = rateOpt.get().getRate();
            return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
        }

        Optional<ExchangeRate> inverseOpt = exchangeRateRepository.findByFromCurrencyAndToCurrency(toCurrency, fromCurrency);
        if (inverseOpt.isPresent()) {
            BigDecimal inverseRate = inverseOpt.get().getRate();
            if (inverseRate.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal rate = BigDecimal.ONE.divide(inverseRate, SCALE, RoundingMode.HALF_UP);
                return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            }
        }

        log.warn("No exchange rate found for {} -> {}, returning original amount", fromCurrency, toCurrency);
        return amount;
    }

    @CacheEvict(value = "exchangeRates", allEntries = true)
    @Transactional
    public void updateRate(String fromCurrency, String toCurrency, BigDecimal newRate) {
        if (fromCurrency == null || toCurrency == null || newRate == null || newRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid rate parameters");
        }

        saveRateInternal(fromCurrency, toCurrency, newRate);

        BigDecimal inverseRate = BigDecimal.ONE.divide(newRate, SCALE, RoundingMode.HALF_UP);
        saveRateInternal(toCurrency, fromCurrency, inverseRate);

        log.info("Updated exchange rate {} -> {} = {} and inverse {} -> {} = {}",
                fromCurrency, toCurrency, newRate, toCurrency, fromCurrency, inverseRate);
    }

    private void saveRateInternal(String fromCurrency, String toCurrency, BigDecimal rate) {
        ExchangeRate exchangeRate = exchangeRateRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency)
                .orElse(new ExchangeRate(fromCurrency, toCurrency, rate));
        exchangeRate.setRate(rate);
        exchangeRate.setUpdatedAt(LocalDateTime.now());
        exchangeRateRepository.save(exchangeRate);
    }

    @CacheEvict(value = "exchangeRates", allEntries = true)
    @Transactional
    public void deleteRate(Long id) {
        ExchangeRate rate = exchangeRateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Rate not found"));
        exchangeRateRepository.findByFromCurrencyAndToCurrency(rate.getToCurrency(), rate.getFromCurrency())
                .ifPresent(inverse -> exchangeRateRepository.delete(inverse));
        exchangeRateRepository.delete(rate);
        log.info("Deleted exchange rate id {} and its inverse", id);
    }

    public List<ExchangeRate> getAllRates() {
        return exchangeRateRepository.findAll();
    }

    public Optional<ExchangeRate> getRate(Long id) {
        return exchangeRateRepository.findById(id);
    }

    /**
     * Returns the currency symbol for a given ISO code.
     * Uses the comprehensive CurrencySymbols utility.
     */
    public String getSymbol(String currencyCode) {
        return CurrencySymbols.getSymbol(currencyCode);
    }
}