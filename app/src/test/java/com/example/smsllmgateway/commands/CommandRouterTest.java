package com.example.smsllmgateway.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.smsllmgateway.GatewayConfig;
import com.example.smsllmgateway.ModelCatalog;

import org.junit.Before;
import org.junit.Test;

public class CommandRouterTest {

    private static final String SENDER = "+79121234567";

    private InMemorySettingsGateway settings;
    private CommandRouter router;

    @Before
    public void setUp() {
        settings = new InMemorySettingsGateway();
        router = new CommandRouter(settings);
    }

    private String route(String body) {
        return router.route(SENDER, CommandParser.parse(body));
    }

    @Test
    public void plainTextIsForwarded() {
        assertNull(route("привет, как дела?"));
        assertNull(route(""));
        assertNull(route(null));
    }

    @Test
    public void helpReturnsKnownText() {
        String reply = route("HELP");
        assertNotNull(reply);
        assertTrue("expected command list: " + reply, reply.contains("HELP"));
        assertTrue("expected SET hint: " + reply, reply.contains("SET"));
    }

    @Test
    public void statusListsAllSettings() {
        String reply = route("STATUS");
        assertNotNull(reply);
        for (String token : new String[]{"Чат", "Модель", "Preset", "Tokens", "Temp", "Think", "Web"}) {
            assertTrue("missing " + token + " in: " + reply, reply.contains(token));
        }
    }

    @Test
    public void resetClearsPerSenderSettings() {
        settings.setTokens(SENDER, 500);
        settings.setThinking(SENDER, true);
        String reply = route("RESET");
        assertTrue(reply.contains("сброшен"));
        assertEquals(GatewayConfig.DEFAULT_TOKENS, settings.getTokens(SENDER));
        assertFalse(settings.isThinking(SENDER));
    }

    @Test
    public void getAllRendersValues() {
        String reply = route("GET");
        assertTrue(reply.contains("preset=" + GatewayConfig.DEFAULT_PRESET));
        assertTrue(reply.contains("tokens=" + GatewayConfig.DEFAULT_TOKENS));
    }

    @Test
    public void getOneUnknownKeyGivesHelpfulError() {
        String reply = route("GET nope");
        assertTrue(reply.contains("Неизвестный ключ"));
    }

    @Test
    public void setPresetAcceptsKnownAndRejectsUnknown() {
        String ok = route("SET preset coder");
        assertEquals("preset=coder", ok);
        assertEquals("coder", settings.getPreset());

        String bad = route("PRESET nonsense");
        assertTrue(bad.contains("Неизвестный пресет"));
        assertEquals("coder", settings.getPreset()); // unchanged
    }

    @Test
    public void setTokensValidatesRange() {
        assertTrue(route("TOKENS abc").contains("целое"));
        assertTrue(route("TOKENS 5").contains("должно быть"));
        assertTrue(route("TOKENS 10000").contains("должно быть"));
        String ok = route("TOKENS 300");
        assertEquals("tokens=300", ok);
        assertEquals(300, settings.getTokens(SENDER));
    }

    @Test
    public void setTemperatureValidatesAndAcceptsComma() {
        assertTrue(route("TEMP foo").contains("ожидается"));
        assertTrue(route("TEMP 5").contains("должно"));
        String ok = route("TEMP 0,7");
        assertTrue("expected updated temp message: " + ok, ok.startsWith("temp="));
        assertEquals(0.7, settings.getTemperature(SENDER), 0.0001);
    }

    @Test
    public void setBooleansAcceptSynonyms() {
        assertEquals("think=on", route("THINK yes"));
        assertTrue(settings.isThinking(SENDER));
        assertEquals("think=off", route("THINK выкл"));
        assertFalse(settings.isThinking(SENDER));

        assertEquals("web=on", route("WEB 1"));
        assertTrue(settings.isWebSearch(SENDER));
        assertEquals("web=off", route("SET web 0"));
        assertFalse(settings.isWebSearch(SENDER));

        assertTrue(route("THINK maybe").contains("ожидается"));
    }

    @Test
    public void setCharsValidatesRange() {
        String ok = route("CHARS 500");
        assertEquals("chars=500", ok);
        assertEquals(500, settings.getReplyChars());

        assertTrue(route("CHARS 5").contains("должно"));
        assertTrue(route("CHARS abc").contains("целое"));
    }

    @Test
    public void modelSelectByNumberUsesCatalog() {
        String reply = route("MODEL 2");
        ModelCatalog.Model expected = ModelCatalog.byIndex(2);
        assertNotNull(expected);
        assertEquals(expected.id, settings.getModel(SENDER));
        assertTrue("expected display name in reply: " + reply, reply.contains(expected.name));
    }

