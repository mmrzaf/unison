package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.playback.AudioOutputRoute
import com.darius.unison.playback.LocalPlayableItem
import com.darius.unison.playback.PlaybackActivityState
import com.darius.unison.playback.PlaybackSample
import com.darius.unison.playback.PlayerMutationCoordinator
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlaybackPauseCause
import com.darius.unison.playback.PlayerState
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.sync.SynchronizationDiagnostics
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
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackParticipationCoordinatorTest {
    @Test
    fun interruptedOldSongRejoinsLatestCanonicalSongAndBecomesActiveImmediately() = runBlocking {
        val clock = MutableClock(2_000_000_000L)
        val player =
            FakePlayer(
                PlayerState(
                    queueItemId = QueueItemId("song-a-item"),
                    positionMs = 20_000L,
                    prepared = true,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.AUDIO_FOCUS,
                )
            )
        val mutations = PlayerMutationCoordinator(player)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val log = DiagnosticLog(File.createTempFile("unison-rejoin-test", ".ndjson"))
        val syncDiagnostics = SynchronizationDiagnostics(scope, log)
        val published = mutableListOf<ProtocolBody.PlaybackStatusReport>()
        val refreshed = mutableListOf<Pair<QueueItemId, Long>>()
        var clearedDrift = 0
        try {
            val coordinator =
                LocalPlaybackParticipationCoordinator(
                    player = player,
                    playerMutations = mutations,
                    clock = clock,
                    clockSync = ClockSyncEngine(clock),
                    playbackSession = PlaybackSessionCoordinator(1L, 1L, 1L),
                    isCoordinator = { true },
                    refreshPlayerQueue = { _, itemId, positionMs ->
                        refreshed += itemId to positionMs
                    },
                    executeImmediatePlay = { commandId, block ->
                        val (ticket, _) = mutations.beginTransport(commandId)
                        mutations.executeTransport(ticket, block)
                    },
                    playbackSynchronization = PlaybackSynchronizationRuntime(),
                    syncDiagnostics = syncDiagnostics,
                    clearLocalDrift = { clearedDrift++ },
                    publishStatus = { published += it },
                    onCoordinatorCohortChanged = {},
                    setError = { error(it) },
                    diagnostics = RoomDiagnostics(log),
                )
            val liveItem = QueueItemId("song-c-item")
            val snapshot = snapshot(liveItem)

            coordinator.rejoin("rejoin-command", snapshot)

            assertEquals(listOf(liveItem to 81_000L), refreshed)
            assertEquals(liveItem, player.state.value.queueItemId)
            assertEquals(81_000L, player.state.value.positionMs)
            assertTrue(player.state.value.playWhenReady)
            assertEquals(LocalPlaybackParticipation.ACTIVE, player.state.value.participation)
            assertEquals(null, player.state.value.inhibitionReason)
            assertEquals(1, clearedDrift)
            assertEquals(LocalPlaybackParticipation.ACTIVE, published.single().participation)
        } finally {
            syncDiagnostics.close()
            log.close()
            scope.cancel()
        }
    }

    @Test
    fun sessionBoundaryClearsStaleInhibitionWithoutStartingPlayback() = runBlocking {
        val clock = MutableClock(2_000_000_000L)
        val player =
            FakePlayer(
                PlayerState(
                    queueItemId = QueueItemId("song-a-item"),
                    positionMs = 20_000L,
                    prepared = true,
                    playWhenReady = false,
                    isPlaying = false,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.BECOMING_NOISY,
                )
            )
        val mutations = PlayerMutationCoordinator(player)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val log = DiagnosticLog(File.createTempFile("unison-session-reset-test", ".ndjson"))
        val syncDiagnostics = SynchronizationDiagnostics(scope, log)
        try {
            val coordinator =
                LocalPlaybackParticipationCoordinator(
                    player = player,
                    playerMutations = mutations,
                    clock = clock,
                    clockSync = ClockSyncEngine(clock),
                    playbackSession = PlaybackSessionCoordinator(1L, 1L, 1L),
                    isCoordinator = { true },
                    refreshPlayerQueue = { _, _, _ -> },
                    executeImmediatePlay = { _, _ -> error("reset must not start playback") },
                    playbackSynchronization = PlaybackSynchronizationRuntime(),
                    syncDiagnostics = syncDiagnostics,
                    clearLocalDrift = {},
                    publishStatus = {},
                    onCoordinatorCohortChanged = {},
                    setError = { error(it) },
                    diagnostics = RoomDiagnostics(log),
                )

            coordinator.resetForSessionBoundary()

            assertEquals(LocalPlaybackParticipation.ACTIVE, player.state.value.participation)
            assertEquals(null, player.state.value.inhibitionReason)
            assertEquals(false, player.state.value.playWhenReady)
            assertEquals(false, player.state.value.isPlaying)
        } finally {
            syncDiagnostics.close()
            log.close()
            scope.cancel()
        }
    }

    private fun snapshot(liveItem: QueueItemId): RoomSnapshot {
        val coordinator = PeerId("coordinator")
        val item =
            QueueItem(
                queueItemId = liveItem,
                track = TrackDescriptor(TrackId("c".repeat(64)), 1024L, durationMs = 180_000L),
                addedByPeerId = coordinator,
                addedAtSequence = 1L,
            )
        return RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1L, coordinator),
            sequence = 10L,
            queue = listOf(item),
            playback =
                CanonicalPlaybackState(
                    queueItemId = liveItem,
                    positionAtTimestampMs = 80_000L,
                    coordinatorTimestampNs = 1_000_000_000L,
                    isPlaying = true,
                    revision = 10L,
                ),
            queueRevision = 5L,
        )
    }

    private class MutableClock(var value: Long) : MonotonicClock {
        override fun nowNs(): Long = value
    }

    private class FakePlayer(initial: PlayerState) : PlayerPort {
        private val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<PlayerState> = mutableState

        override suspend fun samplePlayback(): PlaybackSample =
            PlaybackSample(
                queueItemId = state.value.queueItemId,
                positionMs = state.value.positionMs,
                durationMs = state.value.durationMs,
                sampledAtLocalNs = 0L,
                playWhenReady = state.value.playWhenReady,
                isPlaying = state.value.isPlaying,
                activityState = PlaybackActivityState.READY_PLAYING,
                playbackSpeed = state.value.playbackSpeed,
                outputRoute = AudioOutputRoute.UNKNOWN,
                seekRevision = 0L,
            )

        override suspend fun setQueue(
            items: List<LocalPlayableItem>,
            currentQueueItemId: QueueItemId?,
            positionMs: Long,
        ) {
            mutableState.value =
                mutableState.value.copy(
                    queueItemId = currentQueueItemId,
                    positionMs = positionMs,
                    prepared = currentQueueItemId != null,
                )
        }

        override suspend fun play(): Boolean {
            mutableState.value = mutableState.value.copy(playWhenReady = true, isPlaying = true)
            return true
        }

        override suspend fun rejoinLivePlayback(
            queueItemId: QueueItemId,
            positionMs: Long,
        ): Boolean {
            if (mutableState.value.participation != LocalPlaybackParticipation.OUTPUT_INHIBITED) {
                return false
            }
            mutableState.value =
                mutableState.value.copy(
                    queueItemId = queueItemId,
                    positionMs = positionMs,
                    prepared = true,
                    playWhenReady = true,
                    isPlaying = true,
                    participation = LocalPlaybackParticipation.ACTIVE,
                    inhibitionReason = null,
                )
            return true
        }

        override suspend fun resetLocalPlaybackParticipation() {
            mutableState.value =
                mutableState.value.copy(
                    participation = LocalPlaybackParticipation.ACTIVE,
                    inhibitionReason = null,
                )
        }

        override suspend fun pause(cause: PlaybackPauseCause) {
            mutableState.value = mutableState.value.copy(playWhenReady = false, isPlaying = false)
        }

        override suspend fun seekTo(positionMs: Long) {
            mutableState.value = mutableState.value.copy(positionMs = positionMs)
        }

        override suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean {
            mutableState.value =
                mutableState.value.copy(
                    queueItemId = queueItemId,
                    positionMs = positionMs,
                    prepared = true,
                )
            return true
        }

        override suspend fun setRepeatCurrentItem(enabled: Boolean) = Unit

        override suspend fun setPlaybackSpeed(speed: Float) {
            mutableState.value = mutableState.value.copy(playbackSpeed = speed)
        }
    }
}
