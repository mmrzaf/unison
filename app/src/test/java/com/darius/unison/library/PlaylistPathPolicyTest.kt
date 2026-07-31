package com.darius.unison.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistPathPolicyTest {
    @Test
    fun normalizesSlashVariantsWithoutLosingDisplayCase() {
        val result = PlaylistPathPolicy.evaluate("Music\\Artist/Track.MP3")
        assertEquals(
            PlaylistPathPolicy.Decision.Valid(
                relativePath = "Music/Artist/Track.MP3",
                normalizedPath = "music/artist/track.mp3",
                fileName = "track.mp3",
            ),
            result,
        )
    }

    @Test
    fun rejectsPlainTraversal() {
        assertRejected("../outside.mp3")
        assertRejected("music/../../outside.mp3")
    }

    @Test
    fun rejectsEncodedTraversalAndSeparators() {
        assertRejected("%2e%2e%2foutside.mp3")
        assertRejected("%252e%252e%255coutside.mp3")
    }

    @Test
    fun rejectsUnixWindowsAndUncAbsolutePaths() {
        assertRejected("/storage/emulated/0/song.mp3")
        assertRejected("C:\\Music\\song.mp3")
        assertRejected("\\\\server\\share\\song.mp3")
    }

    @Test
    fun rejectsUriSchemesFromTreeResolution() {
        assertRejected("content://media/song")
        assertRejected("file:///tmp/song.mp3")
    }

    @Test
    fun normalizedPathCollisionsRemainAmbiguous() {
        val decision =
            PlaylistTreeMatchPolicy.decide(
                listOf("content://music/Song.mp3", "content://music/song.mp3"),
                identity = { it },
            )
        assertEquals(PlaylistTreeMatchPolicy.Decision.Ambiguous(2), decision)
    }

    private fun assertRejected(value: String) {
        assertTrue(PlaylistPathPolicy.evaluate(value) is PlaylistPathPolicy.Decision.Rejected)
    }
}