    @Test
    public void modelSelectInvalidNumberIsRejected() {
        String reply = route("MODEL 999");
        assertTrue(reply.contains("Нет модели"));
        // sender model unchanged
        assertEquals(GatewayConfig.DEFAULT_MODEL, settings.getModel(SENDER));
    }

    @Test
    public void modelSelectByIdIsAccepted() {
        String reply = route("MODEL USE openai/gpt-4o-mini");
        assertEquals("openai/gpt-4o-mini", settings.getModel(SENDER));
        assertTrue(reply.contains("openai/gpt-4o-mini"));
    }

    @Test
    public void modelSelectUnknownIdIsStored() {
        // Unknown ids are accepted (user might know a model not in catalog).
        String reply = route("MODEL USE provider/custom-model");
        assertEquals("provider/custom-model", settings.getModel(SENDER));
        assertNotNull(reply);
    }

    @Test
    public void modelListAndInfo() {
        String list = route("MODEL LIST");
        assertTrue(list.contains("1. "));
        String info = route("MODEL INFO 1");
        assertTrue(info, info.contains("id"));
    }

    @Test
    public void listPresetsAndModels() {
        String presets = route("LIST presets");
        assertTrue(presets.contains("default"));
        assertTrue(presets.contains("coder"));
        String models = route("LIST models");
        assertTrue(models.contains("1. "));
    }

    @Test
    public void chatLifecycle() {
        String created = route("CHAT NEW work");
        assertTrue(created.contains("work"));
        assertEquals("work", settings.getActiveChat(SENDER));

        String list = route("CHAT LIST");
        assertTrue(list.contains("work"));
        assertTrue(list.contains("main"));

        String use = route("CHAT USE main");
        assertTrue(use.contains("main"));
        assertEquals("main", settings.getActiveChat(SENDER));

        String cleared = route("CHAT CLEAR");
        assertTrue(cleared.contains("main"));
    }

    @Test
    public void chatDeleteResetsActiveAndRemovesFromList() {
        route("CHAT NEW work");
        assertEquals("work", settings.getActiveChat(SENDER));
        assertTrue(settings.listChats(SENDER).contains("work"));

        String reply = route("CHAT DELETE work");
        assertNotNull(reply);
        assertTrue("reply mentions removal: " + reply, reply.toLowerCase().contains("удал"));
        assertFalse(settings.listChats(SENDER).contains("work"));
        // active resets to main when current chat is deleted
        assertEquals("main", settings.getActiveChat(SENDER));
    }

    @Test
    public void chatDeleteMainIsRejected() {
        String reply = route("CHAT DELETE main");
        assertTrue("expected rejection: " + reply, reply.toLowerCase().contains("нельзя"));
        assertTrue(settings.listChats(SENDER).contains("main"));
    }

    @Test
    public void chatDeleteNonexistentReportsError() {
        String reply = route("CHAT DELETE nope");
        assertTrue(reply, reply.toLowerCase().contains("нет чата"));
    }

    @Test
    public void allowedListWhenEmptyMentionsAllNumbers() {
        String reply = route("ALLOWED");
        assertTrue(reply, reply.toLowerCase().contains("пуст"));
    }

    @Test
    public void allowedAddNormalizesAndPersists() {
        String reply = route("ALLOWED ADD +7 (912) 123-45-67");
        assertTrue(reply, reply.contains("+79121234567"));
        assertTrue(settings.getAllowedSenders().contains("+79121234567"));
    }

    @Test
    public void allowedAddDuplicateIsReported() {
        route("ALLOWED ADD +79121234567");
        String reply = route("ALLOWED ADD +7-912-123-45-67");
        assertTrue(reply.toLowerCase(), reply.toLowerCase().contains("уже"));
    }

    @Test
    public void allowedDelRemoves() {
        route("ALLOWED ADD +79121234567");
        route("ALLOWED ADD +79122223344");
        String reply = route("ALLOWED DEL +79121234567");
        assertTrue(reply, reply.toLowerCase().contains("удал"));
        assertFalse("removed number must not remain: " + settings.getAllowedSenders(),
                settings.getAllowedSenders().contains("79121234567"));
        assertTrue(settings.getAllowedSenders().contains("79122223344"));
    }

    @Test
    public void allowedDelLastNotifiesOpenMode() {
        route("ALLOWED ADD +79121234567");
        String reply = route("ALLOWED DEL +79121234567");
        assertTrue("expected hint about open mode: " + reply,
                reply.toLowerCase().contains("все"));
    }

