package com.enterprise.money;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * Immutable value object representing a monetary amount with a currency.
 * Always uses HALF_UP rounding with 2 decimal places for GBP.
 */
public final class Money implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_SCALE = 2;
    private static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    private final BigDecimal amount;
    private final String currencyCode;

    public Money(BigDecimal amount, String currencyCode) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        if (currencyCode == null || currencyCode.isEmpty()) {
            throw new IllegalArgumentException("Currency code cannot be null or empty");
        }
        if (!isValidCurrency(currencyCode)) {
            throw new IllegalArgumentException("Invalid currency code: " + currencyCode);
        }
        this.amount = amount.setScale(DEFAULT_SCALE, DEFAULT_ROUNDING);
        this.currencyCode = currencyCode.toUpperCase();
    }

    public static Money of(String amount, String currencyCode) {
        return new Money(new BigDecimal(amount), currencyCode);
    }

    public static Money of(long amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), currencyCode);
    }

    public static Money of(double amount, String currencyCode) {
        return new Money(BigDecimal.valueOf(amount), currencyCode);
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public Money add(Money other) {
        if (!currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Cannot add different currencies: " +
                    currencyCode + " and " + other.currencyCode);
        }
        return new Money(amount.add(other.amount), currencyCode);
    }

    public Money subtract(Money other) {
        if (!currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Cannot subtract different currencies: " +
                    currencyCode + " and " + other.currencyCode);
        }
        return new Money(amount.subtract(other.amount), currencyCode);
    }

    public Money multiply(BigDecimal multiplier) {
        return new Money(amount.multiply(multiplier), currencyCode);
    }

    public Money negate() {
        return new Money(amount.negate(), currencyCode);
    }

    public int compareTo(Money other) {
        if (!currencyCode.equals(other.currencyCode)) {
            throw new IllegalArgumentException("Cannot compare different currencies: " +
                    currencyCode + " and " + other.currencyCode);
        }
        return amount.compareTo(other.amount);
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    public boolean isNegative() {
        return amount.signum() < 0;
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Money money = (Money) o;
        return Objects.equals(amount, money.amount) &&
                Objects.equals(currencyCode, money.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, currencyCode);
    }

    @Override
    public String toString() {
        return currencyCode + " " + amount.toPlainString();
    }

    public String toFormattedString() {
        java.text.NumberFormat nf = java.text.NumberFormat.getCurrencyInstance();
        try {
            java.util.Currency currency = java.util.Currency.getInstance(currencyCode);
            nf.setCurrency(currency);
        } catch (IllegalArgumentException e) {
            // If currency is not supported, just use symbol
        }
        nf.setMaximumFractionDigits(DEFAULT_SCALE);
        nf.setMinimumFractionDigits(DEFAULT_SCALE);
        return nf.format(amount.doubleValue());
    }

    private static boolean isValidCurrency(String currencyCode) {
        try {
            java.util.Currency.getInstance(currencyCode);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}