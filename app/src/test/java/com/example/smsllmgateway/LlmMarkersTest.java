package com.example.smsllmgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LlmMarkersTest {

    @Test
    public void noMarkersLeavesBodyAsIs() {
        LlmMarkers m = LlmMarkers.extract("привет, как дела?");
        assertEquals("привет, как дела?", m.cleanedBody);
        assertFalse(m.forceWeb);
        assertFalse(m.forceThinking);
        assertFalse(m.isEmpty());
    }

    @Test
    public void nullAndEmptyAreSafe() {
        LlmMarkers a = LlmMarkers.extract(null);
        assertEquals("", a.cleanedBody);
        assertFalse(a.forceWeb);
        assertFalse(a.forceThinking);
        assertTrue(a.isEmpty());

        LlmMarkers b = LlmMarkers.extract("");
        assertEquals("", b.cleanedBody);
        assertFalse(b.forceWeb);
        assertFalse(b.forceThinking);
        assertTrue(b.isEmpty());
    }

    @Test
    public void webMarkerIsDetectedAndStripped() {
        LlmMarkers m = LlmMarkers.extract("[WEB] какая погода в Москве");
        assertTrue(m.forceWeb);
        assertFalse(m.forceThinking);
        assertEquals("какая погода в Москве", m.cleanedBody);
    }

    @Test
    public void thinkMarkerIsDetectedAndStripped() {
        LlmMarkers m = LlmMarkers.extract("[THINK] reasoning task please");
        assertTrue(m.forceThinking);
        assertFalse(m.forceWeb);
        assertEquals("reasoning task please", m.cleanedBody);
    }

    @Test
    public void bothMarkersCombine() {
        LlmMarkers m = LlmMarkers.extract("[WEB][THINK] complex query");
        assertTrue(m.forceWeb);
        assertTrue(m.forceThinking);
        assertEquals("complex query", m.cleanedBody);
    }

    @Test
    public void markersAreCaseInsensitive() {
        LlmMarkers m = LlmMarkers.extract("[web] foo [Think] bar [WEB] baz");
        assertTrue(m.forceWeb);
        assertTrue(m.forceThinking);
        assertEquals("foo bar baz", m.cleanedBody);
    }

    @Test
    public void markerInTheMiddleIsStripped() {
        LlmMarkers m = LlmMarkers.extract("вопрос [WEB] с маркером посередине");
        assertTrue(m.forceWeb);
        assertEquals("вопрос с маркером посередине", m.cleanedBody);
    }

    @Test
    public void markerAtTheEndIsStripped() {
        LlmMarkers m = LlmMarkers.extract("вопрос с маркером в конце [THINK]");
        assertTrue(m.forceThinking);
        assertEquals("вопрос с маркером в конце", m.cleanedBody);
    }

    @Test
    public void markersDoNotGlueWordsTogether() {
        // "foo[WEB]bar" should produce "foo bar", not "foobar".
        LlmMarkers m = LlmMarkers.extract("foo[WEB]bar");
        assertTrue(m.forceWeb);
        assertEquals("foo bar", m.cleanedBody);
    }

    @Test
    public void tolerantToInternalWhitespaceInMarker() {
        LlmMarkers m = LlmMarkers.extract("[ web ] hello [ THINK ]");
        assertTrue(m.forceWeb);
        assertTrue(m.forceThinking);
        assertEquals("hello", m.cleanedBody);
    }

    @Test
    public void plainWordsWithoutBracketsAreNotMarkers() {
        LlmMarkers m = LlmMarkers.extract("WEB and THINK without brackets");
        assertFalse(m.forceWeb);
        assertFalse(m.forceThinking);
        assertEquals("WEB and THINK without brackets", m.cleanedBody);
    }

    @Test
    public void unknownBracketTokensAreIgnored() {
        LlmMarkers m = LlmMarkers.extract("[search] обычный текст [reasoning]");
        assertFalse(m.forceWeb);
        assertFalse(m.forceThinking);
        assertEquals("[search] обычный текст [reasoning]", m.cleanedBody);
    }

    @Test
    public void bodyOfMarkersOnlyBecomesEmpty() {
        LlmMarkers m = LlmMarkers.extract("[WEB] [THINK]");
        assertTrue(m.forceWeb);
        assertTrue(m.forceThinking);
        assertEquals("", m.cleanedBody);
        assertTrue(m.isEmpty());
    }

    @Test
    public void multipleSpacesAroundMarkersCollapse() {
        LlmMarkers m = LlmMarkers.extract("слово1   [WEB]   слово2");
        assertEquals("слово1 слово2", m.cleanedBody);
        assertTrue(m.forceWeb);
    }

    @Test
    public void newlinesArePreservedOutsideMarkers() {
        LlmMarkers m = LlmMarkers.extract("первая строка\n[WEB]\nвторая строка");
        assertTrue(m.forceWeb);
        // The marker line collapses to a blank line; we don't enforce any
        // particular newline shape, but both surrounding lines must survive.
        assertTrue("expected first line to remain: " + m.cleanedBody,
                m.cleanedBody.contains("первая строка"));
        assertTrue("expected second line to remain: " + m.cleanedBody,
                m.cleanedBody.contains("вторая строка"));
    }
}
