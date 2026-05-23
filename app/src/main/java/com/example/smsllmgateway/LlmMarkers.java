package com.example.smsllmgateway;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects inline override markers in an incoming SMS body and returns the
 * cleaned text alongside boolean flags.
 *
 * <p>Supported markers (case-insensitive, may appear anywhere in the body):
 * <ul>
 *   <li>{@code [WEB]} — request web-search for this single LLM call,
 *       even if the per-sender setting is off.</li>
 *   <li>{@code [THINK]} — request thinking/reasoning for this single LLM call,
 *       even if the per-sender setting is off.</li>
 * </ul>
 *
 * <p>The override is one-shot and additive: a marker can only <em>enable</em>
 * an option, never disable one that the user already turned on in their
 * settings.
 *
 * <p>Pure Java, no Android dependencies; intended to be fully unit-testable.
 */
public final class LlmMarkers {

    // We match the literal bracketed tokens. The keyword inside is case-insensitive
    // and may have a small amount of leading/trailing whitespace inside the brackets
    // (e.g. "[ web ]") to be tolerant of user formatting. Markers may appear
    // anywhere in the body and any number of times.
    private static final Pattern WEB_MARKER = Pattern.compile(
            "\\[\\s*WEB\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern THINK_MARKER = Pattern.compile(
            "\\[\\s*THINK\\s*\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("[ \\t]{2,}");

    public final String cleanedBody;
    public final boolean forceWeb;
    public final boolean forceThinking;

    private LlmMarkers(String cleanedBody, boolean forceWeb, boolean forceThinking) {
        this.cleanedBody = cleanedBody;
        this.forceWeb = forceWeb;
        this.forceThinking = forceThinking;
    }

    public static LlmMarkers extract(String body) {
        if (body == null || body.isEmpty()) {
            return new LlmMarkers("", false, false);
        }

        boolean web = WEB_MARKER.matcher(body).find();
        boolean think = THINK_MARKER.matcher(body).find();

        if (!web && !think) {
            return new LlmMarkers(body, false, false);
        }

        String cleaned = body;
        if (web) {
            cleaned = replaceAll(WEB_MARKER, cleaned);
        }
        if (think) {
            cleaned = replaceAll(THINK_MARKER, cleaned);
        }

        // Collapse internal double-spaces and trim. We do not collapse newlines
        // because they may carry meaning in user prompts.
        cleaned = MULTI_WHITESPACE.matcher(cleaned).replaceAll(" ").trim();

        return new LlmMarkers(cleaned, web, think);
    }

    /** Returns true when stripping markers left nothing useful for the LLM. */
    public boolean isEmpty() {
        return cleanedBody.isEmpty();
    }

    private static String replaceAll(Pattern pattern, String input) {
        Matcher m = pattern.matcher(input);
        StringBuilder sb = new StringBuilder(input.length());
        int last = 0;
        while (m.find()) {
            sb.append(input, last, m.start());
            // Inject a single space so we don't accidentally glue neighbouring
            // words together (e.g. "foo[WEB]bar" -> "foo bar", not "foobar").
            sb.append(' ');
            last = m.end();
        }
        sb.append(input, last, input.length());
        return sb.toString();
    }
}
