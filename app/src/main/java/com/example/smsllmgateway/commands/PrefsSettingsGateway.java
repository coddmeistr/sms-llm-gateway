package com.example.smsllmgateway.commands;

import android.content.SharedPreferences;

import com.example.smsllmgateway.ConversationStore;
import com.example.smsllmgateway.GatewayConfig;
import com.example.smsllmgateway.PromptPresets;

import java.util.List;

/**
 * Production {@link SettingsGateway} implementation that reads/writes to
 * the gateway's {@link SharedPreferences} (global) and a
 * {@link ConversationStore} (per-sender + chats).
 */
public final class PrefsSettingsGateway implements SettingsGateway {

    private final SharedPreferences prefs;
    private final ConversationStore conversations;

    public PrefsSettingsGateway(SharedPreferences prefs, ConversationStore conversations) {
        this.prefs = prefs;
        this.conversations = conversations;
    }

    @Override
    public String getPreset() {
        String preset = prefs.getString(GatewayConfig.KEY_PRESET, GatewayConfig.DEFAULT_PRESET);
        return PromptPresets.isKnown(preset) ? preset : GatewayConfig.DEFAULT_PRESET;
    }

    @Override
    public void setPreset(String presetId) {
        prefs.edit().putString(GatewayConfig.KEY_PRESET, presetId).apply();
    }

    @Override
    public int getReplyChars() {
        return prefs.getInt(
                GatewayConfig.KEY_MAX_REPLY_CHARS,
                GatewayConfig.DEFAULT_MAX_REPLY_CHARS);
    }

    @Override
    public void setReplyChars(int chars) {
        int clamped = Math.max(
                GatewayConfig.MIN_REPLY_CHARS,
                Math.min(GatewayConfig.MAX_REPLY_CHARS_LIMIT, chars));
        prefs.edit().putInt(GatewayConfig.KEY_MAX_REPLY_CHARS, clamped).apply();
    }

    @Override
    public String getEndpoint() {
        String value = prefs.getString(GatewayConfig.KEY_ENDPOINT, GatewayConfig.DEFAULT_ENDPOINT);
        return value == null || value.trim().isEmpty() ? GatewayConfig.DEFAULT_ENDPOINT : value.trim();
    }

    @Override
    public String getApiKey() {
        String key = prefs.getString(GatewayConfig.KEY_API_KEY, GatewayConfig.DEFAULT_API_KEY);
        if (key == null || key.trim().isEmpty()) {
            return GatewayConfig.DEFAULT_API_KEY;
        }
        return key.trim();
    }

    @Override
    public String getAllowedSenders() {
        return prefs.getString(GatewayConfig.KEY_ALLOWED_SENDERS, "");
    }

    @Override
    public void setAllowedSenders(String csv) {
        prefs.edit()
                .putString(GatewayConfig.KEY_ALLOWED_SENDERS, csv == null ? "" : csv)
                .apply();
    }

    @Override
    public String getDefaultModel() {
        String value = prefs.getString(GatewayConfig.KEY_MODEL, GatewayConfig.DEFAULT_MODEL);
        return value == null || value.trim().isEmpty() ? GatewayConfig.DEFAULT_MODEL : value.trim();
    }

    @Override
    public String getModel(String sender) {
        return conversations.getModel(sender, getDefaultModel());
    }

    @Override
    public void setModel(String sender, String modelId) {
        conversations.setModel(sender, modelId);
    }

    @Override
    public int getTokens(String sender) {
        return conversations.getTokens(sender);
    }

    @Override
    public void setTokens(String sender, int tokens) {
        conversations.setTokens(sender, tokens);
    }

    @Override
    public double getTemperature(String sender) {
        return conversations.getTemperature(sender);
    }

    @Override
    public void setTemperature(String sender, double temperature) {
        conversations.setTemperature(sender, temperature);
    }

    @Override
    public boolean isThinking(String sender) {
        return conversations.getThinking(sender);
    }

    @Override
    public void setThinking(String sender, boolean enabled) {
        conversations.setThinking(sender, enabled);
    }

    @Override
    public boolean isWebSearch(String sender) {
        return conversations.getWebSearch(sender);
    }

    @Override
    public void setWebSearch(String sender, boolean enabled) {
        conversations.setWebSearch(sender, enabled);
    }

    @Override
    public void resetSender(String sender) {
        conversations.resetSenderSettings(sender);
    }

    @Override
    public String getActiveChat(String sender) {
        return conversations.getActiveChat(sender);
    }

    @Override
    public void setActiveChat(String sender, String chatName) {
        conversations.setActiveChat(sender, chatName);
    }

    @Override
    public List<String> listChats(String sender) {
        return conversations.listChats(sender);
    }

    @Override
    public void clearActiveChat(String sender) {
        conversations.clearChat(sender, conversations.getActiveChat(sender));
    }

    @Override
    public boolean deleteChat(String sender, String chatName) {
        return conversations.deleteChat(sender, chatName);
    }
}
