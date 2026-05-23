package com.example.smsllmgateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * System-prompt presets. Pure Java, no Android dependencies.
 * The "default" preset is also used as a fallback when an unknown id is requested.
 */
public final class PromptPresets {

    public static final String DEFAULT_ID = "default";

    public static final class Preset {
        public final String id;
        public final String name;
        public final String prompt;

        public Preset(String id, String name, String prompt) {
            this.id = id;
            this.name = name;
            this.prompt = prompt;
        }
    }

    private static final List<Preset> PRESETS;

    static {
        List<Preset> list = new ArrayList<>();
        list.add(new Preset(
                "default",
                "Default",
                "You are a helpful assistant. Reply in plain text only. "
                        + "Keep answers compact and to the point."));
        list.add(new Preset(
                "coder",
                "Coder",
                "You are a senior software engineer. Give precise, technical answers. "
                        + "Show small code fragments as plain text without markdown code fences."));
        list.add(new Preset(
                "brief",
                "Brief",
                "Answer in one or two short sentences. No explanations, no preamble, plain text only."));
        list.add(new Preset(
                "tutor",
                "Tutor",
                "Explain to a curious beginner in simple words. Use short sentences. "
                        + "Give one small example when it helps."));
        list.add(new Preset(
                "translator",
                "Translator",
                "Detect the language of the user message and translate it. "
                        + "If the input is Russian, translate to English. Otherwise translate to Russian. "
                        + "Reply with the translation only, no comments."));
        list.add(new Preset(
                "writer",
                "Writer",
                "Help compose short messages, emails and replies. Keep the tone polite and clear. "
                        + "Reply only with the requested text, no extra commentary."));
        list.add(new Preset(
                "expert",
                "Expert",
                "Give a precise factual answer. No filler, no apologies. "
                        + "If you are not sure, say so in one sentence."));
        PRESETS = Collections.unmodifiableList(list);
    }

    private PromptPresets() {
    }

    public static List<Preset> all() {
        return PRESETS;
    }

    public static Preset byId(String id) {
        if (id != null) {
            String trimmed = id.trim();
            for (Preset preset : PRESETS) {
                if (preset.id.equalsIgnoreCase(trimmed)) {
                    return preset;
                }
            }
        }
        return PRESETS.get(0);
    }

    public static boolean isKnown(String id) {
        if (id == null) {
            return false;
        }
        String trimmed = id.trim();
        for (Preset preset : PRESETS) {
            if (preset.id.equalsIgnoreCase(trimmed)) {
                return true;
            }
        }
        return false;
    }

    public static String idsCsv() {
        StringBuilder sb = new StringBuilder();
        for (Preset preset : PRESETS) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(preset.id);
        }
        return sb.toString();
    }

    /**
     * Build the final system prompt for a request: preset body + SMS-specific rules
     * that force plain text and a length hint that makes the LLM keep the answer short
     * enough to fit a couple of SMS messages.
     *
     * @param presetId  id of the preset to use; unknown ids fall back to "default"
     * @param maxChars  SMS length cap; included as a hint in the prompt
     * @param maxTokens token budget; included as a hint in the prompt
     */
    public static String composeSystemPrompt(String presetId, int maxChars, int maxTokens) {
        Preset preset = byId(presetId);
        int safeChars = Math.max(60, maxChars);
        int wordLimit = Math.max(8, maxTokens / 3);
        return preset.prompt
                + "\n\nSMS rules: plain text only; no emoji; no markdown, code fences, "
                + "tables or decorative symbols; no bullet lists unless strictly necessary. "
                + "Answer in at most " + safeChars + " characters and about " + wordLimit + " words.";
    }
}
