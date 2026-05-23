package com.example.smsllmgateway;

/**
 * Sanitizes LLM text responses for SMS delivery: strips markdown markers,
 * removes emoji and decorative pictographs, normalizes whitespace, and
 * truncates safely without breaking UTF-16 surrogate pairs.
 *
 * Pure Java, no Android dependencies.
 */
public final class TextSanitizer {

    private static final String TRUNCATION_SUFFIX = "\n\n[Ответ обрезан]";

    private TextSanitizer() {
    }

    /** Drops markdown markers and emoji-like pictographs, normalizes whitespace. */
    public static String cleanPlainText(String value) {
        if (value == null) {
            return "";
        }

        String cleaned = value
                .replace("```", "")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("(?m)^>\\s*", "")
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();

        StringBuilder result = new StringBuilder(cleaned.length());
        for (int offset = 0; offset < cleaned.length(); ) {
            int codePoint = cleaned.codePointAt(offset);
            offset += Character.charCount(codePoint);

            if (isStrippable(codePoint)) {
                continue;
            }
            result.appendCodePoint(codePoint);
        }

        return result.toString()
                .replaceAll("[\\t ]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    /**
     * Truncates a string to at most {@code maxChars} UTF-16 code units, appending
     * a Russian "[Ответ обрезан]" marker. Will not split a surrogate pair.
     * When {@code maxChars <= 0} or value already fits, the original string is returned.
     */
    public static String limit(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        if (maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }

        int end = maxChars;
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end -= 1;
        }
        return value.substring(0, end) + TRUNCATION_SUFFIX;
    }

    private static boolean isStrippable(int codePoint) {
        int type = Character.getType(codePoint);
        if (type == Character.SURROGATE
                || type == Character.PRIVATE_USE
                || type == Character.UNASSIGNED
                || type == Character.CONTROL && codePoint != '\n' && codePoint != '\r') {
            return true;
        }
        if (codePoint == 0x200D || codePoint == 0xFE0F) {
            // zero-width joiner and variation selector-16 used in emoji sequences
            return true;
        }
        return isEmojiCodePoint(codePoint);
    }

    /**
     * Conservative emoji detection: only drops code points from known emoji-heavy
     * Unicode blocks. Plain useful symbols like '°', '№', '™', currency signs are kept.
     */
    private static boolean isEmojiCodePoint(int cp) {
        // Misc symbols and pictographs, dingbats etc.
        if (cp >= 0x2600 && cp <= 0x27BF) {
            return true;
        }
        // Regional indicator symbols (country flags)
        if (cp >= 0x1F1E6 && cp <= 0x1F1FF) {
            return true;
        }
        // Misc Symbols and Pictographs, Emoticons, Transport, Supplemental, etc.
        if (cp >= 0x1F300 && cp <= 0x1FAFF) {
            return true;
        }
        return false;
    }
}
