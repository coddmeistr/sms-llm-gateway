package com.example.smsllmgateway.commands;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandParserTest {

    @Test
    public void nullAndBlankBodyAreNotCommands() {
        assertEquals(ParsedCommand.Kind.NOT_A_COMMAND, CommandParser.parse(null).kind);
        assertEquals(ParsedCommand.Kind.NOT_A_COMMAND, CommandParser.parse("").kind);
        assertEquals(ParsedCommand.Kind.NOT_A_COMMAND, CommandParser.parse("   ").kind);
    }

    @Test
    public void randomTextIsNotACommand() {
        ParsedCommand cmd = CommandParser.parse("model 2 в химии очень важна");
        // First token is "model", followed by "2" — that's a real command pattern
        // we want to allow. So this IS a command (MODEL_SELECT with "2") — choose a different test:
        assertEquals(ParsedCommand.Kind.MODEL_SELECT, cmd.kind);

        ParsedCommand prose = CommandParser.parse("привет, как у тебя дела?");
        assertEquals(ParsedCommand.Kind.NOT_A_COMMAND, prose.kind);

        ParsedCommand other = CommandParser.parse("just text here");
        assertEquals(ParsedCommand.Kind.NOT_A_COMMAND, other.kind);
    }

    @Test
    public void helpParsesIncludingRussianAlias() {
        assertEquals(ParsedCommand.Kind.HELP, CommandParser.parse("HELP").kind);
        assertEquals(ParsedCommand.Kind.HELP, CommandParser.parse("help").kind);
        assertEquals(ParsedCommand.Kind.HELP, CommandParser.parse("/help").kind);
        assertEquals(ParsedCommand.Kind.HELP, CommandParser.parse("!help").kind);
        assertEquals(ParsedCommand.Kind.HELP, CommandParser.parse("Помощь").kind);
    }

    @Test
    public void statusAndReset() {
        assertEquals(ParsedCommand.Kind.STATUS, CommandParser.parse("status").kind);
        assertEquals(ParsedCommand.Kind.RESET, CommandParser.parse("RESET").kind);
    }

    @Test
    public void getAllVsGetOne() {
        assertEquals(ParsedCommand.Kind.GET_ALL, CommandParser.parse("GET").kind);
        ParsedCommand one = CommandParser.parse("GET preset");
        assertEquals(ParsedCommand.Kind.GET_ONE, one.kind);
        assertEquals("preset", one.key);
    }

    @Test
    public void setVariants() {
        ParsedCommand a = CommandParser.parse("SET preset coder");
        assertEquals(ParsedCommand.Kind.SET_ONE, a.kind);
        assertEquals("preset", a.key);
        assertEquals("coder", a.value);

        ParsedCommand b = CommandParser.parse("set preset=coder");
        assertEquals(ParsedCommand.Kind.SET_ONE, b.kind);
        assertEquals("preset", b.key);
        assertEquals("coder", b.value);

        ParsedCommand c = CommandParser.parse("/SET tokens : 200");
        assertEquals(ParsedCommand.Kind.SET_ONE, c.kind);
        assertEquals("tokens", c.key);
        assertEquals("200", c.value);
    }

    @Test
    public void setWithoutValueIsBadUsage() {
        ParsedCommand cmd = CommandParser.parse("SET preset");
        assertEquals(ParsedCommand.Kind.BAD_USAGE, cmd.kind);
        assertNotNull(cmd.error);
    }

    @Test
    public void presetAlias() {
        ParsedCommand cmd = CommandParser.parse("PRESET coder");
        assertEquals(ParsedCommand.Kind.SET_ONE, cmd.kind);
        assertEquals("preset", cmd.key);
        assertEquals("coder", cmd.value);
    }

    @Test
    public void tokensAlias() {
        ParsedCommand cmd = CommandParser.parse("TOKENS 500");
        assertEquals(ParsedCommand.Kind.SET_ONE, cmd.kind);
        assertEquals("tokens", cmd.key);
        assertEquals("500", cmd.value);
    }

    @Test
    public void thinkAlias() {
        ParsedCommand cmd = CommandParser.parse("THINK on");
        assertEquals(ParsedCommand.Kind.SET_ONE, cmd.kind);
        assertEquals("think", cmd.key);
        assertEquals("on", cmd.value);
    }

    @Test
    public void webAlias() {
        ParsedCommand cmd = CommandParser.parse("web yes");
        assertEquals(ParsedCommand.Kind.SET_ONE, cmd.kind);
        assertEquals("web", cmd.key);
    }

    @Test
    public void tempAlias() {
        ParsedCommand cmd = CommandParser.parse("TEMP 0.7");
        assertEquals(ParsedCommand.Kind.SET_ONE, cmd.kind);
        assertEquals("temp", cmd.key);
        assertEquals("0.7", cmd.value);
    }

    @Test
    public void charsAlias() {
        ParsedCommand cmd = CommandParser.parse("CHARS 500");
        assertEquals(ParsedCommand.Kind.SET_ONE, cmd.kind);
        assertEquals("chars", cmd.key);
    }

    @Test
    public void listVariants() {
        assertEquals(ParsedCommand.Kind.LIST_PRESETS, CommandParser.parse("LIST presets").kind);
        assertEquals(ParsedCommand.Kind.LIST_PRESETS, CommandParser.parse("LIST preset").kind);
        assertEquals(ParsedCommand.Kind.LIST_MODELS, CommandParser.parse("LIST models").kind);
        assertEquals(ParsedCommand.Kind.LIST_MODELS, CommandParser.parse("list model").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("LIST garbage").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("LIST").kind);
    }

    @Test
    public void chatCommands() {
        assertEquals(ParsedCommand.Kind.CHAT_LIST, CommandParser.parse("CHAT LIST").kind);
        assertEquals(ParsedCommand.Kind.CHAT_CLEAR, CommandParser.parse("chat clear").kind);

        ParsedCommand neu = CommandParser.parse("CHAT NEW work");
        assertEquals(ParsedCommand.Kind.CHAT_NEW, neu.kind);
        assertEquals("work", neu.value);

        ParsedCommand use = CommandParser.parse("/chat use Home Stuff");
        assertEquals(ParsedCommand.Kind.CHAT_USE, use.kind);
        assertEquals("Home Stuff", use.value);

        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("CHAT NEW").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("CHAT").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("CHAT what").kind);
    }

    @Test
    public void chatDeleteVariants() {
        ParsedCommand a = CommandParser.parse("CHAT DELETE work");
        assertEquals(ParsedCommand.Kind.CHAT_DELETE, a.kind);
        assertEquals("work", a.value);

        ParsedCommand b = CommandParser.parse("chat del work");
        assertEquals(ParsedCommand.Kind.CHAT_DELETE, b.kind);
        assertEquals("work", b.value);

        ParsedCommand c = CommandParser.parse("/CHAT REMOVE Old Stuff");
        assertEquals(ParsedCommand.Kind.CHAT_DELETE, c.kind);
        assertEquals("Old Stuff", c.value);

        ParsedCommand d = CommandParser.parse("CHAT RM work");
        assertEquals(ParsedCommand.Kind.CHAT_DELETE, d.kind);

        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("CHAT DELETE").kind);
    }

    @Test
    public void allowedVerbVariants() {
        assertEquals(ParsedCommand.Kind.ALLOWED_LIST, CommandParser.parse("ALLOWED").kind);
        assertEquals(ParsedCommand.Kind.ALLOWED_LIST, CommandParser.parse("ALLOWED LIST").kind);
        assertEquals(ParsedCommand.Kind.ALLOWED_LIST, CommandParser.parse("allowed get").kind);

        ParsedCommand add = CommandParser.parse("ALLOWED ADD +79121234567");
        assertEquals(ParsedCommand.Kind.ALLOWED_ADD, add.kind);
        assertEquals("+79121234567", add.value);

        ParsedCommand del = CommandParser.parse("allowed del +79121234567");
        assertEquals(ParsedCommand.Kind.ALLOWED_DEL, del.kind);
        assertEquals("+79121234567", del.value);

        assertEquals(ParsedCommand.Kind.ALLOWED_DEL, CommandParser.parse("ALLOWED REMOVE +7912").kind);
        assertEquals(ParsedCommand.Kind.ALLOWED_DEL, CommandParser.parse("ALLOWED RM +7912").kind);

        assertEquals(ParsedCommand.Kind.ALLOWED_CLEAR, CommandParser.parse("ALLOWED CLEAR").kind);
        assertEquals(ParsedCommand.Kind.ALLOWED_CLEAR, CommandParser.parse("ALLOWED OFF").kind);

        ParsedCommand set = CommandParser.parse("ALLOWED SET +7912,+7913");
        assertEquals(ParsedCommand.Kind.ALLOWED_SET, set.kind);
        assertEquals("+7912,+7913", set.value);

        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("ALLOWED ADD").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("ALLOWED DEL").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("ALLOWED SET").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("ALLOWED nonsense").kind);
    }

    @Test
    public void setAllowedUniversalForm() {
        ParsedCommand cmd = CommandParser.parse("SET allowed +79121234567,+79122223344");
        assertEquals(ParsedCommand.Kind.SET_ONE, cmd.kind);
        assertEquals("allowed", cmd.key);
        assertEquals("+79121234567,+79122223344", cmd.value);
    }

    @Test
    public void modelByNumber() {
        ParsedCommand cmd = CommandParser.parse("MODEL 5");
        assertEquals(ParsedCommand.Kind.MODEL_SELECT, cmd.kind);
        assertEquals("model", cmd.key);
        assertEquals("5", cmd.value);
    }

    @Test
    public void modelListAndInfoAndUse() {
        assertEquals(ParsedCommand.Kind.MODEL_LIST, CommandParser.parse("MODEL LIST").kind);

        ParsedCommand info = CommandParser.parse("MODEL INFO 4");
        assertEquals(ParsedCommand.Kind.MODEL_INFO, info.kind);
        assertEquals("4", info.value);

        ParsedCommand use = CommandParser.parse("MODEL USE openai/gpt-4o-mini");
        assertEquals(ParsedCommand.Kind.MODEL_SELECT, use.kind);
        assertEquals("openai/gpt-4o-mini", use.value);

        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("MODEL").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("MODEL INFO").kind);
        assertEquals(ParsedCommand.Kind.BAD_USAGE, CommandParser.parse("MODEL USE").kind);
    }

    @Test
    public void onOffSynonyms() {
        assertTrue(CommandParser.isOnValue("on"));
        assertTrue(CommandParser.isOnValue("ON"));
        assertTrue(CommandParser.isOnValue("yes"));
        assertTrue(CommandParser.isOnValue("y"));
        assertTrue(CommandParser.isOnValue("1"));
        assertTrue(CommandParser.isOnValue("+"));
        assertTrue(CommandParser.isOnValue("вкл"));

        assertTrue(CommandParser.isOffValue("off"));
        assertTrue(CommandParser.isOffValue("no"));
        assertTrue(CommandParser.isOffValue("0"));
        assertTrue(CommandParser.isOffValue("выкл"));

        assertTrue(!CommandParser.isOnValue(null));
        assertTrue(!CommandParser.isOnValue("maybe"));
    }

    @Test
    public void prefixSlashOrBangIsStripped() {
        assertEquals(ParsedCommand.Kind.STATUS, CommandParser.parse("/STATUS").kind);
        assertEquals(ParsedCommand.Kind.STATUS, CommandParser.parse("!STATUS").kind);
        assertEquals(ParsedCommand.Kind.HELP, CommandParser.parse("/   help").kind);
    }

    @Test
    public void unknownVerbIsNotACommand() {
        ParsedCommand cmd = CommandParser.parse("/foobar baz");
        assertEquals(ParsedCommand.Kind.NOT_A_COMMAND, cmd.kind);
        assertNull(cmd.key);
    }
}
