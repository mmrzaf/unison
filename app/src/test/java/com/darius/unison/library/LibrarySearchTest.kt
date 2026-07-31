package com.darius.unison.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchTest {
    @Test
    fun normalizesCaseAndRepeatedWhitespace() {
        assertEquals("måneskin rush!", normalizeSearchText("  MÅNESKIN\n  Rush!  "))
    }

    @Test
    fun emptyQueryRemainsEmpty() {
        assertEquals("", normalizeSearchText("   \t"))
    }

    @Test
    fun escapesSqlLikeMetacharactersInUserQueries() {
        assertEquals("100!% real !_mix!!", normalizeSearchQuery("  100% REAL _mix!  "))
    }
}