    @Test
    public void allowedDelNonexistent() {
        route("ALLOWED ADD +79121234567");
        String reply = route("ALLOWED DEL +79129999999");
        assertTrue(reply.toLowerCase(), reply.toLowerCase().contains("не найден"));
    }

    @Test
    public void allowedDelSelfWarnsAboutLockout() {
        // SENDER removes themselves while another number stays — must warn.
        route("ALLOWED ADD +79121234567");
        route("ALLOWED ADD +79122223344");
        String reply = route("ALLOWED DEL +79121234567");
        assertTrue("expected self-lockout warning: " + reply,
                reply.toLowerCase().contains("ваш номер"));
        assertTrue(reply.contains("+79121234567"));
    }

    @Test
    public void allowedDelOtherDoesNotWarn() {
        route("ALLOWED ADD +79121234567");
        route("ALLOWED ADD +79122223344");
        String reply = route("ALLOWED DEL +79122223344");
        assertFalse("must not warn when sender stays in whitelist: " + reply,
                reply.toLowerCase().contains("ваш номер"));
    }

    @Test
    public void allowedDelLastDoesNotWarnBecauseOpenMode() {
        route("ALLOWED ADD +79121234567");
        String reply = route("ALLOWED DEL +79121234567");
        // Когда whitelist пустеет — режим открытый, отдельное предупреждение не нужно.
        assertFalse("no self-lockout warning when list empties: " + reply,
                reply.toLowerCase().contains("ваш номер"));
    }

    @Test
    public void allowedSetWithoutSelfWarns() {
        String reply = route("ALLOWED SET +79129999999,+79128888888");
        assertTrue("expected self-lockout warning: " + reply,
                reply.toLowerCase().contains("ваш номер"));
    }

    @Test
    public void allowedSetWithSelfDoesNotWarn() {
        String reply = route("ALLOWED SET +79121234567,+79129999999");
        assertFalse("must not warn when sender is in new list: " + reply,
                reply.toLowerCase().contains("ваш номер"));
    }

    @Test
    public void allowedSetReplacesAndNormalizes() {
        route("ALLOWED ADD +79121234567");
        String reply = route("ALLOWED SET +7912-000-00-01,+7912 000 00 02");
        assertTrue(reply, reply.contains("+79120000001"));
        assertTrue(reply, reply.contains("+79120000002"));
        assertFalse(settings.getAllowedSenders().contains("79121234567"));
    }

    @Test
    public void allowedClearEmptiesList() {
        route("ALLOWED ADD +79121234567");
        String reply = route("ALLOWED CLEAR");
        assertTrue(reply.toLowerCase(), reply.toLowerCase().contains("очищ"));
        assertEquals("", settings.getAllowedSenders());
    }

    @Test
    public void setAllowedAliasWorks() {
        String reply = route("SET allowed +79121234567");
        assertTrue(reply, reply.contains("+79121234567"));
        assertTrue(settings.getAllowedSenders().contains("+79121234567"));
    }

    @Test
    public void getAllowedRenders() {
        route("ALLOWED ADD +79121234567");
        String reply = route("GET allowed");
        assertTrue(reply, reply.contains("+79121234567"));
    }

    @Test
    public void getAllIncludesAllowed() {
        route("ALLOWED ADD +79121234567");
        String reply = route("GET");
        assertTrue("GET ALL must mention allowed: " + reply, reply.contains("allowed="));
    }

    @Test
    public void badUsageReturnsParserError() {
        String reply = route("SET preset");
        assertNotNull(reply);
        assertTrue("expected error hint: " + reply, reply.contains("preset"));
    }

    @Test
    public void perSenderSettingsAreIndependent() {
        String a = "+79121111111";
        String b = "+79122222222";
        router.route(a, CommandParser.parse("TOKENS 400"));
        router.route(b, CommandParser.parse("TOKENS 600"));
        assertEquals(400, settings.getTokens(a));
        assertEquals(600, settings.getTokens(b));
    }

    // ----- Batch (multiple commands per SMS, `&&` delimiter) -----

    private String routeBatch(String body) {
        return router.routeBatch(SENDER, CommandBatch.split(body));
    }

    @Test
    public void batchExecutesAllSegmentsInOrder() {
        String reply = routeBatch("PRESET coder && TOKENS 400 && THINK on");
        assertNotNull(reply);
        assertTrue("expected numbered first line: " + reply, reply.startsWith("1) "));
        assertTrue("expected numbered second line: " + reply, reply.contains("\n2) "));
        assertTrue("expected numbered third line: " + reply, reply.contains("\n3) "));
        assertEquals("coder", settings.getPreset());
        assertEquals(400, settings.getTokens(SENDER));
        assertTrue(settings.isThinking(SENDER));
    }

