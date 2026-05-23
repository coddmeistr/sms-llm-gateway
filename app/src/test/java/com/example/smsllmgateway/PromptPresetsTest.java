package com.example.smsllmgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PromptPresetsTest {

    @Test
    public void defaultPresetExists() {
        PromptPresets.Preset preset = PromptPresets.byId("default");
        assertNotNull(preset);
        assertEquals("default", preset.id);
    }

    @Test
    public void unknownIdFallsBackToFirstPreset() {
        PromptPresets.Preset preset = PromptPresets.byId("definitely-not-a-preset");
        assertNotNull(preset);
        // Falls back to the first registered preset, which is "default".
        assertEquals("default", preset.id);
    }

    @Test
    public void nullIdFallsBackToDefault() {
        PromptPresets.Preset preset = PromptPresets.byId(null);
        assertNotNull(preset);
        assertEquals("default", preset.id);
    }

    @Test
    public void isKnownIsCaseInsensitive() {
        assertTrue(PromptPresets.isKnown("coder"));
        assertTrue(PromptPresets.isKnown("CODER"));
        assertTrue(PromptPresets.isKnown("  Coder  "));
        assertFalse(PromptPresets.isKnown("nonexistent"));
        assertFalse(PromptPresets.isKnown(null));
    }

    @Test
    public void allKnownPresetsResolve() {
        for (PromptPresets.Preset preset : PromptPresets.all()) {
            assertTrue("id should be known: " + preset.id, PromptPresets.isKnown(preset.id));
            PromptPresets.Preset resolved = PromptPresets.byId(preset.id);
            assertEquals(preset.id, resolved.id);
        }
    }

    @Test
    public void composeSystemPromptAppendsSmsRules() {
        String prompt = PromptPresets.composeSystemPrompt("brief", 700, 220);
        assertTrue(prompt.contains("at most 700 characters"));
        assertTrue(prompt.contains("plain text only"));
        assertTrue(prompt.contains("no emoji"));
        assertFalse("must not contain a markdown fence: " + prompt, prompt.contains("```"));
    }

    @Test
    public void composeSystemPromptStartsWithPresetPrompt() {
        PromptPresets.Preset coder = PromptPresets.byId("coder");
        String prompt = PromptPresets.composeSystemPrompt("coder", 500, 200);
        assertTrue(prompt.startsWith(coder.prompt));
    }

    @Test
    public void idsCsvListsAllPresets() {
        String csv = PromptPresets.idsCsv();
        for (PromptPresets.Preset preset : PromptPresets.all()) {
            assertTrue("preset missing in csv: " + preset.id, csv.contains(preset.id));
        }
    }
}
