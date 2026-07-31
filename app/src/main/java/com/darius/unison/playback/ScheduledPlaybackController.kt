package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** Executes canonical transport commands at room time through one Media3 mutation owner. */
class ScheduledPlaybackController(
    private val player: PlayerPort,
    private val mutations: PlayerMutationCoordinator,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val onError: (PlaybackFailure) -> Unit,
    private val onCommandPhase: (String, TransportCommandPhase, String?) -> Unit = { _, _, _ -> },
    private val usesLocalCoordinatorClock: () -> Boolean = { false },
    private val clockSyncWaitTimeoutMs: Long = DEFAULT_CLOCK_SYNC_WAIT_TIMEOUT_MS,
) {
    private var scheduled: Job? = null
    private var scheduledCommandId: String? = null
    private var scheduledGeneration = 0L

    fun schedulePlay(
        queueItemId: QueueItemId,
        positionMs: Long,
        executeAtCoordinatorNs: Long,
        commandId: String? = null,
    ) {
        log.i(
            TAG,
            "Schedule play item=${queueItemId.value.take(8)} positionMs=$positionMs executeAtNs=$executeAtCoordinatorNs",
        )
        schedule("play", commandId, queueItemId, executeAtCoordinatorNs) {
            val lateMs =
                ((clock.nowNs() - localTargetNs(executeAtCoordinatorNs)).coerceAtLeast(0) /
                    1_000_000L)
            val expectedPositionMs = positionMs + lateMs
            val local = state.value
            if (
                local.queueItemId == queueItemId &&
                    abs(local.positionMs - expectedPositionMs) <= PLAY_POSITION_TOLERANCE_MS &&
                    local.playWhenReady
            ) {
                return@schedule true
            }
            if (
                local.queueItemId != queueItemId ||
                    abs(local.positionMs - expectedPositionMs) > PLAY_POSITION_TOLERANCE_MS
            ) {
                if (!seekToItem(queueItemId, expectedPositionMs)) return@schedule false
            }
            setPlaybackSpeed(1f)
            play()
        }
    }

    fun schedulePause(
        queueItemId: QueueItemId,
        positionMs: Long,
        executeAtCoordinatorNs: Long,
        commandId: String? = null,
    ) {
        log.i(
            TAG,
            "Schedule pause item=${queueItemId.value.take(8)} positionMs=$positionMs executeAtNs=$executeAtCoordinatorNs",
        )
        schedule("pause", commandId, queueItemId, executeAtCoordinatorNs) {
            // Pause is intentionally non-seeking. Seeking here flushed the decoder and made a basic
            // pause feel delayed and unstable. Normal synchronization reconciles position later.
            if (!state.value.playWhenReady) return@schedule true
            pause()
            setPlaybackSpeed(1f)
            true
        }
    }

    fun scheduleSeek(
        queueItemId: QueueItemId,
        positionMs: Long,
        resume: Boolean,
        executeAtCoordinatorNs: Long,
        commandId: String? = null,
    ) {
        log.i(
            TAG,
            "Schedule seek item=${queueItemId.value.take(8)} positionMs=$positionMs resume=$resume executeAtNs=$executeAtCoordinatorNs",
        )
        schedule("seek", commandId, queueItemId, executeAtCoordinatorNs) {
            val lateMs =
                if (resume) {
                    ((clock.nowNs() - localTargetNs(executeAtCoordinatorNs)).coerceAtLeast(0) /
                        1_000_000L)
                } else 0L
            val expectedPositionMs = positionMs + lateMs
            val local = state.value
            if (
                local.queueItemId == queueItemId &&
                    abs(local.positionMs - expectedPositionMs) <= SEEK_POSITION_TOLERANCE_MS &&
                    local.playWhenReady == resume
            ) {
                return@schedule true
            }
            if (!seekToItem(queueItemId, expectedPositionMs)) return@schedule false
            setPlaybackSpeed(1f)
            if (resume) play()
            else {
                pause()
                true
            }
        }
    }

    fun cancel(reason: String = "Cancelled") {
        val job = scheduled
        val commandId = scheduledCommandId
        scheduledGeneration++
        scheduled = null
        scheduledCommandId = null
        job?.cancel()
        mutations.invalidateTransport()
        if (commandId != null) onCommandPhase(commandId, TransportCommandPhase.SUPERSEDED, reason)
    }

    fun cancelIfOwned(commandId: String, publishSuperseded: Boolean = true): Boolean {
        if (scheduledCommandId != commandId) return false
        val job = scheduled
        scheduledGeneration++
        scheduled = null
        scheduledCommandId = null
        job?.cancel()
        mutations.invalidateTransport()
        if (publishSuperseded) {
            onCommandPhase(commandId, TransportCommandPhase.SUPERSEDED, "Cancelled")
        }
        return true
    }

    private fun schedule(
        name: String,
        commandId: String?,
        queueItemId: QueueItemId,
        executeAtCoordinatorNs: Long,
        action: suspend PlayerPort.() -> Boolean,
    ) {
        val previousJob = scheduled
        val generation = ++scheduledGeneration
        previousJob?.cancel()
        val (ticket, supersededCommandId) = mutations.beginTransport(commandId)
        if (supersededCommandId != null && supersededCommandId != commandId) {
            onCommandPhase(
                supersededCommandId,
                TransportCommandPhase.SUPERSEDED,
                "Replaced by a newer action",
            )
        }
        scheduledCommandId = commandId
        commandId?.let { onCommandPhase(it, TransportCommandPhase.SCHEDULED, null) }
        val replacement =
            scope.launch(Dispatchers.Default, start = CoroutineStart.LAZY) {
                try {
                    var latestLocalTarget = localTargetNs(executeAtCoordinatorNs)
                    var clockWaitedMs = 0L
                    while (isActive) {
                        if (!usesLocalCoordinatorClock() && !clockSync.synchronized) {
                            if (clockWaitedMs >= clockSyncWaitTimeoutMs) {
                                fail(
                                    PlaybackFailure.ClockUnavailable(commandId),
                                    "Playback clock is not synchronized",
                                )
                                return@launch
                            }
                            delay(CLOCK_RECHECK_INTERVAL_MS)
                            clockWaitedMs += CLOCK_RECHECK_INTERVAL_MS
                            continue
                        }
                        latestLocalTarget = localTargetNs(executeAtCoordinatorNs)
                        val remainingNs = latestLocalTarget - clock.nowNs()
                        if (remainingNs <= 0) break
                        if (remainingNs > 2_000_000L) {
                            delay(((remainingNs - 1_000_000L) / 1_000_000L).coerceAtLeast(1L))
                        } else {
                            yield()
                        }
                    }
                    log.i(
                        TAG,
                        "Execute $name lateMs=${((clock.nowNs() - latestLocalTarget).coerceAtLeast(0) / 1_000_000L)}",
                    )
                    commandId?.let { onCommandPhase(it, TransportCommandPhase.EXECUTING, null) }
                    when (mutations.executeTransport(ticket) { action() }) {
                        PlayerMutationCoordinator.ExecutionResult.SUCCESS ->
                            commandId?.let {
                                onCommandPhase(it, TransportCommandPhase.SETTLED, null)
                            }

                        PlayerMutationCoordinator.ExecutionResult.FAILED ->
                            fail(
                                PlaybackFailure.TrackUnavailable(commandId, queueItemId),
                                "This song is not ready yet",
                            )

                        PlayerMutationCoordinator.ExecutionResult.STALE ->
                            commandId?.let {
                                onCommandPhase(
                                    it,
                                    TransportCommandPhase.SUPERSEDED,
                                    "Replaced by a newer action",
                                )
                            }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    log.e(TAG, "Scheduled $name failed", error)
                    fail(
                        PlaybackFailure.ActionFailed(commandId, name, error),
                        "Playback could not complete that action",
                    )
                } finally {
                    if (scheduledGeneration == generation) {
                        scheduledCommandId = null
                        scheduled = null
                    }
                }
            }
        scheduled = replacement
        replacement.start()
    }

    private fun localTargetNs(executeAtCoordinatorNs: Long): Long =
        if (usesLocalCoordinatorClock()) executeAtCoordinatorNs
        else clockSync.toLocalTime(executeAtCoordinatorNs)

    private fun fail(failure: PlaybackFailure, message: String) {
        log.e(TAG, message, (failure as? PlaybackFailure.ActionFailed)?.cause)
        failure.commandId?.let { onCommandPhase(it, TransportCommandPhase.REJECTED, message) }
        onError(failure)
    }

    private companion object {
        const val TAG = "UnisonScheduler"
        const val CLOCK_RECHECK_INTERVAL_MS = 25L
        const val DEFAULT_CLOCK_SYNC_WAIT_TIMEOUT_MS = 8_000L
        const val PLAY_POSITION_TOLERANCE_MS = 450L
        const val SEEK_POSITION_TOLERANCE_MS = 250L
    }
}
