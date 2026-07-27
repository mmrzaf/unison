package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.ProtocolBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueuePolicyTest {
    private val peer = PeerId("peer")
    private fun track(char: Char, title: String) = TrackDescriptor(
        trackId = TrackId(char.toString().repeat(64)),
        sizeBytes = 100,
        durationMs = 180_000,
        title = title,
    )

    private val first = QueueItem.create(track('a', "First"), peer)
    private val second = QueueItem.create(track('b', "Second"), peer)
    private val third = QueueItem.create(track('c', "Third"), peer)

    private fun snapshot(wait: Boolean = false, prepared: Set<QueueItemId> = emptySet()) = RoomSnapshot(
        roomId = "room",
        roomName = "Room",
        term = CoordinatorTerm(1, peer),
        sequence = 1,
        options = RoomOptions(waitAtTrackBoundary = wait),
        members = listOf(MemberSnapshot(peer, "Peer")),
        queue = listOf(first, second, third),
        preparedQueueItemIds = prepared,
        playback = CanonicalPlaybackState(first.queueItemId, 0, 1, isPlaying = true),
    )

    @Test
    fun missingMiddleTrackCannotBeSkipped() {
        val playable = PlaybackQueuePolicy.playableItems(
            snapshot(),
            setOf(first.track.trackId, third.track.trackId),
        )
        assertEquals(listOf(first.queueItemId), playable.map { it.queueItemId })
    }

    @Test
    fun readableHistoryIsKeptButFutureMustBeContiguous() {
        val room = snapshot().copy(playback = CanonicalPlaybackState(second.queueItemId, 0, 1, true))
        val playable = PlaybackQueuePolicy.playableItems(
            room,
            setOf(first.track.trackId, second.track.trackId),
        )
        assertEquals(listOf(first.queueItemId, second.queueItemId), playable.map { it.queueItemId })
    }

    @Test
    fun boundaryModeOnlyExposesPreparedItemsAndCurrent() {
        val playable = PlaybackQueuePolicy.playableItems(
            snapshot(wait = true, prepared = setOf(first.queueItemId, third.queueItemId)),
            setOf(first.track.trackId, second.track.trackId, third.track.trackId),
        )
        assertEquals(listOf(first.queueItemId, third.queueItemId), playable.map { it.queueItemId })
    }

    @Test
    fun naturalEndWaitsOnUnpreparedNextTrack() {
        val plan = PlaybackQueuePolicy.planNaturalEnd(snapshot(), first.queueItemId, 179_990, 180_000, 5_000)!!
        val change = plan.mutation as ProtocolBody.CurrentItemChanged
        assertEquals(second.queueItemId, change.queueItemId)
        assertFalse(change.resumePlayback)
        assertEquals(second.queueItemId, plan.waitForQueueItemId)
    }

    @Test
    fun naturalEndSchedulesPreparedNextTrack() {
        val plan = PlaybackQueuePolicy.planNaturalEnd(
            snapshot(prepared = setOf(second.queueItemId)), first.queueItemId, 180_000, 180_000, 5_000, 1_000,
        )!!
        val change = plan.mutation as ProtocolBody.CurrentItemChanged
        assertTrue(change.resumePlayback)
        assertEquals(6_000, change.executeAtCoordinatorNs)
        assertNull(plan.waitForQueueItemId)
    }

    @Test
    fun finalTrackEndsPausedAtDuration() {
        val room = snapshot().copy(
            queue = listOf(first),
            playback = CanonicalPlaybackState(first.queueItemId, 0, 1, true),
        )
        val plan = PlaybackQueuePolicy.planNaturalEnd(room, first.queueItemId, 179_999, 180_000, 5_000)!!
        val pause = plan.mutation as ProtocolBody.PauseScheduled
        assertEquals(180_000, pause.positionMs)
        assertNull(plan.waitForQueueItemId)
    }
    @Test
    fun playerWindowKeepsSmallHistoryAndUpcomingRange() {
        val queue = (0 until 30).map { index ->
            QueueItem.create(
                track(('a'.code + (index % 20)).toChar(), "Track $index").copy(
                    trackId = TrackId(index.toString(16).padStart(64, '0'))
                ),
                peer,
            )
        }
        val room = snapshot().copy(
            queue = queue,
            playback = CanonicalPlaybackState(queue[15].queueItemId, 0, 1, true),
        )
        val window = PlaybackQueuePolicy.playerWindow(room, historyCount = 2, upcomingCount = 5)
        assertEquals(queue.subList(13, 21).map { it.queueItemId }, window.map { it.queueItemId })
    }

    @Test
    fun repeatOneRestartsCurrentTrack() {
        val room = snapshot(prepared = setOf(first.queueItemId)).copy(repeatMode = RepeatMode.ONE)
        val plan = PlaybackQueuePolicy.planNaturalEnd(room, first.queueItemId, 180_000, 180_000, 5_000, 1_000)!!
        val change = plan.mutation as ProtocolBody.CurrentItemChanged
        assertEquals(first.queueItemId, change.queueItemId)
        assertTrue(change.resumePlayback)
    }

    @Test
    fun repeatOneCanonicalizesAMiddleTrackPlayerLoop() {
        val room = snapshot(prepared = setOf(second.queueItemId)).copy(
            playback = CanonicalPlaybackState(second.queueItemId, 0, 1, true),
            repeatMode = RepeatMode.ONE,
        )
        val change = PlaybackQueuePolicy.planRepeatTransition(
            snapshot = room,
            repeatedQueueItemId = second.queueItemId,
            positionMs = 25,
            coordinatorNowNs = 30_000_000,
        )

        requireNotNull(change)
        assertEquals(second.queueItemId, change.queueItemId)
        assertEquals(0, change.positionMs)
        assertEquals(5_000_000, change.executeAtCoordinatorNs)
        assertTrue(change.resumePlayback)
    }

    @Test
    fun repeatAllWrapsAtQueueEnd() {
        val room = snapshot(prepared = setOf(first.queueItemId)).copy(
            playback = CanonicalPlaybackState(third.queueItemId, 0, 1, true),
            repeatMode = RepeatMode.ALL,
        )
        val plan = PlaybackQueuePolicy.planNaturalEnd(room, third.queueItemId, 180_000, 180_000, 5_000, 1_000)!!
        val change = plan.mutation as ProtocolBody.CurrentItemChanged
        assertEquals(first.queueItemId, change.queueItemId)
    }

}
