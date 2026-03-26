package dev.pg.support.util;

public final class CardMaskingUtils {

    private CardMaskingUtils() {
    }

    public static String mask(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return "";
        }
        String digits = cardNumber.trim();
        if (digits.length() <= 10) {
            return "*".repeat(digits.length());
        }

        String prefix = digits.substring(0, 6);
        String suffix = digits.substring(digits.length() - 4);
        return prefix + "*".repeat(digits.length() - 10) + suffix;
    }
}
