package com.example.smsllmgateway;

final class GatewayConfig {
    static final String PREFS = "gateway_settings";

    static final String KEY_ENABLED = "enabled";
    static final String KEY_ENDPOINT = "endpoint";
    static final String KEY_API_KEY = "api_key";
    static final String KEY_MODEL = "model";
    static final String KEY_SYSTEM_PROMPT = "system_prompt";
    static final String KEY_ALLOWED_SENDERS = "allowed_senders";
    static final String KEY_MAX_REPLY_CHARS = "max_reply_chars";
    static final String KEY_LAST_STATUS = "last_status";

    static final String DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    static final String DEFAULT_MODEL = "openrouter/free";
    static final String DEFAULT_API_KEY = BuildConfig.DEFAULT_OPENROUTER_API_KEY;
    static final String DEFAULT_SYSTEM_PROMPT =
            "You are a helpful assistant. Reply in plain text only. Do not use emoji, markdown, tables, code blocks, or decorative symbols. Keep answers compact because they will be sent by SMS.";
    static final int DEFAULT_MAX_REPLY_CHARS = 700;
    static final int MAX_HISTORY_MESSAGES = 12;

    static final String[] SUGGESTED_MODELS = {
            "openrouter/free",
            "meta-llama/llama-3.2-3b-instruct:free",
            "google/gemma-3-4b-it:free"
    };

    static final String[] SUGGESTED_MODEL_NAMES = {
            "Авто: любая доступная бесплатная модель",
            "Llama 3.2 3B: быстро и коротко",
            "Gemma 3 4B: универсальная бесплатная модель"
    };

    private GatewayConfig() {
    }
}
