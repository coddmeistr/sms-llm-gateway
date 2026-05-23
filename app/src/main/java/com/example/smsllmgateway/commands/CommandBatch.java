package com.example.smsllmgateway.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits an SMS body into one or more command segments separated by
 * {@link #DELIMITER}.
 *
 * <p>The delimiter is the shell-style {@code &&}. It was chosen because:
 * <ul>
 *   <li>{@code ;} already serves as a CSV separator inside arguments such as
 *       {@code ALLOWED SET +79121234567;+79122223344};</li>
 *   <li>{@code |} is reserved by the chat-list storage format;</li>
 *   <li>{@code &&} is universally recognised, easy to type, and extremely
 *       unlikely to appear inside any legitimate command argument that this
 *       gateway accepts (phone numbers, model ids, preset names, etc.).</li>
 * </ul>
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Whitespace around each segment is trimmed.</li>
 *   <li>Empty segments are dropped, so {@code &&STATUS&&HELP&&} yields
 *       {@code ["STATUS", "HELP"]}.</li>
 *   <li>If the body contains no delimiter the original (trimmed) string is
 *       returned as a single-element array, so callers can use the same code
 *       path for one or many segments.</li>
 *   <li>{@code null} or whitespace-only input yields an empty array.</li>
 * </ul>
 *
 * <p>Pure Java, no Android dependencies; fully unit-testable.
 */
public final class CommandBatch {

    public static final String DELIMITER = "&&";

    // "&" has no special meaning in regex, so the literal pattern is safe.
    private static final Pattern SPLIT_PATTERN = Pattern.compile("&&");

    private CommandBatch() {
    }

    /**
     * Returns true when the body contains the delimiter token, regardless of
     * whether the split would yield 0, 1 or more useful segments. Caller can
     * use this to distinguish "user typed batch syntax but it was empty" from
     * "user typed a normal single command".
     */
    public static boolean isBatch(String body) {
        return body != null && body.contains(DELIMITER);
    }

    /**
     * Splits {@code body} into trimmed, non-empty segments.
     * Returns an empty array for {@code null}, empty, whitespace-only, or
     * delimiter-only input.
     */
    public static String[] split(String body) {
        if (body == null) {
            return new String[0];
        }
        if (body.indexOf('&') < 0) {
            String trimmed = body.trim();
            if (trimmed.isEmpty()) {
                return new String[0];
            }
            return new String[]{trimmed};
        }

        // limit=-1 keeps trailing empty strings so we can detect and drop them
        // ourselves; the alternative (default limit=0) silently swallows them
        // and we'd miscount when the SMS ends with the delimiter.
        String[] raw = SPLIT_PATTERN.split(body, -1);
        List<String> kept = new ArrayList<>(raw.length);
        for (String s : raw) {
            String trimmed = s == null ? "" : s.trim();
            if (!trimmed.isEmpty()) {
                kept.add(trimmed);
            }
        }
        return kept.toArray(new String[0]);
    }
}
