package com.darius.unison.ui

import com.darius.unison.library.PlaylistDetail
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueAddSelectionPolicyTest {
    @Test
    fun mergesPlaylistsThenSongsWithoutAccidentalDuplicates() {
        val one = track("one")
        val two = track("two")
        val three = track("three")

        val result =
            QueueAddSelectionPolicy.merge(
                allMusicTracks = emptyList(),
                playlists =
                    listOf(
                        PlaylistDetail("a", "A", listOf(one, two)),
                        PlaylistDetail("b", "B", listOf(two, three)),
                    ),
                directTracks = listOf(three.trackId, TrackId("four")),
            )

        assertEquals(listOf(TrackId("one"), TrackId("two"), TrackId("three"), TrackId("four")), result)
    }

    @Test
    fun allMusicComesFirstAndStillDeduplicatesOtherSources() {
        val one = track("one")
        val two = track("two")
        val three = track("three")

        val result =
            QueueAddSelectionPolicy.merge(
                allMusicTracks = listOf(one.trackId, two.trackId),
                playlists = listOf(PlaylistDetail("a", "A", listOf(two, three))),
                directTracks = listOf(one.trackId, TrackId("four")),
            )

        assertEquals(
            listOf(TrackId("one"), TrackId("two"), TrackId("three"), TrackId("four")),
            result,
        )
    }

    private fun track(id: String): TrackDescriptor =
        TrackDescriptor(
            trackId = TrackId(id),
            sizeBytes = 1,
            mimeType = "audio/mpeg",
            durationMs = 1,
            title = id,
            artist = null,
            album = null,
            originalFileName = "$id.mp3",
        )
}
