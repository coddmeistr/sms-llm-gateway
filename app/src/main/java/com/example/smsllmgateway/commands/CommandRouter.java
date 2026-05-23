package com.example.smsllmgateway.commands;

import com.example.smsllmgateway.GatewayConfig;
import com.example.smsllmgateway.ModelCatalog;
import com.example.smsllmgateway.PhoneMatcher;
import com.example.smsllmgateway.PromptPresets;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Translates a {@link ParsedCommand} into a reply text, delegating storage to
 * a {@link SettingsGateway}. Returns {@code null} if the input is not a command
 * (caller should forward to the LLM).
 *
 * Pure Java, no Android dependencies. Fully unit-testable against an in-memory
 * SettingsGateway.
 */
public final class CommandRouter {

    private static final String HELP_TEXT =
            "Команды (любой регистр). HELP, STATUS, GET [ключ], SET ключ знач, RESET. "
                    + "Алиасы: PRESET имя, MODEL N|LIST|USE id|INFO N, TOKENS N, TEMP 0.4, "
                    + "THINK on/off, WEB on/off, CHARS N. "
                    + "Чаты: CHAT LIST|NEW имя|USE имя|CLEAR|DELETE имя. "
                    + "Whitelist: ALLOWED [LIST|ADD номер|DEL номер|CLEAR|SET csv]. "
                    + "LIST presets|models. "
                    + "Маркеры в SMS: [WEB] и [THINK] разово включают веб-поиск/мышление "
                    + "для одного запроса (даже если в настройках они выключены). "
                    + "Несколько команд в одной SMS — через &&: "
                    + "PRESET coder && TOKENS 400 && THINK on.";

    private final SettingsGateway settings;

    public CommandRouter(SettingsGateway settings) {
        this.settings = settings;
    }

