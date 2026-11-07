package com.framebucket.common.phone;

import java.util.regex.Pattern;

public final class IndianMobileNumber {
    private static final Pattern TEN_DIGITS = Pattern.compile("^[0-9]{10}$");
    private static final Pattern WITH_COUNTRY_CODE = Pattern.compile("^\\+91[0-9]{10}$");

    private IndianMobileNumber() {
    }

    public static boolean isValidInput(String value) {
        if (value == null) {
            return false;
        }
        String number = value.trim();
        return TEN_DIGITS.matcher(number).matches() || WITH_COUNTRY_CODE.matcher(number).matches();
    }

    public static String normalize(String value) {
        String number = value == null ? "" : value.trim();
        if (TEN_DIGITS.matcher(number).matches()) {
            return number;
        }
        if (WITH_COUNTRY_CODE.matcher(number).matches()) {
            return number.substring(3);
        }
        throw new IllegalArgumentException("Mobile number must contain 10 digits, optionally prefixed by +91.");
    }
}
