package com.darius.unison.library

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class M3uCodecTest {
    @Test
    fun extendedM3uRoundTrips() {
        val original = listOf(
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
}