    @Test
    public void batchOrderMatters() {
        // Последний TOKENS должен победить.
        routeBatch("TOKENS 200 && TOKENS 800 && TOKENS 555");
        assertEquals(555, settings.getTokens(SENDER));
    }

    @Test
    public void batchSettingsThenStatusReadsTheNewValues() {
        String reply = routeBatch("PRESET coder && CHARS 1200 && STATUS");
        assertTrue("STATUS должен видеть свежий preset: " + reply,
                reply.contains("Preset: coder"));
        assertTrue("STATUS должен видеть свежий chars: " + reply,
                reply.contains("Chars: 1200"));
    }

    @Test
    public void batchSingleSegmentStillNumbered() {
        // Чисто формальный случай — внутри роутера сегмент один, но мы всё равно
        // нумеруем для единообразного контракта. Внешний контроллер (Service)
        // отвечает за то, чтобы вообще не звать routeBatch на одиночном сегменте.
        String reply = router.routeBatch(SENDER, new String[]{"STATUS"});
        assertTrue("expected numbered reply: " + reply, reply.startsWith("1) "));
    }

    @Test
    public void batchEmptyOrNullReturnsExplanation() {
        assertNotNull(router.routeBatch(SENDER, null));
        assertNotNull(router.routeBatch(SENDER, new String[0]));
        String reply = router.routeBatch(SENDER, new String[0]);
        assertTrue("expected hint about format: " + reply, reply.contains("&&"));
    }

    @Test
    public void batchUnknownSegmentReportedInlineAndOthersStillRun() {
        String reply = routeBatch("PRESET coder && привет && CHARS 800");
        assertNotNull(reply);
        assertTrue("first segment must succeed: " + reply, reply.contains("1) preset=coder"));
        assertTrue("second segment must be flagged: " + reply,
                reply.contains("2) не команда: привет"));
        assertTrue("third segment must succeed: " + reply, reply.contains("3) chars=800"));
        // Side effects of the two valid segments did apply.
        assertEquals("coder", settings.getPreset());
        assertEquals(800, settings.getReplyChars());
    }

    @Test
    public void batchInvalidValueProducesBadUsageReplyButContinues() {
        // TOKENS abc -> BAD_USAGE -> in batch this segment yields the parser's
        // error message; subsequent segments still execute.
        String reply = routeBatch("TOKENS abc && TOKENS 500");
        assertTrue("expected error mention in segment 1: " + reply,
                reply.toLowerCase().contains("tokens"));
        assertTrue(reply.contains("2) tokens=500"));
        assertEquals(500, settings.getTokens(SENDER));
    }

    @Test
    public void batchDoesNotInvokeLlmEvenForFreeTextSegment() {
        // Доказываем контракт «батч никогда не уходит в LLM»: для не-команды
        // мы возвращаем строку с пометкой, а не null.
        String reply = router.routeBatch(SENDER, new String[]{"расскажи мне про яблоки"});
        assertNotNull(reply);
        assertTrue(reply.contains("не команда"));
    }

    @Test
    public void batchMixedCommandsCoverEverySetting() {
        String reply = routeBatch(
                "PRESET coder && MODEL USE openai/gpt-4o-mini && TOKENS 600"
                        + " && TEMP 0.3 && THINK on && WEB on && CHARS 1000"
                        + " && CHAT NEW work && ALLOWED ADD +79129999999");
        assertNotNull(reply);
        assertEquals("coder", settings.getPreset());
        assertEquals("openai/gpt-4o-mini", settings.getModel(SENDER));
        assertEquals(600, settings.getTokens(SENDER));
        assertEquals(0.3, settings.getTemperature(SENDER), 0.0001);
        assertTrue(settings.isThinking(SENDER));
        assertTrue(settings.isWebSearch(SENDER));
        assertEquals(1000, settings.getReplyChars());
        assertEquals("work", settings.getActiveChat(SENDER));
        assertTrue(settings.getAllowedSenders().contains("79129999999"));
        // Reply must have nine numbered segments.
        assertTrue(reply, reply.contains("\n9) "));
    }

    @Test
    public void batchReplyUsesNewlineBetweenSegments() {
        String reply = routeBatch("STATUS && HELP");
        int firstNl = reply.indexOf('\n');
        assertTrue("expected newline between segments: " + reply, firstNl > 0);
        assertTrue(reply.substring(firstNl + 1).startsWith("2) "));
    }
}
