package com.darius.unison.playback

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PlaybackReconciliationKeyTest {
    @Test
    fun playbackPositionAndSequenceDoNotChangeTimelineKey() {
        val first = snapshot()
        val second =
            first.copy(
                sequence = first.sequence + 99,
                playback =
                    first.playback.copy(
                        revision = first.playback.revision + 1,
                        positionAtTimestampMs = 42_000,
                        coordinatorTimestampNs = 99_000,
                    ),
            )

        assertEquals(
            PlaybackReconciliationKey.from(first, emptySet()),
            PlaybackReconciliationKey.from(second, emptySet()),
        )
    }

    @Test
    fun legacyBoundaryOptionDoesNotChangeTimelineKey() {
        val base = snapshot()
        val legacyChanged =
            base.copy(
                options = base.options.copy(waitAtTrackBoundary = !base.options.waitAtTrackBoundary)
            )

        assertEquals(
            PlaybackReconciliationKey.from(base, emptySet()),
            PlaybackReconciliationKey.from(legacyChanged, emptySet()),
        )
    }

    @Test
    fun queueReadinessAndPreparationOptionsChangeTimelineKey() {
        val base = snapshot()
        val item = base.queue.first().queueItemId
        assertNotEquals(
            PlaybackReconciliationKey.from(base, emptySet()),
            PlaybackReconciliationKey.from(
                base.copy(queueRevision = base.queueRevision + 1),
                emptySet(),
            ),
        )
        assertNotEquals(
            PlaybackReconciliationKey.from(base, emptySet()),
            PlaybackReconciliationKey.from(base, setOf(item)),
        )
        assertNotEquals(
            PlaybackReconciliationKey.from(base, emptySet()),
            PlaybackReconciliationKey.from(
                base.copy(
                    options = base.options.copy(preloadCount = base.options.preloadCount + 1)
                ),
                emptySet(),
            ),
        )
    }

    private fun snapshot(): RoomSnapshot {
        val coordinator = PeerId("coordinator")
        val item =
            QueueItem(
                queueItemId = QueueItemId("item"),
                track =
                    TrackDescriptor(
                        trackId = TrackId("track"),
                        sizeBytes = 1_000L,
                        durationMs = 10L,
                        title = "Song",
                    ),
                addedByPeerId = coordinator,
                addedAtSequence = 1,
            )
        return RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, coordinator),
            sequence = 7,
            queueRevision = 4,
            queue = listOf(item),
            playback = CanonicalPlaybackState(queueItemId = item.queueItemId, revision = 3),
            options = RoomOptions(),
        )
    }
}
