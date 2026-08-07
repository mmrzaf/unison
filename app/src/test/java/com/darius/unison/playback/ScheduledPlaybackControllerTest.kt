package com.darius.unison.playback

import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.sync.ClockSyncConfig
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ScheduledPlaybackControllerTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    private val itemId = QueueItemId("queue-item")

    @Test
    fun pauseNeverSeeksOrRebuildsDecoderPosition() = runBlocking {
        val player =
            FakePlayer(
                PlayerState(
                    queueItemId = itemId,
                    positionMs = 4_000L,
                    prepared = true,
                    playWhenReady = true,
                )
            )
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            controller(player, scope) { id, phase, _ ->
                    if (id == "pause" && phase == TransportCommandPhase.SETTLED)
                        settled.complete(Unit)
                }
                .schedulePause(itemId, 4_000L, 0L, "pause")

            withTimeout(2_000L) { settled.await() }
            assertEquals(1, player.pauseCalls)
            assertEquals(0, player.seekItemCalls)
            assertEquals(0, player.seekCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun playOnSamePositionDoesNotSeek() = runBlocking {
        val player =
            FakePlayer(PlayerState(queueItemId = itemId, positionMs = 10_000L, prepared = true))
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            controller(player, scope) { id, phase, _ ->
                    if (id == "play" && phase == TransportCommandPhase.SETTLED)
                        settled.complete(Unit)
                }
                .schedulePlay(itemId, 10_000L, 0L, "play")

            withTimeout(2_000L) { settled.await() }
            assertEquals(1, player.playCalls)
            assertEquals(0, player.seekItemCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun alreadyPlayingAlignedItemSettlesWithoutPlayerMutation() = runBlocking {
        val player =
            FakePlayer(
                PlayerState(
                    queueItemId = itemId,
                    positionMs = 10_000L,
                    prepared = true,
                    playWhenReady = true,
                    isPlaying = true,
                )
            )
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            controller(player, scope) { id, phase, _ ->
                    if (id == "aligned" && phase == TransportCommandPhase.SETTLED)
                        settled.complete(Unit)
                }
                .schedulePlay(itemId, 10_000L, 0L, "aligned")

            withTimeout(2_000L) { settled.await() }
            assertEquals(0, player.playCalls)
            assertEquals(0, player.pauseCalls)
            assertEquals(0, player.seekItemCalls)
            assertEquals(0, player.speedCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun alreadyPausedItemSettlesWithoutPlayerMutation() = runBlocking {
        val player =
            FakePlayer(PlayerState(queueItemId = itemId, positionMs = 10_000L, prepared = true))
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            controller(player, scope) { id, phase, _ ->
                    if (id == "paused" && phase == TransportCommandPhase.SETTLED)
                        settled.complete(Unit)
                }
                .schedulePause(itemId, 10_000L, 0L, "paused")

            withTimeout(2_000L) { settled.await() }
            assertEquals(0, player.pauseCalls)
            assertEquals(0, player.seekItemCalls)
            assertEquals(0, player.speedCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun newerScheduledCommandSupersedesOlderCommand() = runBlocking {
        val player =
            FakePlayer(
                PlayerState(
                    queueItemId = itemId,
                    positionMs = 0L,
                    prepared = true,
                    playWhenReady = true,
                    isPlaying = true,
                )
            )
        val superseded = CompletableDeferred<Unit>()
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller =
                controller(player, scope) { id, phase, _ ->
                    if (id == "old" && phase == TransportCommandPhase.SUPERSEDED)
                        superseded.complete(Unit)
                    if (id == "new" && phase == TransportCommandPhase.SETTLED)
                        settled.complete(Unit)
                }
            controller.schedulePlay(itemId, 0L, 10_000_000_000L, "old")
            controller.schedulePause(itemId, 0L, 0L, "new")

            withTimeout(2_000L) {
                superseded.await()
                settled.await()
            }
            assertEquals(0, player.playCalls)
            assertEquals(1, player.pauseCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun commandLifecycleIsOrderedAndCorrelated() = runBlocking {
        val player = FakePlayer(PlayerState(queueItemId = itemId, positionMs = 0L, prepared = true))
        val phases = mutableListOf<TransportCommandPhase>()
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            controller(player, scope) { id, phase, _ ->
                    if (id == "ordered") {
                        synchronized(phases) { phases += phase }
                        if (phase == TransportCommandPhase.SETTLED) settled.complete(Unit)
                    }
                }
                .schedulePlay(itemId, 0L, 0L, "ordered")

            withTimeout(2_000L) { settled.await() }
            assertEquals(
                listOf(
                    TransportCommandPhase.SCHEDULED,
                    TransportCommandPhase.EXECUTING,
                    TransportCommandPhase.SETTLED,
                ),
                synchronized(phases) { phases.toList() },
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun seekBurstExecutesOnlyLatestAbsoluteTarget() = runBlocking {
        val player = FakePlayer(PlayerState(queueItemId = itemId, positionMs = 0L, prepared = true))
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller =
                controller(player, scope) { id, phase, _ ->
                    if (id == "seek-9" && phase == TransportCommandPhase.SETTLED)
                        settled.complete(Unit)
                }
            repeat(10) { index ->
                controller.scheduleSeek(
                    queueItemId = itemId,
                    positionMs = index * 1_000L,
                    resume = true,
                    executeAtCoordinatorNs = if (index == 9) 0L else 10_000_000_000L,
                    commandId = "seek-$index",
                )
            }

            withTimeout(2_000L) { settled.await() }
            assertEquals(1, player.seekItemCalls)
            assertEquals(9_000L, player.lastSeekItemPositionMs)
            assertTrue(player.playCalls <= 1)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun scheduledActionWaitsForClockReacquisitionAndExecutesOnce() = runBlocking {
        val clock = MutableClock(1_000_000_000L)
        val clockSync =
            ClockSyncEngine(
                clock,
                ClockSyncConfig(
                    maxSamples = 8,
                    minimumLockSamples = 3,
                    minimumRateFitSpanNs = 1L,
                ),
            )
        val player = FakePlayer(PlayerState(queueItemId = itemId, positionMs = 0L, prepared = true))
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller =
                ScheduledPlaybackController(
                    player = player,
                    mutations = PlayerMutationCoordinator(player),
                    clock = clock,
                    clockSync = clockSync,
                    scope = scope,
                    log = testLog(),
                    onError = {},
                    onCommandPhase = { id, phase, _ ->
                        if (id == "reconnect" && phase == TransportCommandPhase.SETTLED)
                            settled.complete(Unit)
                    },
                    usesLocalCoordinatorClock = { false },
                )
            controller.schedulePlay(itemId, 0L, 3_000_000_000L, "reconnect")
            delay(80L)
            assertEquals(0, player.playCalls)

            repeat(3) {
                val ping = clockSync.createPing()
                val localSend = ping.localSendNs
                val coordinatorReceive = localSend + 110_000_000L
                val coordinatorSend = coordinatorReceive + 1_000_000L
                clock.value = localSend + 21_000_000L
                clockSync.recordPong(
                    pingId = ping.pingId,
                    echoedGuestSendNs = localSend,
                    coordinatorReceiveNs = coordinatorReceive,
                    coordinatorSendNs = coordinatorSend,
                    localReceiveNs = clock.value,
                )
                clock.value += 100_000_000L
            }
            clock.value = clockSync.toLocalTime(3_000_000_000L)

            withTimeout(2_000L) { settled.await() }
            assertEquals(1, player.playCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unavailableScheduledItemReportsTypedFailure() = runBlocking {
        val missing = QueueItemId("missing")
        val player =
            FakePlayer(
                PlayerState(queueItemId = itemId, positionMs = 0L, prepared = true),
                seekToItemResult = false,
            )
        val failure = CompletableDeferred<PlaybackFailure>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val clock = MonotonicClock { 0L }
            ScheduledPlaybackController(
                    player = player,
                    mutations = PlayerMutationCoordinator(player),
                    clock = clock,
                    clockSync = ClockSyncEngine(clock),
                    scope = scope,
                    log = testLog(),
                    onError = { failure.complete(it) },
                    usesLocalCoordinatorClock = { true },
                )
                .scheduleSeek(
                    missing,
                    0L,
                    resume = true,
                    executeAtCoordinatorNs = 0L,
                    commandId = "missing",
                )

            val issue = withTimeout(2_000L) { failure.await() }
            assertEquals(missing, (issue as PlaybackFailure.TrackUnavailable).queueItemId)
            assertEquals("missing", issue.commandId)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun unsynchronizedClockRejectsCommandInsteadOfWaitingForever() = runBlocking {
        val player = FakePlayer(PlayerState(queueItemId = itemId, positionMs = 0L, prepared = true))
        val failure = CompletableDeferred<PlaybackFailure>()
        val phases = mutableListOf<TransportCommandPhase>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val clock = MonotonicClock { 0L }
            ScheduledPlaybackController(
                    player = player,
                    mutations = PlayerMutationCoordinator(player),
                    clock = clock,
                    clockSync = ClockSyncEngine(clock),
                    scope = scope,
                    log = testLog(),
                    onError = { failure.complete(it) },
                    onCommandPhase = { id, phase, _ ->
                        if (id == "clock-timeout") synchronized(phases) { phases += phase }
                    },
                    usesLocalCoordinatorClock = { false },
                    clockSyncWaitTimeoutMs = 50L,
                )
                .schedulePlay(itemId, 0L, 1_000_000_000L, "clock-timeout")

            val issue = withTimeout(2_000L) { failure.await() }
            assertTrue(issue is PlaybackFailure.ClockUnavailable)
            assertEquals("clock-timeout", issue.commandId)
            assertEquals(
                listOf(TransportCommandPhase.SCHEDULED, TransportCommandPhase.REJECTED),
                synchronized(phases) { phases.toList() },
            )
            assertEquals(0, player.playCalls)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun nullCommandReplacementStillOwnsAndCancelsLatestJob() = runBlocking {
        val player = FakePlayer(PlayerState(queueItemId = itemId, positionMs = 0L, prepared = true))
        val parent = SupervisorJob()
        val scope = CoroutineScope(parent + Dispatchers.Default)
        try {
            val controller = controller(player, scope) { _, _, _ -> }
            controller.schedulePlay(itemId, 0L, 10_000_000_000L, commandId = null)
            controller.schedulePause(itemId, 0L, 10_000_000_000L, commandId = null)

            // Let the cancelled first job run its finally block before cancelling the replacement.
            delay(80L)
            controller.cancel()
            withTimeout(2_000L) {
                while (parent.children.any { it.isActive }) delay(10L)
            }

            assertEquals(0, player.playCalls)
            assertEquals(0, player.pauseCalls)
        } finally {
            scope.cancel()
        }
    }


    @Test
    fun scheduledPlayAlignsSilentlyWhileOutputIsInhibited() = runBlocking {
        val player =
            FakePlayer(
                PlayerState(
                    queueItemId = itemId,
                    positionMs = 1_000L,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.AUDIO_FOCUS,
                )
            )
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            controller(player, scope) { id, phase, _ ->
                    if (id == "inhibited-play" && phase == TransportCommandPhase.SETTLED)
                        settled.complete(Unit)
                }
                .schedulePlay(itemId, 2_000L, 0L, "inhibited-play")

            withTimeout(2_000L) { settled.await() }
            assertEquals(0, player.playCalls)
            assertTrue(player.seekItemCalls > 0)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun scheduledResumeSeekNeverPlaysWhileOutputIsInhibited() = runBlocking {
        val player =
            FakePlayer(
                PlayerState(
                    queueItemId = itemId,
                    positionMs = 0L,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                    inhibitionReason = LocalPlaybackInhibitionReason.BECOMING_NOISY,
                )
            )
        val settled = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            controller(player, scope) { id, phase, _ ->
                    if (id == "inhibited-seek" && phase == TransportCommandPhase.SETTLED)
                        settled.complete(Unit)
                }
                .scheduleSeek(itemId, 5_000L, resume = true, executeAtCoordinatorNs = 0L, commandId = "inhibited-seek")

            withTimeout(2_000L) { settled.await() }
            assertEquals(0, player.playCalls)
            assertTrue(player.seekItemCalls > 0)
        } finally {
            scope.cancel()
        }
    }

    private fun controller(
        player: FakePlayer,
        scope: CoroutineScope,
        onPhase: (String, TransportCommandPhase, String?) -> Unit,
    ): ScheduledPlaybackController {
        val clock = MonotonicClock { 0L }
        return ScheduledPlaybackController(
            player = player,
            mutations = PlayerMutationCoordinator(player),
            clock = clock,
            clockSync = ClockSyncEngine(clock),
            scope = scope,
            log = testLog(),
            onError = {},
            onCommandPhase = onPhase,
            usesLocalCoordinatorClock = { true },
        )
    }

    private class MutableClock(@Volatile var value: Long) : MonotonicClock {
        override fun nowNs(): Long = value
    }

    private fun testLog(): DiagnosticLog =
        DiagnosticLog(temporaryFolder.newFile("diagnostics-${System.nanoTime()}.log"))

    private class FakePlayer(
        initial: PlayerState,
        private val seekToItemResult: Boolean = true,
    ) : PlayerPort {
        private val mutableState = MutableStateFlow(initial)
        override val state: StateFlow<PlayerState> = mutableState
        var playCalls = 0
        var pauseCalls = 0
        var seekCalls = 0
        var seekItemCalls = 0
        var speedCalls = 0
        var lastSeekItemPositionMs: Long? = null

        override suspend fun samplePlayback(): PlaybackSample =
            PlaybackSample(
                queueItemId = mutableState.value.queueItemId,
                positionMs = mutableState.value.positionMs,
                durationMs = mutableState.value.durationMs,
                sampledAtLocalNs = 0L,
                playWhenReady = mutableState.value.playWhenReady,
                isPlaying = mutableState.value.isPlaying,
                activityState = mutableState.value.activityState,
                playbackSpeed = mutableState.value.playbackSpeed,
                outputRoute = mutableState.value.outputRoute,
                seekRevision = mutableState.value.seekRevision,
            )

        override suspend fun setQueue(
            items: List<LocalPlayableItem>,
            currentQueueItemId: QueueItemId?,
            positionMs: Long,
        ) = Unit

        override suspend fun play(): Boolean {
            playCalls++
            return true
        }

        override suspend fun beginLocalRejoin() {}

        override suspend fun completeLocalRejoin() {}

        override suspend fun pause(cause: PlaybackPauseCause) {
            pauseCalls++
        }

        override suspend fun seekTo(positionMs: Long) {
            seekCalls++
        }

        override suspend fun seekToItem(queueItemId: QueueItemId, positionMs: Long): Boolean {
            seekItemCalls++
            lastSeekItemPositionMs = positionMs
            return seekToItemResult
        }

        override suspend fun setRepeatCurrentItem(enabled: Boolean) = Unit

        override suspend fun setPlaybackSpeed(speed: Float) {
            speedCalls++
        }
    }
}
