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
import com.darius.unison.playback.PlaybackPauseCause
import com.darius.unison.playback.PlaybackSample
import com.darius.unison.playback.PlayerExecutor
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.sync.ClockSyncConfig
import com.darius.unison.sync.ClockSyncEngine
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

class LocalPlaybackParticipationCoordinatorTest {
    @Test
    fun manualRejoinUsesLatestCanonicalSongAndBecomesActive() = runBlocking {
        val harness = Harness(isLocalCoordinator = true)
        try {
            harness.player.update(
                PlayerState(
                    queueItemId = QueueItemId("song-a-item"),
                    positionMs = 20_000L,
                    prepared = true,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.BECOMING_NOISY,
                )
            )
            harness.coordinator.observe(harness.player.state.value, harness.snapshot)
            harness.coordinator.requestManualRejoin("rejoin-command")
            harness.coordinator.tryPendingRejoin()

            assertEquals(listOf(harness.liveItem to 81_000L), harness.refreshed)
            assertEquals(harness.liveItem, harness.player.state.value.queueItemId)
            assertEquals(81_000L, harness.player.state.value.positionMs)
            assertTrue(harness.player.state.value.playWhenReady)
            assertEquals(
                LocalPlaybackParticipation.ACTIVE,
                harness.player.state.value.participation,
            )
            assertEquals(null, harness.player.state.value.inhibitionReason)
            assertEquals(1, harness.clearedDrift)
            assertEquals(
                LocalPlaybackParticipation.ACTIVE,
                harness.published.single().participation,
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun transientAudioFocusAutomaticallyRejoinsOnlyAfterSuppressionClears() = runBlocking {
        val harness = Harness(isLocalCoordinator = true)
        try {
            harness.player.update(
                PlayerState(
                    queueItemId = harness.liveItem,
                    positionMs = 80_000L,
                    prepared = true,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.AUDIO_FOCUS,
                    outputResumeBlocked = true,
                )
            )
            harness.coordinator.observe(harness.player.state.value, harness.snapshot)
            harness.coordinator.tryPendingRejoin()
            assertFalse(harness.player.state.value.playWhenReady)
            assertEquals(0, harness.executions)

            harness.clock.value += 300_000_000L
            harness.player.update(harness.player.state.value.copy(outputResumeBlocked = false))
            harness.coordinator.observe(harness.player.state.value, harness.snapshot)
            harness.coordinator.tryPendingRejoin()

            assertEquals(1, harness.executions)
            assertTrue(harness.player.state.value.playWhenReady)
            assertEquals(
                LocalPlaybackParticipation.ACTIVE,
                harness.player.state.value.participation,
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun manualRejoinWaitsForParticipantClockAndThenSucceedsWithoutSecondCommand() = runBlocking {
        val harness = Harness(isLocalCoordinator = false)
        try {
            harness.player.update(
                PlayerState(
                    queueItemId = harness.liveItem,
                    positionMs = 80_000L,
                    prepared = true,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.AUDIO_FOCUS,
                    outputResumeBlocked = false,
                )
            )
            harness.coordinator.observe(harness.player.state.value, harness.snapshot)
            harness.coordinator.requestManualRejoin("manual-while-clock-down")
            harness.coordinator.tryPendingRejoin()
            assertEquals(0, harness.executions)
            assertEquals(
                LocalPlaybackParticipation.OUTPUT_INHIBITED,
                harness.player.state.value.participation,
            )

            harness.lockParticipantClock()
            harness.clock.value += 300_000_000L
            harness.coordinator.tryPendingRejoin()

            assertEquals(1, harness.executions)
            assertEquals(
                LocalPlaybackParticipation.ACTIVE,
                harness.player.state.value.participation,
            )
            assertTrue(harness.player.state.value.playWhenReady)
        } finally {
            harness.close()
        }
    }

    @Test
    fun becomingNoisyNeverCreatesAutomaticResume() = runBlocking {
        val harness = Harness(isLocalCoordinator = true)
        try {
            harness.player.update(
                PlayerState(
                    queueItemId = harness.liveItem,
                    positionMs = 80_000L,
                    prepared = true,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.BECOMING_NOISY,
                    outputResumeBlocked = false,
                )
            )
            harness.coordinator.observe(harness.player.state.value, harness.snapshot)
            harness.coordinator.tryPendingRejoin()

            assertEquals(0, harness.executions)
            assertFalse(harness.player.state.value.playWhenReady)
            assertEquals(
                LocalPlaybackParticipation.OUTPUT_INHIBITED,
                harness.player.state.value.participation,
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun newerNoisyInterruptionCancelsInFlightAutomaticFocusRejoin() = runBlocking {
        val harness = Harness(isLocalCoordinator = true)
        try {
            harness.player.update(
                PlayerState(
                    queueItemId = harness.liveItem,
                    positionMs = 80_000L,
                    prepared = true,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.AUDIO_FOCUS,
                    outputResumeBlocked = false,
                )
            )
            harness.coordinator.observe(harness.player.state.value, harness.snapshot)
            harness.refreshHook = {
                harness.player.update(
                    harness.player.state.value.copy(
                        participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                        inhibitionReason = LocalPlaybackInhibitionReason.BECOMING_NOISY,
                        outputResumeBlocked = false,
                    )
                )
                harness.coordinator.observe(harness.player.state.value, harness.snapshot)
            }

            harness.coordinator.tryPendingRejoin()

            assertEquals(0, harness.executions)
            assertFalse(harness.player.state.value.playWhenReady)
            assertEquals(
                LocalPlaybackInhibitionReason.BECOMING_NOISY,
                harness.player.state.value.inhibitionReason,
            )
        } finally {
            harness.close()
        }
    }

    @Test
    fun sessionBoundaryClearsStaleNoisyInhibitionWithoutStartingPlayback() = runBlocking {
        val harness = Harness(isLocalCoordinator = true)
        try {
            harness.player.update(
                PlayerState(
                    queueItemId = QueueItemId("song-a-item"),
                    positionMs = 20_000L,
                    prepared = true,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.BECOMING_NOISY,
                )
            )
            harness.coordinator.observe(harness.player.state.value, harness.snapshot)
            harness.coordinator.resetForSessionBoundary()

            assertEquals(
                LocalPlaybackParticipation.ACTIVE,
                harness.player.state.value.participation,
            )
            assertEquals(null, harness.player.state.value.inhibitionReason)
            assertFalse(harness.player.state.value.playWhenReady)
            assertEquals(0, harness.executions)
        } finally {
            harness.close()
        }
    }

    private class Harness(private val isLocalCoordinator: Boolean) : AutoCloseable {
        val clock = MutableClock(2_000_000_000L)
        val liveItem = QueueItemId("song-c-item")
        var snapshot = snapshot(liveItem)
        val player = FakePlayer(PlayerState())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val log = DiagnosticLog(File.createTempFile("unison-rejoin-test", ".ndjson"))
        val clockSync =
            ClockSyncEngine(
                clock,
                ClockSyncConfig(minimumLockSamples = 3, maxSamples = 8),
            )
        val executor =
            PlayerExecutor(
                player = player,
                clock = clock,
                clockSync = clockSync,
                scope = scope,
                log = log,
                onError = {},
                usesLocalCoordinatorClock = { isLocalCoordinator },
            )
        val published = mutableListOf<ProtocolBody.PlaybackStatusReport>()
        val refreshed = mutableListOf<Pair<QueueItemId, Long>>()
        var clearedDrift = 0
        var executions = 0
        var refreshHook: suspend () -> Unit = {}
        val coordinator =
            LocalPlaybackParticipationCoordinator(
                player = player,
                playerExecutor = executor,
                clock = clock,
                clockSync = clockSync,
                playbackSession = PlaybackSessionCoordinator(1L, 1L),
                isCoordinator = { isLocalCoordinator },
                snapshotProvider = { snapshot },
                isQueueItemExecutable = { true },
                refreshPlayerQueue = { _, itemId, positionMs ->
                    refreshed += itemId to positionMs
                    refreshHook()
                },
                executeRejoin = { commandId, _, block ->
                    executions++
                    executor.executeImmediateTransport(commandId, block)
                },
                resetLocalSynchronization = { clearedDrift++ },
                publishStatus = { published += it },
                onCoordinatorCohortChanged = {},
                diagnostics = RoomDiagnostics(log),
            )

        fun lockParticipantClock() {
            repeat(3) { index ->
                val sendNs = 3_000_000_000L + index * 1_000_000_000L
                clock.value = sendNs
                val ping = clockSync.createPing()
                val coordinatorReceiveNs = sendNs + 1_000_000_000L + 4_000_000L
                val coordinatorSendNs = coordinatorReceiveNs + 1_000_000L
                val receiveNs = sendNs + 10_000_000L
                clock.value = receiveNs
                check(
                    clockSync.recordPong(
                        pingId = ping.pingId,
                        echoedGuestSendNs = ping.localSendNs,
                        coordinatorReceiveNs = coordinatorReceiveNs,
                        coordinatorSendNs = coordinatorSendNs,
                        localReceiveNs = receiveNs,
                    ) != null
                )
            }
            check(clockSync.synchronized)
        }

        override fun close() {
            log.close()
            scope.cancel()
        }
    }

    private companion object {
        fun snapshot(liveItem: QueueItemId): RoomSnapshot {
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
    }

    private class MutableClock(var value: Long) : MonotonicClock {
        override fun nowNs(): Long = value
    }

    private class FakePlayer(initial: PlayerState) : PlayerPort {
        private val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<PlayerState> = mutableState

        fun update(value: PlayerState) {
            mutableState.value = value
        }

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
            val current = mutableState.value
            if (
                current.participation != LocalPlaybackParticipation.OUTPUT_INHIBITED ||
                    current.outputResumeBlocked
            )
                return false
            mutableState.value =
                current.copy(
                    queueItemId = queueItemId,
                    positionMs = positionMs,
                    prepared = true,
                    playWhenReady = true,
                    isPlaying = true,
                    participation = LocalPlaybackParticipation.ACTIVE,
                    inhibitionReason = null,
                    outputResumeBlocked = false,
                )
            return true
        }

        override suspend fun resetLocalPlaybackParticipation() {
            mutableState.value =
                mutableState.value.copy(
                    participation = LocalPlaybackParticipation.ACTIVE,
                    inhibitionReason = null,
                    outputResumeBlocked = false,
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
