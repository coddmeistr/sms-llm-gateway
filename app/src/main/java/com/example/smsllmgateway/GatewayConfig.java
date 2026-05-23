package com.example.smsllmgateway;

public final class GatewayConfig {
    public static final String PREFS = "gateway_settings";

    // Global preferences
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_ENDPOINT = "endpoint";
    public static final String KEY_API_KEY = "api_key";
    public static final String KEY_MODEL = "model";
    public static final String KEY_SYSTEM_PROMPT = "system_prompt";
    public static final String KEY_PRESET = "preset";
    public static final String KEY_ALLOWED_SENDERS = "allowed_senders";
    public static final String KEY_MAX_REPLY_CHARS = "max_reply_chars";
    public static final String KEY_LAST_STATUS = "last_status";

    // Per-sender preferences (stored via ConversationStore)
    public static final String SENDER_KEY_MODEL = "model";
    public static final String SENDER_KEY_TOKENS = "tokens";
    public static final String SENDER_KEY_TEMP = "temp";
    public static final String SENDER_KEY_THINK = "think";
    public static final String SENDER_KEY_WEB = "web";

    public static final String DEFAULT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions";
    public static final String DEFAULT_MODEL = "openrouter/auto";
    public static final String DEFAULT_API_KEY = BuildConfig.DEFAULT_OPENROUTER_API_KEY;
    public static final String DEFAULT_PRESET = "default";
    public static final int DEFAULT_MAX_REPLY_CHARS = 700;
    public static final int MAX_HISTORY_MESSAGES = 12;

    public static final int DEFAULT_TOKENS = 220;
    public static final int MIN_TOKENS = 50;
    public static final int MAX_TOKENS = 2000;

    public static final double DEFAULT_TEMPERATURE = 0.4;
    public static final double MIN_TEMPERATURE = 0.0;
    public static final double MAX_TEMPERATURE = 2.0;

    public static final int MIN_REPLY_CHARS = 100;
    public static final int MAX_REPLY_CHARS_LIMIT = 1500;

    private GatewayConfig() {
    }
}
