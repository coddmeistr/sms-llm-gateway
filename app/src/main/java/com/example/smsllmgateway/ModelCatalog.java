package com.example.smsllmgateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Catalog of suggested LLM models. Pure Java, no Android dependencies.
 * The first three entries are free OpenRouter models, the rest are paid but
 * popular and well-supported via OpenRouter.
 */
public final class ModelCatalog {

    public static final class Model {
        public final String id;
        public final String name;
        public final boolean free;
        public final boolean supportsThinking;

        public Model(String id, String name, boolean free, boolean supportsThinking) {
            this.id = id;
            this.name = name;
            this.free = free;
            this.supportsThinking = supportsThinking;
        }
    }

    private static final List<Model> MODELS;

    static {
        List<Model> models = new ArrayList<>();
        models.add(new Model("openrouter/auto", "Auto (free)", true, false));
        models.add(new Model("meta-llama/llama-3.2-3b-instruct:free", "Llama 3.2 3B (free)", true, false));
        models.add(new Model("google/gemma-3-4b-it:free", "Gemma 3 4B (free)", true, false));
        models.add(new Model("openai/gpt-4o-mini", "GPT-4o mini", false, false));
        models.add(new Model("openai/gpt-4o", "GPT-4o", false, false));
        models.add(new Model("anthropic/claude-3.5-haiku", "Claude 3.5 Haiku", false, false));
        models.add(new Model("anthropic/claude-sonnet-4", "Claude Sonnet 4", false, true));
        models.add(new Model("google/gemini-2.5-flash", "Gemini 2.5 Flash", false, true));
        models.add(new Model("google/gemini-2.5-pro", "Gemini 2.5 Pro", false, true));
        models.add(new Model("deepseek/deepseek-chat", "DeepSeek Chat", false, false));
        MODELS = Collections.unmodifiableList(models);
    }

    private ModelCatalog() {
    }

    public static List<Model> all() {
        return MODELS;
    }

    public static int size() {
        return MODELS.size();
    }

    public static Model byIndex(int oneBasedIndex) {
        int i = oneBasedIndex - 1;
        if (i < 0 || i >= MODELS.size()) {
            return null;
        }
        return MODELS.get(i);
    }

    public static Model byId(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        for (Model model : MODELS) {
            if (model.id.equalsIgnoreCase(trimmed)) {
                return model;
            }
        }
        return null;
    }

    public static int indexOf(String id) {
        if (id == null) {
            return -1;
        }
        String trimmed = id.trim();
        for (int i = 0; i < MODELS.size(); i++) {
            if (MODELS.get(i).id.equalsIgnoreCase(trimmed)) {
                return i + 1;
            }
        }
        return -1;
    }

    /** Short numbered list: "1. Auto (free)\n2. Llama 3.2 3B (free)\n..." */
    public static String numberedList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MODELS.size(); i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(". ").append(MODELS.get(i).name);
        }
        return sb.toString();
    }
}
