package com.enterprise.money;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, String> {

    private static final String SEPARATOR = "|";

    @Override
    public String convertToDatabaseColumn(Money money) {
        if (money == null) {
            return null;
        }
        return money.getAmount().toPlainString() + SEPARATOR + money.getCurrencyCode();
    }

    @Override
    public Money convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        String[] parts = dbData.split("\\" + SEPARATOR);
        if (parts.length != 2) {
            throw new IllegalStateException("Invalid Money format: " + dbData);
        }
        return new Money(new java.math.BigDecimal(parts[0]), parts[1]);
    }
}