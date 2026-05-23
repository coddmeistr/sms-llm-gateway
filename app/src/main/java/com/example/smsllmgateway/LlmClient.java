package com.example.smsllmgateway;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

final class LlmClient {
    private LlmClient() {
    }

    static String requestChatCompletion(
            String endpoint,
            String apiKey,
            String model,
            String systemPrompt,
            JSONArray history,
            String userMessage,
            LlmOptions options) throws Exception {

        LlmOptions opts = options != null ? options : LlmOptions.defaults();

        JSONObject payload = new JSONObject();
        payload.put("model", model);
        payload.put("temperature", opts.temperature);
        payload.put("max_tokens", opts.maxTokens);

        if (opts.thinking) {
            // OpenRouter and several providers honour this. Models that don't support
            // thinking simply ignore the field.
            payload.put("reasoning", new JSONObject().put("enabled", true));
        }
        if (opts.webSearch) {
            // OpenRouter web-search plugin. Works with any model on OpenRouter.
            JSONArray plugins = new JSONArray();
            plugins.put(new JSONObject().put("id", "web"));
            payload.put("plugins", plugins);
        }

        JSONArray messages = new JSONArray();
        messages.put(new JSONObject()
                .put("role", "system")
                .put("content", systemPrompt));
        if (history != null) {
            for (int i = 0; i < history.length(); i++) {
                JSONObject message = history.optJSONObject(i);
                if (message != null) {
                    messages.put(message);
                }
            }
        }
        messages.put(new JSONObject()
                .put("role", "user")
                .put("content", userMessage));
        payload.put("messages", messages);

        byte[] requestBytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(90_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("X-Title", "SMS LLM Gateway");
            connection.setRequestProperty("HTTP-Referer", "https://local.sms-llm-gateway");
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            }

            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBytes);
            }

            int code = connection.getResponseCode();
            String response = readFully(code >= 200 && code < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());

            if (code < 200 || code >= 300) {
                throw new IOException("LLM HTTP " + code + ": " + response);
            }

            return parseAnswer(response);
        } finally {
            connection.disconnect();
        }
    }

    /** Parses an OpenAI-compatible Chat Completions JSON response. Package-private for tests. */
    static String parseAnswer(String response) throws IOException {
        JSONObject json;
        try {
            json = new JSONObject(response);
        } catch (JSONException e) {
            throw new IOException("Invalid LLM response JSON: " + e.getMessage(), e);
        }
        JSONArray choices = json.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IOException("LLM response has no choices: " + response);
        }

        JSONObject choice = choices.optJSONObject(0);
        if (choice == null) {
            throw new IOException("LLM response choice is not an object: " + response);
        }
        String content = extractText(choice).trim();
        if (isEmptyContent(content)) {
            throw new IOException("LLM response is empty");
        }
        return content;
    }

    static String extractText(JSONObject choice) {
        JSONObject message = choice.optJSONObject("message");
        if (message != null) {
            String content = valueToText(message.opt("content"));
            if (!isEmptyContent(content)) {
                return content;
            }

            String reasoning = valueToText(message.opt("reasoning"));
            if (!isEmptyContent(reasoning)) {
                return reasoning;
            }

            String refusal = valueToText(message.opt("refusal"));
            if (!isEmptyContent(refusal)) {
                return refusal;
            }
        }

        String text = valueToText(choice.opt("text"));
        if (!isEmptyContent(text)) {
            return text;
        }

        return "";
    }

    static String valueToText(Object value) {
        if (value == null || value == JSONObject.NULL) {
            return "";
        }

        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    String text = valueToText(((JSONObject) item).opt("text"));
                    if (!isEmptyContent(text)) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }
                        result.append(text);
                    }
                } else {
                    String text = valueToText(item);
                    if (!isEmptyContent(text)) {
                        if (result.length() > 0) {
                            result.append('\n');
                        }
                        result.append(text);
                    }
                }
            }
            return result.toString();
        }

        return String.valueOf(value);
    }

    static boolean isEmptyContent(String content) {
        if (content == null) {
            return true;
        }

        String trimmed = content.trim();
        return trimmed.isEmpty()
                || "null".equalsIgnoreCase(trimmed)
                || "undefined".equalsIgnoreCase(trimmed);
    }

    private static String readFully(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }

        // We don't use readLine() because it discards line terminators, which
        // can corrupt payloads that contain newlines inside JSON string values
        // (and force us to guess separators when re-assembling).
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            int read;
            while ((read = reader.read(buffer)) != -1) {
                result.append(buffer, 0, read);
            }
        }
        return result.toString();
    }
}
