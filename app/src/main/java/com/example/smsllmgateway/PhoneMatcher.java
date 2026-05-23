package com.example.smsllmgateway;

/**
 * Phone-number normalization and whitelist matching. Pure Java.
 *
 * The whitelist accepts entries separated by commas, semicolons or newlines.
 * An entry matches a sender when:
 *   - normalized digits are exactly equal (ignoring a leading "+"), OR
 *   - the entry is a suffix of the sender's number AND the entry has at least
 *     {@link #MIN_SUFFIX_DIGITS} digits.
 *
 * This prevents the old footgun where "123" would whitelist every number ending
 * in 123 (or even containing 123, since the previous check matched both directions).
 */
public final class PhoneMatcher {

    public static final int MIN_SUFFIX_DIGITS = 7;

    private PhoneMatcher() {
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder result = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                result.append(c);
            } else if (c == '+' && result.length() == 0) {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static String digitsOnly(String normalized) {
        if (normalized.startsWith("+")) {
            return normalized.substring(1);
        }
        return normalized;
    }

    public static boolean isAllowed(String sender, String whitelist) {
        if (whitelist == null || whitelist.trim().isEmpty()) {
            return true;
        }
        String normalizedSender = normalize(sender);
        if (normalizedSender.isEmpty()) {
            return false;
        }
        String senderDigits = digitsOnly(normalizedSender);

        String[] entries = whitelist.split("[,;\\n\\r]+");
        for (String entry : entries) {
            String normalizedEntry = normalize(entry);
            if (normalizedEntry.isEmpty()) {
                continue;
            }
            if (normalizedSender.equalsIgnoreCase(normalizedEntry)) {
                return true;
            }
            String entryDigits = digitsOnly(normalizedEntry);
            if (entryDigits.equals(senderDigits)) {
                return true;
            }
            if (entryDigits.length() >= MIN_SUFFIX_DIGITS
                    && senderDigits.endsWith(entryDigits)) {
                return true;
            }
        }
        return false;
    }
}
