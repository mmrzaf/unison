package com.darius.unison.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistMatchPolicyTest {
    private val first = PlaylistMatchPolicy.Candidate("a", "song.mp3", "Song", 180_000, 1_000)
    private val second = PlaylistMatchPolicy.Candidate("b", "song.mp3", "Song Live", 240_000, 2_000)

    @Test
    fun uniqueFilenameResolves() {
        val decision = PlaylistMatchPolicy.decide("song.mp3", null, null, listOf(first))
        assertEquals(PlaylistMatchPolicy.Decision.Unique(first), decision)
    }

    @Test
    fun duplicateFilenameRemainsAmbiguous() {
        val decision = PlaylistMatchPolicy.decide("song.mp3", null, null, listOf(first, second))
        assertTrue(decision is PlaylistMatchPolicy.Decision.Ambiguous)
    }

    @Test
    fun durationNarrowsDuplicateFilename() {
        val decision = PlaylistMatchPolicy.decide("song.mp3", null, 180, listOf(first, second))
        assertEquals(PlaylistMatchPolicy.Decision.Unique(first), decision)
    }

    @Test
    fun titleNarrowsDuplicateFilename() {
        val decision =
            PlaylistMatchPolicy.decide("song.mp3", "Song Live", null, listOf(first, second))
        assertEquals(PlaylistMatchPolicy.Decision.Unique(second), decision)
    }

    @Test
    fun unrelatedCandidatesAreMissing() {
        val decision = PlaylistMatchPolicy.decide("other.mp3", "Other", null, listOf(first, second))
        assertEquals(PlaylistMatchPolicy.Decision.Missing, decision)
    }
}