    /**
     * Executes a pre-split batch of command segments sequentially, returning a
     * single concatenated reply. The result is never {@code null}; non-command
     * segments are reported inline so the user still gets feedback for every
     * position. This intentionally never falls through to the LLM — once the
     * user opts into batch syntax we treat the SMS as a settings macro.
     */
    public String routeBatch(String sender, String[] segments) {
        if (segments == null || segments.length == 0) {
            return "Пустой батч. Используйте формат: CMD1 && CMD2 && ...";
        }
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(i + 1).append(") ");
            String segment = segments[i];
            ParsedCommand parsed = CommandParser.parse(segment);
            if (parsed == null || parsed.kind == ParsedCommand.Kind.NOT_A_COMMAND) {
                out.append("не команда: ").append(segment);
                continue;
            }
            String reply = route(sender, parsed);
            // route() returns null only for NOT_A_COMMAND, which we've already
            // handled above; the defensive branch keeps the contract obvious.
            out.append(reply != null ? reply : "(нет ответа)");
        }
        return out.toString();
    }

    public String route(String sender, ParsedCommand cmd) {
        if (cmd == null || cmd.kind == ParsedCommand.Kind.NOT_A_COMMAND) {
            return null;
        }

        switch (cmd.kind) {
            case HELP:
                return HELP_TEXT;

            case STATUS:
                return renderStatus(sender);

            case RESET:
                settings.resetSender(sender);
                return "Per-sender настройки сброшены. Чаты сохранены.";

            case GET_ALL:
                return renderAll(sender);

            case GET_ONE:
                return renderOne(sender, cmd.key);

            case SET_ONE:
                return applySet(sender, cmd.key, cmd.value);

            case LIST_PRESETS:
                return renderPresetList();

            case LIST_MODELS:
            case MODEL_LIST:
                return "Модели:\n" + ModelCatalog.numberedList()
                        + "\nВыбор: MODEL N. Полный id: MODEL INFO N.";

            case MODEL_INFO:
                return renderModelInfo(cmd.value);

            case MODEL_SELECT:
                return applyModelSelection(sender, cmd.value);

            case CHAT_LIST: {
                List<String> chats = settings.listChats(sender);
                return "Чаты: " + join(chats) + ". Текущий: " + settings.getActiveChat(sender) + ".";
            }

            case CHAT_NEW:
                settings.setActiveChat(sender, cmd.value);
                return "Создан и выбран чат: " + settings.getActiveChat(sender) + ".";

            case CHAT_USE:
                settings.setActiveChat(sender, cmd.value);
                return "Выбран чат: " + settings.getActiveChat(sender) + ".";

            case CHAT_CLEAR: {
                String active = settings.getActiveChat(sender);
                settings.clearActiveChat(sender);
                return "История очищена: " + active + ".";
            }

            case CHAT_DELETE:
                return deleteChat(sender, cmd.value);

            case ALLOWED_LIST:
                return renderAllowed();

            case ALLOWED_ADD:
                return allowedAdd(cmd.value);

            case ALLOWED_DEL:
                return allowedRemove(sender, cmd.value);

            case ALLOWED_CLEAR:
                settings.setAllowedSenders("");
                return "Whitelist очищен. Шлюз принимает SMS со всех номеров.";

            case ALLOWED_SET:
                return allowedSet(sender, cmd.value);

            case BAD_USAGE:
                return cmd.error;

            default:
                return null;
        }
    }

    public static String helpText() {
        return HELP_TEXT;
    }

    private String renderStatus(String sender) {
        return "Чат: " + settings.getActiveChat(sender)
                + ". Модель: " + settings.getModel(sender)
                + ". Preset: " + settings.getPreset()
                + ". Tokens: " + settings.getTokens(sender)
                + ". Temp: " + formatTemperature(settings.getTemperature(sender))
                + ". Think: " + onOff(settings.isThinking(sender))
                + ". Web: " + onOff(settings.isWebSearch(sender))
                + ". Chars: " + settings.getReplyChars()
                + ".";
    }

    private String renderAll(String sender) {
        return "Настройки. "
                + "preset=" + settings.getPreset()
                + "; model=" + settings.getModel(sender)
                + "; tokens=" + settings.getTokens(sender)
                + "; temp=" + formatTemperature(settings.getTemperature(sender))
                + "; think=" + onOff(settings.isThinking(sender))
                + "; web=" + onOff(settings.isWebSearch(sender))
                + "; chars=" + settings.getReplyChars()
                + "; chat=" + settings.getActiveChat(sender)
                + "; allowed=" + describeAllowed()
                + ".";
    }

    private String renderOne(String sender, String key) {
        if (key == null) {
            return "Укажите ключ. Список: GET.";
        }
        switch (key) {
            case "preset":
                return "preset=" + settings.getPreset();
            case "model":
                return "model=" + settings.getModel(sender);
            case "tokens":
                return "tokens=" + settings.getTokens(sender);
            case "temp":
                return "temp=" + formatTemperature(settings.getTemperature(sender));
            case "think":
                return "think=" + onOff(settings.isThinking(sender));
            case "web":
                return "web=" + onOff(settings.isWebSearch(sender));
            case "chars":
                return "chars=" + settings.getReplyChars();
            case "chat":
                return "chat=" + settings.getActiveChat(sender);
            case "allowed":
                return "allowed=" + describeAllowed();
            default:
                return "Неизвестный ключ: " + key
                        + ". Доступны: preset, model, tokens, temp, think, web, chars, chat, allowed.";
        }
    }

    private String applySet(String sender, String key, String value) {
        if (key == null) {
            return "Укажите ключ. Пример: SET preset coder.";
        }
        if (value == null || value.trim().isEmpty()) {
            return "Укажите значение для " + key + ".";
        }
        String trimmed = value.trim();
        switch (key) {
            case "preset":
                if (!PromptPresets.isKnown(trimmed)) {
                    return "Неизвестный пресет: " + trimmed
                            + ". Доступны: " + PromptPresets.idsCsv() + ".";
                }
                settings.setPreset(trimmed.toLowerCase(Locale.US));
                return "preset=" + settings.getPreset();

            case "model":
                return applyModelSelection(sender, trimmed);

            case "tokens":
                return applyTokens(sender, trimmed);

            case "temp":
                return applyTemperature(sender, trimmed);

            case "think":
                return applyBoolean(sender, "think", trimmed, true);

            case "web":
                return applyBoolean(sender, "web", trimmed, false);

            case "chars":
                return applyChars(trimmed);

            case "allowed":
                return allowedSet(sender, trimmed);

            default:
                return "Неизвестный ключ: " + key
                        + ". Доступны: preset, model, tokens, temp, think, web, chars, allowed.";
        }
    }

    private String applyTokens(String sender, String value) {
        Integer parsed = tryParseInt(value);
        if (parsed == null) {
            return "tokens: ожидается целое число от " + GatewayConfig.MIN_TOKENS
                    + " до " + GatewayConfig.MAX_TOKENS + ".";
        }
        if (parsed < GatewayConfig.MIN_TOKENS || parsed > GatewayConfig.MAX_TOKENS) {
            return "tokens должно быть от " + GatewayConfig.MIN_TOKENS
                    + " до " + GatewayConfig.MAX_TOKENS + ".";
        }
        settings.setTokens(sender, parsed);
        return "tokens=" + settings.getTokens(sender);
    }

    private String applyTemperature(String sender, String value) {
        Double parsed = tryParseDouble(value);
        if (parsed == null) {
            return "temp: ожидается число от " + GatewayConfig.MIN_TEMPERATURE
                    + " до " + GatewayConfig.MAX_TEMPERATURE + ".";
        }
        if (parsed < GatewayConfig.MIN_TEMPERATURE || parsed > GatewayConfig.MAX_TEMPERATURE) {
            return "temp должно быть от " + GatewayConfig.MIN_TEMPERATURE
                    + " до " + GatewayConfig.MAX_TEMPERATURE + ".";
        }
        settings.setTemperature(sender, parsed);
        return "temp=" + formatTemperature(settings.getTemperature(sender));
    }

    private String applyBoolean(String sender, String key, String value, boolean isThink) {
        if (CommandParser.isOnValue(value)) {
            if (isThink) {
                settings.setThinking(sender, true);
            } else {
                settings.setWebSearch(sender, true);
            }
            return key + "=on";
        }
        if (CommandParser.isOffValue(value)) {
            if (isThink) {
                settings.setThinking(sender, false);
            } else {
                settings.setWebSearch(sender, false);
            }
            return key + "=off";
        }
        return key + ": ожидается on или off.";
    }

    private String applyChars(String value) {
        Integer parsed = tryParseInt(value);
        if (parsed == null) {
            return "chars: ожидается целое число от " + GatewayConfig.MIN_REPLY_CHARS
                    + " до " + GatewayConfig.MAX_REPLY_CHARS_LIMIT + ".";
        }
        if (parsed < GatewayConfig.MIN_REPLY_CHARS
                || parsed > GatewayConfig.MAX_REPLY_CHARS_LIMIT) {
            return "chars должно быть от " + GatewayConfig.MIN_REPLY_CHARS
                    + " до " + GatewayConfig.MAX_REPLY_CHARS_LIMIT + ".";
        }
        settings.setReplyChars(parsed);
        return "chars=" + settings.getReplyChars();
    }

    private String applyModelSelection(String sender, String value) {
        if (value == null) {
            return "Укажите номер или id модели.";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "Укажите номер или id модели.";
        }
        if (trimmed.matches("\\d+")) {
            return selectModelByNumber(sender, trimmed);
        }
        ModelCatalog.Model byId = ModelCatalog.byId(trimmed);
        settings.setModel(sender, byId != null ? byId.id : trimmed);
        String label = byId != null ? byId.name + " (" + byId.id + ")" : trimmed;
        return "model=" + label;
    }

    private String selectModelByNumber(String sender, String numberText) {
        int oneBased;
        try {
            oneBased = Integer.parseInt(numberText);
        } catch (NumberFormatException e) {
            return "Не понял номер модели. Отправьте MODEL LIST, затем MODEL N.";
        }
        ModelCatalog.Model model = ModelCatalog.byIndex(oneBased);
        if (model == null) {
            return "Нет модели с номером " + oneBased + ". MODEL LIST для списка.";
        }
        settings.setModel(sender, model.id);
        return "model=" + model.name + " (" + model.id + ")";
    }

    private String renderModelInfo(String numberText) {
        int oneBased;
        try {
            oneBased = Integer.parseInt(numberText.trim());
        } catch (NumberFormatException e) {
            return "MODEL INFO N. Пример: MODEL INFO 5.";
        }
        ModelCatalog.Model model = ModelCatalog.byIndex(oneBased);
        if (model == null) {
            return "Нет модели с номером " + oneBased + ".";
        }
        return oneBased + ". " + model.name + " — id: " + model.id
                + (model.free ? " (бесплатная)" : "")
                + (model.supportsThinking ? "; поддерживает thinking" : "") + ".";
    }

    private String deleteChat(String sender, String chatName) {
        if (chatName == null || chatName.trim().isEmpty()) {
            return "CHAT DELETE имя. Пример: CHAT DELETE work.";
        }
        String trimmed = chatName.trim();
        if ("main".equalsIgnoreCase(trimmed)) {
            return "Нельзя удалить чат по умолчанию: main. Очистить можно командой CHAT CLEAR.";
        }
        boolean existed = settings.deleteChat(sender, trimmed);
        if (!existed) {
            return "Нет чата с именем " + trimmed + ". Список: CHAT LIST.";
        }
        return "Чат удалён: " + trimmed + ". Активный: " + settings.getActiveChat(sender) + ".";
    }

    private List<String> parseAllowedList() {
        List<String> result = new ArrayList<>();
        String raw = settings.getAllowedSenders();
        if (raw == null) {
            return result;
        }
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String entry : raw.split("[,;\\n\\r]+")) {
            String normalized = PhoneMatcher.normalize(entry);
            if (!normalized.isEmpty() && seen.add(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String describeAllowed() {
        List<String> list = parseAllowedList();
        if (list.isEmpty()) {
            return "все номера";
        }
        return joinCsv(list) + " (" + list.size() + ")";
    }

    private String renderAllowed() {
        List<String> list = parseAllowedList();
        if (list.isEmpty()) {
            return "Whitelist пуст: SMS принимаются со всех номеров. "
                    + "Добавить: ALLOWED ADD <номер>.";
        }
        return "Whitelist (" + list.size() + "): " + joinCsv(list) + ".";
    }

    private String allowedAdd(String value) {
        String normalized = PhoneMatcher.normalize(value);
        if (normalized.isEmpty()) {
            return "Не понял номер: " + value + ". Пример: ALLOWED ADD +79121234567.";
        }
        List<String> list = parseAllowedList();
        for (String entry : list) {
            if (entry.equalsIgnoreCase(normalized)) {
                return "Уже в whitelist: " + normalized + ". Всего: " + list.size() + ".";
            }
        }
        list.add(normalized);
        settings.setAllowedSenders(joinCsv(list));
        return "Добавлен: " + normalized + ". Всего в whitelist: " + list.size() + ".";
    }

    private String allowedRemove(String sender, String value) {
        String normalized = PhoneMatcher.normalize(value);
        if (normalized.isEmpty()) {
            return "Не понял номер: " + value + ". Пример: ALLOWED DEL +79121234567.";
        }
        List<String> list = parseAllowedList();
        boolean removed = false;
        List<String> next = new ArrayList<>(list.size());
        for (String entry : list) {
            if (entry.equalsIgnoreCase(normalized)) {
                removed = true;
                continue;
            }
            next.add(entry);
        }
        if (!removed) {
            return "Не найден в whitelist: " + normalized + ". Список: ALLOWED LIST.";
        }
        settings.setAllowedSenders(joinCsv(next));
        String tail = next.isEmpty()
                ? " Whitelist пуст: SMS теперь принимаются со всех номеров."
                : " Осталось: " + next.size() + "." + selfLockoutWarning(sender, next);
        return "Удалён: " + normalized + "." + tail;
    }

    private String allowedSet(String sender, String value) {
        if (value == null) {
            return "ALLOWED SET <номер[,номер...]>. Пустое значение = ALLOWED CLEAR.";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            settings.setAllowedSenders("");
            return "Whitelist очищен. Шлюз принимает SMS со всех номеров.";
        }
        // If a comma or semicolon is present we treat input as CSV. Otherwise it's a
        // single number (which may contain spaces or formatting like "+7 912 123-45-67").
        String[] entries = (trimmed.indexOf(',') >= 0 || trimmed.indexOf(';') >= 0)
                ? trimmed.split("[,;]+")
                : new String[] { trimmed };
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String entry : entries) {
            String n = PhoneMatcher.normalize(entry);
            if (!n.isEmpty()) {
                normalized.add(n);
            }
        }
        if (normalized.isEmpty()) {
            return "Не нашёл ни одного номера в: " + trimmed + ".";
        }
        List<String> list = new ArrayList<>(normalized);
        settings.setAllowedSenders(joinCsv(list));
        return "Whitelist установлен (" + list.size() + "): " + joinCsv(list) + "."
                + selfLockoutWarning(sender, list);
    }

    /**
     * Returns a warning suffix if {@code sender} is no longer authorised by
     * {@code newList}. Returns an empty string when the sender is still allowed
     * or when we cannot reliably identify the sender.
     */
    private static String selfLockoutWarning(String sender, List<String> newList) {
        if (newList == null || newList.isEmpty()) {
            return "";
        }
        String normalizedSender = PhoneMatcher.normalize(sender);
        if (normalizedSender.isEmpty()) {
            return "";
        }
        StringBuilder csv = new StringBuilder();
        for (String entry : newList) {
            if (csv.length() > 0) {
                csv.append(',');
            }
            csv.append(entry);
        }
        if (PhoneMatcher.isAllowed(sender, csv.toString())) {
            return "";
        }
        return " Внимание: ваш номер " + normalizedSender
                + " больше не в whitelist. SMS от вас будут отклоняться."
                + " Восстановите доступ через экран настроек приложения.";
    }

    private static String joinCsv(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(value);
        }
        return sb.toString();
    }

    private String renderPresetList() {
        StringBuilder sb = new StringBuilder("Пресеты: ");
        boolean first = true;
        for (PromptPresets.Preset preset : PromptPresets.all()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(preset.id);
            first = false;
        }
        sb.append(". Выбор: PRESET имя.");
        return sb.toString();
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(", ");
            }
            result.append(value);
        }
        return result.toString();
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static String formatTemperature(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private static Integer tryParseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double tryParseDouble(String value) {
        try {
            return Double.parseDouble(value.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
