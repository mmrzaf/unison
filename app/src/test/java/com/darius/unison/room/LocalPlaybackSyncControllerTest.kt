package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.playback.AudioOutputRoute
import com.darius.unison.playback.LocalPlayableItem
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackPauseCause
import com.darius.unison.playback.PlaybackSample
import com.darius.unison.playback.PlayerExecutor
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.sync.SyncAction
import com.darius.unison.sync.SyncHoldReason
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackSyncControllerTest {
    @Test
    fun participantDoesNotProjectCanonicalPositionBeforeClockLock() = runBlocking {
        val clock = MutableClock(900_000_000_000L)
        val itemId = QueueItemId("item")
        val player = FakePlayer(itemId, clock)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val log = DiagnosticLog(File.createTempFile("unison-local-sync-test", ".ndjson"))
        val clockSync = ClockSyncEngine(clock)
        val executor =
            PlayerExecutor(
                player = player,
                clock = clock,
                clockSync = clockSync,
                scope = scope,
                log = log,
                onError = {},
                usesLocalCoordinatorClock = { false },
            )
        try {
            val controller =
                LocalPlaybackSyncController(
                    player = player,
                    playerExecutor = executor,
                    clock = clock,
                    clockSync = clockSync,
                    playbackSession = PlaybackSessionCoordinator(1L, 1L),
                    synchronization = PlaybackSynchronizationRuntime(),
                )
            val snapshot = snapshot(itemId)

            val result = controller.tick(snapshot, coordinator = false, connected = true)
            assertTrue(result is LocalPlaybackSyncController.TickResult.Evaluated)
            result as LocalPlaybackSyncController.TickResult.Evaluated
            assertEquals(snapshot.playback.coordinatorTimestampNs, result.sampleCoordinatorNs)
            assertEquals(null, result.canonicalPositionMs)
            val action = result.decision.action
            assertTrue(action is SyncAction.Hold)
            assertEquals(SyncHoldReason.CLOCK_UNAVAILABLE, (action as SyncAction.Hold).reason)
        } finally {
            log.close()
            scope.cancel()
        }
    }


    @Test
    fun soloCoordinatorModeNormalizesSpeedOnceAndReacquiresWhenListenerReturns() = runBlocking {
        val clock = MutableClock(5_000_000_000L)
        val itemId = QueueItemId("item")
        val player = FakePlayer(itemId, clock, initialPlaybackSpeed = 1.03f)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val log = DiagnosticLog(File.createTempFile("unison-solo-sync-test", ".ndjson"))
        val clockSync = ClockSyncEngine(clock)
        val executor =
            PlayerExecutor(
                player = player,
                clock = clock,
                clockSync = clockSync,
                scope = scope,
                log = log,
                onError = {},
                usesLocalCoordinatorClock = { true },
            )
        try {
            val controller =
                LocalPlaybackSyncController(
                    player = player,
                    playerExecutor = executor,
                    clock = clock,
                    clockSync = clockSync,
                    playbackSession = PlaybackSessionCoordinator(1L, 1L),
                    synchronization = PlaybackSynchronizationRuntime(),
                )
            val canonical = snapshot(itemId).playback

            assertTrue(controller.setSoloCoordinatorMode(true, canonical))
            assertEquals(1f, player.state.value.playbackSpeed)
            assertEquals(1, player.speedMutationCount)
            assertFalse(controller.setSoloCoordinatorMode(true, canonical))
            assertEquals(1, player.speedMutationCount)

            assertTrue(controller.setSoloCoordinatorMode(false, canonical))
            assertEquals(1f, player.state.value.playbackSpeed)
            assertEquals(1, player.speedMutationCount)
        } finally {
            log.close()
            scope.cancel()
        }
    }

    private fun snapshot(itemId: QueueItemId): RoomSnapshot {
        val coordinator = PeerId("coordinator")
        val item =
            QueueItem(
                queueItemId = itemId,
                track = TrackDescriptor(TrackId("a".repeat(64)), 100L, durationMs = 180_000L),
                addedByPeerId = coordinator,
                addedAtSequence = 1L,
            )
        return RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1L, coordinator),
            sequence = 1L,
            queue = listOf(item),
            playback =
                CanonicalPlaybackState(
                    queueItemId = itemId,
                    positionAtTimestampMs = 10_000L,
                    coordinatorTimestampNs = 5_000_000_000L,
                    isPlaying = true,
                    revision = 1L,
                ),
            queueRevision = 1L,
        )
    }

    private class MutableClock(var value: Long) : MonotonicClock {
        override fun nowNs(): Long = value
    }

    private class FakePlayer(
        itemId: QueueItemId,
        private val clock: MonotonicClock,
        initialPlaybackSpeed: Float = 1f,
    ) : PlayerPort {
        var speedMutationCount: Int = 0
            private set
        private val mutableState =
            MutableStateFlow(
                PlayerState(
                    queueItemId = itemId,
                    positionMs = 20_000L,
                    durationMs = 180_000L,
                    playWhenReady = true,
                    isPlaying = true,
                    prepared = true,
                    activityState = PlaybackActivityState.READY_PLAYING,
                    playbackSpeed = initialPlaybackSpeed,
                )
            )
        override val state: StateFlow<PlayerState> = mutableState

        override suspend fun samplePlayback(): PlaybackSample =
            PlaybackSample(
                queueItemId = state.value.queueItemId,
                positionMs = state.value.positionMs,
                durationMs = state.value.durationMs,
                sampledAtLocalNs = clock.nowNs(),
                playWhenReady = true,
                isPlaying = true,
                activityState = PlaybackActivityState.READY_PLAYING,
                playbackSpeed = state.value.playbackSpeed,
                outputRoute = AudioOutputRoute.BUILT_IN_SPEAKER,
                seekRevision = 0L,
            )

        override suspend fun setQueue(items: List<LocalPlayableItem>, currentQueueItemId: QueueItemId?, positionMs: Long) = Unit
        override suspend fun play(): Boolean = true
        override suspend fun rejoinLivePlayback(queueItemId: QueueItemId, positionMs: Long): Boolean = false
        override suspend fun resetLocalPlaybackParticipation() = Unit
        override suspend fun pause(cause: PlaybackPauseCause) = Unit
        override suspend fun seekTo(positionMs: Long) = Unit
        override suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean = true
        override suspend fun setRepeatCurrentItem(enabled: Boolean) = Unit
        override suspend fun setPlaybackSpeed(speed: Float) {
            speedMutationCount++
            mutableState.value = mutableState.value.copy(playbackSpeed = speed)
        }
    }
}
