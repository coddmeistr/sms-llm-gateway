package com.example.smsllmgateway.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parses SMS bodies into {@link ParsedCommand}s.
 *
 * Design goals: forgiving for users typing on T9 keypads, but never grabs random
 * messages by accident. A body is only treated as a command when its first token
 * is one of the recognized verbs (case-insensitive). Anything else becomes
 * {@link ParsedCommand.Kind#NOT_A_COMMAND} so the caller can forward it to the LLM.
 *
 * Allowed extras:
 *   - leading "/" or "!" (e.g. "/PRESET coder")
 *   - whitespace, "=" or ":" as separators ("SET preset=coder", "PRESET:coder")
 *   - case-insensitive verbs and Russian alias for HELP ("Помощь")
 */
public final class CommandParser {

    private static final Set<String> VERBS;
    static {
        Set<String> verbs = new HashSet<>(Arrays.asList(
                "HELP", "STATUS", "RESET",
                "GET", "SET", "LIST",
                "CHAT", "MODEL", "ALLOWED",
                "PRESET", "TOKENS", "THINK", "WEB", "TEMP", "CHARS"));
        VERBS = Collections.unmodifiableSet(verbs);
    }

    private static final Set<String> ON_VALUES;
    private static final Set<String> OFF_VALUES;
    static {
        Set<String> on = new HashSet<>(Arrays.asList("on", "1", "yes", "y", "true", "+", "вкл", "да"));
        Set<String> off = new HashSet<>(Arrays.asList("off", "0", "no", "n", "false", "-", "выкл", "нет"));
        ON_VALUES = Collections.unmodifiableSet(on);
        OFF_VALUES = Collections.unmodifiableSet(off);
    }

    private CommandParser() {
    }

    public static boolean isOnValue(String token) {
        return token != null && ON_VALUES.contains(token.trim().toLowerCase(Locale.US));
    }

    public static boolean isOffValue(String token) {
        return token != null && OFF_VALUES.contains(token.trim().toLowerCase(Locale.US));
    }

    public static ParsedCommand parse(String body) {
        if (body == null) {
            return ParsedCommand.notCommand();
        }
        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return ParsedCommand.notCommand();
        }
        if (trimmed.charAt(0) == '/' || trimmed.charAt(0) == '!') {
            trimmed = trimmed.substring(1).trim();
            if (trimmed.isEmpty()) {
                return ParsedCommand.notCommand();
            }
        }

        String[] tokens = trimmed.split("[\\s:=]+");
        if (tokens.length == 0) {
            return ParsedCommand.notCommand();
        }

        String firstToken = tokens[0];
        String verb = firstToken.toUpperCase(Locale.US);
        if ("ПОМОЩЬ".equalsIgnoreCase(firstToken)) {
            verb = "HELP";
        }
        if (!VERBS.contains(verb)) {
            return ParsedCommand.notCommand();
        }

        switch (verb) {
            case "HELP":
                return ParsedCommand.of(ParsedCommand.Kind.HELP);

            case "STATUS":
                return ParsedCommand.of(ParsedCommand.Kind.STATUS);

            case "RESET":
                return ParsedCommand.of(ParsedCommand.Kind.RESET);

            case "GET":
                if (tokens.length == 1) {
                    return ParsedCommand.of(ParsedCommand.Kind.GET_ALL);
                }
                return ParsedCommand.getOne(tokens[1].toLowerCase(Locale.US));

            case "SET":
                if (tokens.length < 3) {
                    return ParsedCommand.badUsage("SET требует ключ и значение. Пример: SET preset coder.");
                }
                return ParsedCommand.setOne(
                        tokens[1].toLowerCase(Locale.US),
                        joinFrom(tokens, 2));

            case "LIST":
                if (tokens.length < 2) {
                    return ParsedCommand.badUsage("LIST presets или LIST models.");
                }
                String what = tokens[1].toUpperCase(Locale.US);
                if (what.startsWith("PRESET")) {
                    return ParsedCommand.of(ParsedCommand.Kind.LIST_PRESETS);
                }
                if (what.startsWith("MODEL")) {
                    return ParsedCommand.of(ParsedCommand.Kind.LIST_MODELS);
                }
                return ParsedCommand.badUsage("Неизвестный LIST. Используйте LIST presets или LIST models.");

            case "CHAT":
                return parseChat(tokens);

            case "MODEL":
                return parseModel(tokens);

            case "ALLOWED":
                return parseAllowed(tokens);

            case "PRESET":
                if (tokens.length < 2) {
                    return ParsedCommand.badUsage("PRESET имя. Пример: PRESET coder.");
                }
                return ParsedCommand.setOne("preset", tokens[1]);

            case "TOKENS":
                if (tokens.length < 2) {
                    return ParsedCommand.badUsage("TOKENS N. Пример: TOKENS 200.");
                }
                return ParsedCommand.setOne("tokens", tokens[1]);

            case "THINK":
                if (tokens.length < 2) {
                    return ParsedCommand.badUsage("THINK on или THINK off.");
                }
                return ParsedCommand.setOne("think", tokens[1]);

            case "WEB":
                if (tokens.length < 2) {
                    return ParsedCommand.badUsage("WEB on или WEB off.");
                }
                return ParsedCommand.setOne("web", tokens[1]);

            case "TEMP":
                if (tokens.length < 2) {
                    return ParsedCommand.badUsage("TEMP N. Пример: TEMP 0.4.");
                }
                return ParsedCommand.setOne("temp", tokens[1]);

            case "CHARS":
                if (tokens.length < 2) {
                    return ParsedCommand.badUsage("CHARS N. Пример: CHARS 700.");
                }
                return ParsedCommand.setOne("chars", tokens[1]);

            default:
                return ParsedCommand.notCommand();
        }
    }

    private static ParsedCommand parseChat(String[] tokens) {
        if (tokens.length < 2) {
            return ParsedCommand.badUsage(
                    "CHAT LIST | CHAT NEW имя | CHAT USE имя | CHAT CLEAR | CHAT DELETE имя.");
        }
        String sub = tokens[1].toUpperCase(Locale.US);
        switch (sub) {
            case "LIST":
                return ParsedCommand.of(ParsedCommand.Kind.CHAT_LIST);
            case "CLEAR":
                return ParsedCommand.of(ParsedCommand.Kind.CHAT_CLEAR);
            case "NEW":
                if (tokens.length < 3) {
                    return ParsedCommand.badUsage("CHAT NEW имя. Пример: CHAT NEW work.");
                }
                return ParsedCommand.withValue(ParsedCommand.Kind.CHAT_NEW, joinFrom(tokens, 2));
            case "USE":
                if (tokens.length < 3) {
                    return ParsedCommand.badUsage("CHAT USE имя. Пример: CHAT USE work.");
                }
                return ParsedCommand.withValue(ParsedCommand.Kind.CHAT_USE, joinFrom(tokens, 2));
            case "DELETE":
            case "DEL":
            case "REMOVE":
            case "RM":
                if (tokens.length < 3) {
                    return ParsedCommand.badUsage("CHAT DELETE имя. Пример: CHAT DELETE work.");
                }
                return ParsedCommand.withValue(ParsedCommand.Kind.CHAT_DELETE, joinFrom(tokens, 2));
            default:
                return ParsedCommand.badUsage(
                        "CHAT LIST | CHAT NEW имя | CHAT USE имя | CHAT CLEAR | CHAT DELETE имя.");
        }
    }

    private static ParsedCommand parseAllowed(String[] tokens) {
        if (tokens.length < 2) {
            return ParsedCommand.of(ParsedCommand.Kind.ALLOWED_LIST);
        }
        String sub = tokens[1].toUpperCase(Locale.US);
        switch (sub) {
            case "LIST":
            case "GET":
                return ParsedCommand.of(ParsedCommand.Kind.ALLOWED_LIST);
            case "ADD":
                if (tokens.length < 3) {
                    return ParsedCommand.badUsage("ALLOWED ADD <номер>. Пример: ALLOWED ADD +79121234567.");
                }
                return ParsedCommand.withValue(ParsedCommand.Kind.ALLOWED_ADD, joinFrom(tokens, 2));
            case "DEL":
            case "DELETE":
            case "REMOVE":
            case "RM":
                if (tokens.length < 3) {
                    return ParsedCommand.badUsage("ALLOWED DEL <номер>. Пример: ALLOWED DEL +79121234567.");
                }
                return ParsedCommand.withValue(ParsedCommand.Kind.ALLOWED_DEL, joinFrom(tokens, 2));
            case "CLEAR":
            case "OFF":
                return ParsedCommand.of(ParsedCommand.Kind.ALLOWED_CLEAR);
            case "SET":
                if (tokens.length < 3) {
                    return ParsedCommand.badUsage(
                            "ALLOWED SET <номер[,номер...]>. Пример: ALLOWED SET +79121234567,+79122223344.");
                }
                return ParsedCommand.withValue(ParsedCommand.Kind.ALLOWED_SET, joinFrom(tokens, 2));
            default:
                return ParsedCommand.badUsage(
                        "ALLOWED [LIST|ADD <номер>|DEL <номер>|CLEAR|SET <csv>].");
        }
    }

    private static ParsedCommand parseModel(String[] tokens) {
        if (tokens.length < 2) {
            return ParsedCommand.badUsage("MODEL N | MODEL LIST | MODEL INFO N | MODEL USE id.");
        }
        String sub = tokens[1].toUpperCase(Locale.US);
        if ("LIST".equals(sub)) {
            return ParsedCommand.of(ParsedCommand.Kind.MODEL_LIST);
        }
        if ("INFO".equals(sub)) {
            if (tokens.length < 3) {
                return ParsedCommand.badUsage("MODEL INFO N. Пример: MODEL INFO 5.");
            }
            return ParsedCommand.withValue(ParsedCommand.Kind.MODEL_INFO, tokens[2]);
        }
        if ("USE".equals(sub)) {
            if (tokens.length < 3) {
                return ParsedCommand.badUsage("MODEL USE id. Пример: MODEL USE openai/gpt-4o-mini.");
            }
            return ParsedCommand.modelSelect(tokens[2]);
        }
        return ParsedCommand.modelSelect(tokens[1]);
    }

    private static String joinFrom(String[] tokens, int startIndex) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < tokens.length; i++) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(tokens[i]);
        }
        return sb.toString();
    }
}
