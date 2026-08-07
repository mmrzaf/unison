package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMutationCoordinatorTest {
    @Test
    fun newerTransportMakesOlderTicketStale() = runBlocking {
        val player = FakePlayer()
        val coordinator = PlayerMutationCoordinator(player)
        val (oldTicket, _) = coordinator.beginTransport("old")
        val (newTicket, superseded) = coordinator.beginTransport("new")

        assertEquals("old", superseded)
        assertEquals(
            PlayerMutationCoordinator.ExecutionResult.STALE,
            coordinator.executeTransport(oldTicket) { play() },
        )
        assertEquals(
            PlayerMutationCoordinator.ExecutionResult.SUCCESS,
            coordinator.executeTransport(newTicket) { play() },
        )
        assertEquals(1, player.playCalls)
    }

    @Test
    fun synchronizationCannotInterleaveWithPendingTransport() = runBlocking {
        val player = FakePlayer()
        val coordinator = PlayerMutationCoordinator(player)
        val (ticket, _) = coordinator.beginTransport("play")

        assertFalse(coordinator.synchronize { setPlaybackSpeed(1.01f) })
        assertEquals(0, player.speedCalls)
        coordinator.executeTransport(ticket) { play() }
        assertTrue(coordinator.synchronize { setPlaybackSpeed(1.01f) })
        assertEquals(1, player.speedCalls)
    }

    @Test
    fun timelineMaintenanceDefersWhileTransportIsPending() = runBlocking {
        val player = FakePlayer()
        val coordinator = PlayerMutationCoordinator(player)
        val (ticket, _) = coordinator.beginTransport("next")

        assertFalse(coordinator.maintenanceIfTransportIdle { setQueue(emptyList(), null, 0L) })
        assertEquals(0, player.queueCalls)

        coordinator.executeTransport(ticket) { play() }
        assertTrue(coordinator.maintenanceIfTransportIdle { setQueue(emptyList(), null, 0L) })
        assertEquals(1, player.queueCalls)
    }

    @Test
    fun failedActionReleasesTransportOwnership() = runBlocking {
        val player = FakePlayer()
        val coordinator = PlayerMutationCoordinator(player)
        val (ticket, _) = coordinator.beginTransport("broken")

        try {
            coordinator.executeTransport(ticket) { error("boom") }
        } catch (_: IllegalStateException) {
            // Expected: ownership cleanup is the behavior under test.
        }

        assertFalse(coordinator.hasPendingTransport)
        assertTrue(coordinator.synchronize { pause(PlaybackPauseCause.CANONICAL_RECONCILIATION) })
    }

    private class FakePlayer : PlayerPort {
        override val state: StateFlow<PlayerState> = MutableStateFlow(PlayerState())
        var playCalls = 0
        var speedCalls = 0
        var queueCalls = 0

        override suspend fun samplePlayback(): PlaybackSample =
            PlaybackSample(
                queueItemId = null,
                positionMs = 0,
                durationMs = 0,
                sampledAtLocalNs = 0,
                playWhenReady = false,
                isPlaying = false,
                activityState = PlaybackActivityState.IDLE,
                playbackSpeed = 1f,
                outputRoute = AudioOutputRoute.UNKNOWN,
                seekRevision = 0,
            )

        override suspend fun setQueue(
            items: List<LocalPlayableItem>,
            currentQueueItemId: QueueItemId?,
            positionMs: Long,
        ) {
            queueCalls++
        }

        override suspend fun play(): Boolean {
            playCalls++
            return true
        }

        override suspend fun beginLocalRejoin() = Unit

        override suspend fun completeLocalRejoin() = Unit

        override suspend fun pause(cause: PlaybackPauseCause) = Unit

        override suspend fun seekTo(positionMs: Long) = Unit

        override suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean = true

        override suspend fun setRepeatCurrentItem(enabled: Boolean) = Unit

        override suspend fun setPlaybackSpeed(speed: Float) {
            speedCalls++
        }
    }
}
