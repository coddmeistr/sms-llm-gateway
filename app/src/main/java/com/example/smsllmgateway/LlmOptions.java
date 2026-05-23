package com.example.smsllmgateway;

/**
 * Per-request LLM options. Immutable POJO, pure Java.
 */
public final class LlmOptions {

    public final int maxTokens;
    public final double temperature;
    public final boolean thinking;
    public final boolean webSearch;

    public LlmOptions(int maxTokens, double temperature, boolean thinking, boolean webSearch) {
        this.maxTokens = maxTokens;
        this.temperature = temperature;
        this.thinking = thinking;
        this.webSearch = webSearch;
    }

    public static LlmOptions defaults() {
        return new LlmOptions(
                GatewayConfig.DEFAULT_TOKENS,
                GatewayConfig.DEFAULT_TEMPERATURE,
                false,
                false);
    }
}
