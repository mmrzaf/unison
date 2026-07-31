package com.darius.unison.library

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uCodecTest {
    @Test
    fun extendedM3uRoundTrips() {
        val original =
            listOf(
                M3uEntry("Music/song one.mp3", 201, "Artist - Song One"),
                M3uEntry("content://media/song-two", null, "Song Two"),
            )
        val encoded = M3uCodec.encode(original)
        assertEquals(original, M3uCodec.parse(StringReader(encoded)).entries)
    }

    @Test
    fun parserIgnoresCommentsAndBom() {
        val parsed = M3uCodec.parse(StringReader("\uFEFF#EXTM3U\n# comment\ntrack.mp3\n"))
        assertEquals(listOf(M3uEntry("track.mp3")), parsed.entries)
    }

    @Test
    fun parserRejectsOversizedLines() {
        val oversized = "a".repeat(M3uCodec.MAX_LINE_LENGTH + 1)
        assertIllegalArgument {
            M3uCodec.parse(StringReader(oversized))
        }
    }

    @Test
    fun parserRejectsTooManyEntries() {
        val playlist = buildString {
            repeat(M3uCodec.MAX_ENTRIES + 1) { appendLine("track-$it.mp3") }
        }
        assertIllegalArgument {
            M3uCodec.parse(StringReader(playlist))
        }
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
