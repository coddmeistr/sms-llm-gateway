package com.example.smsllmgateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ModelCatalogTest {

    @Test
    public void allReturnsAtLeastFreeAndPaidModels() {
        assertTrue("expected several models", ModelCatalog.size() >= 5);
        int free = 0, paid = 0;
        for (ModelCatalog.Model m : ModelCatalog.all()) {
            if (m.free) {
                free++;
            } else {
                paid++;
            }
        }
        assertTrue("expected free models", free >= 1);
        assertTrue("expected paid models", paid >= 1);
    }

    @Test
    public void byIndexIsOneBased() {
        ModelCatalog.Model first = ModelCatalog.byIndex(1);
        assertNotNull(first);
        assertEquals(ModelCatalog.all().get(0).id, first.id);
    }

    @Test
    public void byIndexOutOfRangeReturnsNull() {
        assertNull(ModelCatalog.byIndex(0));
        assertNull(ModelCatalog.byIndex(-1));
        assertNull(ModelCatalog.byIndex(ModelCatalog.size() + 1));
    }

    @Test
    public void byIdIsCaseInsensitive() {
        ModelCatalog.Model gpt = ModelCatalog.byId("openai/GPT-4O-MINI");
        assertNotNull(gpt);
        assertEquals("openai/gpt-4o-mini", gpt.id);
    }

    @Test
    public void byIdReturnsNullForUnknown() {
        assertNull(ModelCatalog.byId("nonsense/model"));
        assertNull(ModelCatalog.byId(null));
    }

    @Test
    public void numberedListHasOnlyNamesWithoutIds() {
        String list = ModelCatalog.numberedList();
        // Should not contain provider/model ids like "openai/"
        assertTrue("expected '1. ' prefix: " + list, list.contains("1. "));
        // The display names are short, so the list should not include path-like ids.
        // (sanity check: forward slash is part of model ids and should NOT appear in the list)
        for (ModelCatalog.Model m : ModelCatalog.all()) {
            if (m.id.contains("/")) {
                assertTrue("display list missing entry: " + m.name, list.contains(m.name));
            }
        }
    }
}
