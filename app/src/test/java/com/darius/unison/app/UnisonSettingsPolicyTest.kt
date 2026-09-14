package com.darius.unison.app

import com.darius.unison.model.DEFAULT_DISPLAY_NAME
import org.junit.Assert.assertEquals
import org.junit.Test

class UnisonSettingsPolicyTest {
    @Test
    fun displayNameIsCanonicalBeforePersistenceAndRuntimeUse() {
        assertEquals("Alice", normalizeDisplayName("  Alice  "))
        assertEquals("AliceBob", normalizeDisplayName("Alice\nBob"))
        assertEquals(DEFAULT_DISPLAY_NAME, normalizeDisplayName(" \n\t "))
        assertEquals("a".repeat(40), normalizeDisplayName("a".repeat(80)))
    }
}
