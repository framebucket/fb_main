package com.framebucket.common.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public final class PriceAmountParser {
    private static final Pattern PRICE_VALUE = Pattern.compile("^\\d+(?:\\.\\d{1,2})?$");

    private PriceAmountParser() {
    }

    public static BigDecimal parse(String submittedPrice) {
        if (submittedPrice == null || submittedPrice.isBlank()) {
            return null;
        }
        String normalized = submittedPrice.trim()
                .replaceAll("(?i)^(?:INR|RS\\.?|₹)\\s*", "")
                .replaceAll("(?i)\\s*(?:INR|RS\\.?)$", "")
                .replace(",", "")
                .replace("/-", "")
                .trim();
        if (!PRICE_VALUE.matcher(normalized).matches()) {
            return null;
        }
        try {
            return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
