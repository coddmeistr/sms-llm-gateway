package com.example.smsllmgateway.commands;

/**
 * Result of {@link CommandParser#parse(String)}.
 * Immutable, pure-Java POJO.
 */
public final class ParsedCommand {

    public enum Kind {
        /** Not a command at all; caller should forward the text to the LLM. */
        NOT_A_COMMAND,
        HELP,
        STATUS,
        RESET,
        GET_ALL,
        GET_ONE,
        SET_ONE,
        LIST_PRESETS,
        LIST_MODELS,
        CHAT_LIST,
        CHAT_NEW,
        CHAT_USE,
        CHAT_CLEAR,
        CHAT_DELETE,
        MODEL_LIST,
        MODEL_INFO,
        MODEL_SELECT,
        ALLOWED_LIST,
        ALLOWED_ADD,
        ALLOWED_DEL,
        ALLOWED_CLEAR,
        ALLOWED_SET,
        /** Command verb recognized but arguments are missing/invalid; {@code error} explains it. */
        BAD_USAGE
    }

    public final Kind kind;
    /** Setting key for GET_ONE / SET_ONE (lowercase), or null. */
    public final String key;
    /** Raw value or argument string, or null. */
    public final String value;
    /** Human-readable error message for {@link Kind#BAD_USAGE}, else null. */
    public final String error;

    private ParsedCommand(Kind kind, String key, String value, String error) {
        this.kind = kind;
        this.key = key;
        this.value = value;
        this.error = error;
    }

    static ParsedCommand notCommand() {
        return new ParsedCommand(Kind.NOT_A_COMMAND, null, null, null);
    }

    static ParsedCommand of(Kind kind) {
        return new ParsedCommand(kind, null, null, null);
    }

    static ParsedCommand withValue(Kind kind, String value) {
        return new ParsedCommand(kind, null, value, null);
    }

    static ParsedCommand setOne(String key, String value) {
        return new ParsedCommand(Kind.SET_ONE, key, value, null);
    }

    static ParsedCommand getOne(String key) {
        return new ParsedCommand(Kind.GET_ONE, key, null, null);
    }

    static ParsedCommand modelSelect(String token) {
        return new ParsedCommand(Kind.MODEL_SELECT, "model", token, null);
    }

    static ParsedCommand badUsage(String error) {
        return new ParsedCommand(Kind.BAD_USAGE, null, null, error);
    }
}
