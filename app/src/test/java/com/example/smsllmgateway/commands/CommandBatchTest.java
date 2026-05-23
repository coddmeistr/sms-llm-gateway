package com.example.smsllmgateway.commands;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CommandBatchTest {

    @Test
    public void isBatchDetectsDelimiter() {
        assertTrue(CommandBatch.isBatch("a && b"));
        assertTrue(CommandBatch.isBatch("a&&b"));
        assertTrue(CommandBatch.isBatch("&&"));
        assertTrue(CommandBatch.isBatch("PRESET coder && TOKENS 400"));
    }

    @Test
    public void isBatchIgnoresPlainBodies() {
        assertFalse(CommandBatch.isBatch("STATUS"));
        assertFalse(CommandBatch.isBatch("hello world"));
        assertFalse(CommandBatch.isBatch("a & b"));
        assertFalse(CommandBatch.isBatch(""));
        assertFalse(CommandBatch.isBatch(null));
    }

    @Test
    public void splitNullReturnsEmpty() {
        assertArrayEquals(new String[0], CommandBatch.split(null));
    }

    @Test
    public void splitEmptyReturnsEmpty() {
        assertArrayEquals(new String[0], CommandBatch.split(""));
        assertArrayEquals(new String[0], CommandBatch.split("   "));
    }

    @Test
    public void splitNoDelimiterReturnsSingleTrimmedSegment() {
        assertArrayEquals(new String[]{"STATUS"}, CommandBatch.split("STATUS"));
        assertArrayEquals(new String[]{"STATUS"}, CommandBatch.split("   STATUS   "));
        assertArrayEquals(new String[]{"hello world"}, CommandBatch.split("hello world"));
    }

    @Test
    public void splitTwoCommands() {
        assertArrayEquals(
                new String[]{"STATUS", "HELP"},
                CommandBatch.split("STATUS && HELP"));
    }

    @Test
    public void splitTrimsEachSegment() {
        assertArrayEquals(
                new String[]{"STATUS", "HELP"},
                CommandBatch.split("   STATUS   &&   HELP   "));
    }

    @Test
    public void splitWithoutSpacesAroundDelimiter() {
        assertArrayEquals(
                new String[]{"STATUS", "HELP"},
                CommandBatch.split("STATUS&&HELP"));
    }

    @Test
    public void splitDropsLeadingEmptySegment() {
        assertArrayEquals(
                new String[]{"STATUS"},
                CommandBatch.split("&&STATUS"));
    }

    @Test
    public void splitDropsTrailingEmptySegment() {
        assertArrayEquals(
                new String[]{"STATUS"},
                CommandBatch.split("STATUS&&"));
    }

    @Test
    public void splitDropsInteriorEmptySegments() {
        assertArrayEquals(
                new String[]{"STATUS", "HELP"},
                CommandBatch.split("STATUS && && HELP"));
    }

    @Test
    public void splitOnlyDelimitersReturnsEmpty() {
        assertArrayEquals(new String[0], CommandBatch.split("&&"));
        assertArrayEquals(new String[0], CommandBatch.split("&& &&"));
        assertArrayEquals(new String[0], CommandBatch.split("&&&&"));
        assertArrayEquals(new String[0], CommandBatch.split("   &&   &&   "));
    }

    @Test
    public void splitManyCommands() {
        assertArrayEquals(
                new String[]{"PRESET coder", "TOKENS 400", "THINK on", "STATUS"},
                CommandBatch.split("PRESET coder && TOKENS 400 && THINK on && STATUS"));
    }

    @Test
    public void splitPreservesArgumentSpaces() {
        assertArrayEquals(
                new String[]{"MODEL USE openai/gpt-4o-mini", "STATUS"},
                CommandBatch.split("MODEL USE openai/gpt-4o-mini && STATUS"));
    }

    @Test
    public void splitPreservesArgumentSemicolonsAndCommas() {
        // Внутри ALLOWED SET ',' и ';' — CSV-разделители номеров.
        // Они не должны конфликтовать с разделителем '&&'.
        assertArrayEquals(
                new String[]{"ALLOWED SET +79121234567,+79122223344", "CHARS 800"},
                CommandBatch.split("ALLOWED SET +79121234567,+79122223344 && CHARS 800"));
        assertArrayEquals(
                new String[]{"ALLOWED SET +79121111111;+79122222222"},
                CommandBatch.split("ALLOWED SET +79121111111;+79122222222"));
    }

    @Test
    public void splitDoesNotSplitOnSingleAmpersand() {
        assertArrayEquals(
                new String[]{"R&D and Q&A"},
                CommandBatch.split("R&D and Q&A"));
    }

    @Test
    public void splitTripleAmpersandLeavesLoneAmpersand() {
        // "&&&" разбивается на ["", "&"] -> после фильтрации остаётся ["&"].
        // Это бессмысленный сегмент, но мы его сохраняем, чтобы router мог
        // выдать осмысленное "не команда".
        assertArrayEquals(new String[]{"&"}, CommandBatch.split("&&&"));
    }

    @Test
    public void delimiterConstantMatchesDocumentation() {
        assertEquals("&&", CommandBatch.DELIMITER);
    }
}
