package com.darius.unison.library

import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uResolutionPolicyTest {
    private fun track(id: Char, title: String) =
        TrackDescriptor(
            trackId = TrackId(id.toString().repeat(64)),
            sizeBytes = 100,
            durationMs = 1_000,
            title = title,
            originalFileName = "$title.mp3",
        )

    @Test
    fun explicitChoicePreservesPlaylistOrder() {
        val first = M3uResolvedEntry(0, M3uEntry("first.mp3"), track('a', "First"))
        val third = M3uResolvedEntry(2, M3uEntry("third.mp3"), track('c', "Third"))
        val selected = track('b', "Second")
        val ambiguity =
            M3uAmbiguousEntry(
                1,
                M3uEntry("second.mp3"),
                listOf(selected, track('d', "Other")),
            )
        val resolved =
            M3uResolutionPolicy.choose(listOf(third, first), ambiguity, selected.trackId)!!
        assertEquals(listOf("First", "Second", "Third"), resolved.map { it.track.title })
        assertEquals(
            listOf('a', 'b', 'c'),
            M3uResolutionPolicy.orderedTrackIds(resolved).map { it.value.first() },
        )
    }

    @Test
    fun rejectsChoiceOutsideCandidateSet() {
        val ambiguity = M3uAmbiguousEntry(0, M3uEntry("song.mp3"), listOf(track('a', "A")))
        assertNull(M3uResolutionPolicy.choose(emptyList(), ambiguity, TrackId("b".repeat(64))))
    }
}
