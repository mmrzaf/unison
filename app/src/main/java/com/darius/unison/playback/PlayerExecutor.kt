package com.darius.unison.playback

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.util.DiagnosticCategory
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield

/**
 * The single Media3 mutation authority for one room runtime.
 *
 * Every player mutation -- scheduled transport, immediate transport, timeline maintenance and
 * synchronization correction -- crosses [mutationMutex]. Scheduled transport also owns a ticket
 * from registration until execution, so non-urgent maintenance cannot slip into that interval.
 * Timing jobs may wait concurrently, but they never touch [PlayerPort] directly.
 */
class PlayerExecutor(
    private val player: PlayerPort,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val onError: (PlaybackFailure) -> Unit,
    private val onCommandPhase: (String, TransportCommandPhase, String?) -> Unit = { _, _, _ -> },
    private val usesLocalCoordinatorClock: () -> Boolean = { false },
    private val clockSyncWaitTimeoutMs: Long = DEFAULT_CLOCK_SYNC_WAIT_TIMEOUT_MS,
) {
    class Ticket internal constructor(val generation: Long, val commandId: String?)

    enum class ExecutionResult {
        SUCCESS,
        FAILED,
        STALE,
    }

    data class ImmediateExecution(
        val result: ExecutionResult,
        val supersededCommandId: String?,
    )

    private val mutationMutex = Mutex()
    private val stateLock = Any()
    private var transportGeneration = 0L
    private var pendingTicket: Ticket? = null
    private var scheduled: Job? = null
    private var scheduledCommandId: String? = null
    private var scheduledGeneration = 0L

    val hasPendingTransport: Boolean
        get() = synchronized(stateLock) { pendingTicket != null }

    suspend fun executeImmediateTransport(
        commandId: String,
        action: suspend PlayerPort.() -> Boolean,
    ): ImmediateExecution {
        val (ticket, superseded, scheduledJob) = beginImmediateTransport(commandId)
        scheduledJob?.cancel()
        return ImmediateExecution(executeTransport(ticket, action), superseded)
    }

    suspend fun maintenance(action: suspend PlayerPort.() -> Unit) =
        mutationMutex.withLock { player.action() }

    /**
     * Runs replaceable/non-urgent player maintenance only if no exact transport owns the player.
     * A transport registered while older maintenance is already executing waits for that mutation;
     * maintenance submitted after registration observes the ticket and yields instead.
     */
    suspend fun maintenanceIfTransportIdle(action: suspend PlayerPort.() -> Unit): Boolean =
        mutationMutex.withLock {
            if (hasPendingTransport) return@withLock false
            player.action()
            true
        }

    /** Automatic sync correction never overrides an accepted exact transport. */
    suspend fun synchronize(action: suspend PlayerPort.() -> Unit): Boolean =
        mutationMutex.withLock {
            if (hasPendingTransport) return@withLock false
            player.action()
            true
        }

    suspend fun schedulePlay(
        queueItemId: QueueItemId,
        positionMs: Long,
        executeAtCoordinatorNs: Long,
        commandId: String? = null,
    ) {
        log.info(
            TAG,
            DiagnosticCategory.PLAYBACK,
            "playback.command.scheduled",
            attributes =
                scheduledAttributes(
                    "play",
                    commandId,
                    queueItemId,
                    positionMs,
                    executeAtCoordinatorNs,
                ),
        )
        schedule("play", commandId, queueItemId, executeAtCoordinatorNs) {
            val lateMs =
                ((clock.nowNs() - localTargetNs(executeAtCoordinatorNs)).coerceAtLeast(0) /
                    1_000_000L)
            val expectedPositionMs = positionMs + lateMs
            val local = state.value
            if (local.participation == LocalPlaybackParticipation.OUTPUT_INHIBITED) {
                if (
                    local.queueItemId != queueItemId ||
                        abs(local.positionMs - expectedPositionMs) > PLAY_POSITION_TOLERANCE_MS
                ) {
                    if (!seekToItem(queueItemId, expectedPositionMs)) return@schedule false
                }
                setPlaybackSpeed(1f)
                if (state.value.playWhenReady) pause(PlaybackPauseCause.OUTPUT_INHIBITION)
                logOutputDeferred("play", commandId, queueItemId)
                return@schedule true
            }
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

    suspend fun schedulePause(
        queueItemId: QueueItemId,
        positionMs: Long,
        executeAtCoordinatorNs: Long,
        commandId: String? = null,
    ) {
        log.info(
            TAG,
            DiagnosticCategory.PLAYBACK,
            "playback.command.scheduled",
            attributes =
                scheduledAttributes(
                    "pause",
                    commandId,
                    queueItemId,
                    positionMs,
                    executeAtCoordinatorNs,
                ),
        )
        schedule("pause", commandId, queueItemId, executeAtCoordinatorNs) {
            // Pause is intentionally non-seeking. Seeking here flushes the decoder and makes a
            // basic pause feel delayed; normal synchronization reconciles position afterward.
            if (!state.value.playWhenReady) return@schedule true
            pause(PlaybackPauseCause.SCHEDULED_TRANSPORT)
            setPlaybackSpeed(1f)
            true
        }
    }

    suspend fun scheduleSeek(
        queueItemId: QueueItemId,
        positionMs: Long,
        resume: Boolean,
        executeAtCoordinatorNs: Long,
        commandId: String? = null,
    ) {
        log.info(
            TAG,
            DiagnosticCategory.PLAYBACK,
            "playback.command.scheduled",
            attributes =
                scheduledAttributes(
                    "seek",
                    commandId,
                    queueItemId,
                    positionMs,
                    executeAtCoordinatorNs,
                ) + ("playback.resume" to resume),
        )
        schedule("seek", commandId, queueItemId, executeAtCoordinatorNs) {
            val lateMs =
                if (resume) {
                    ((clock.nowNs() - localTargetNs(executeAtCoordinatorNs)).coerceAtLeast(0) /
                        1_000_000L)
                } else {
                    0L
                }
            val expectedPositionMs = positionMs + lateMs
            val local = state.value
            if (resume && local.participation == LocalPlaybackParticipation.OUTPUT_INHIBITED) {
                if (
                    local.queueItemId != queueItemId ||
                        abs(local.positionMs - expectedPositionMs) > SEEK_POSITION_TOLERANCE_MS
                ) {
                    if (!seekToItem(queueItemId, expectedPositionMs)) return@schedule false
                }
                setPlaybackSpeed(1f)
                if (state.value.playWhenReady) pause(PlaybackPauseCause.OUTPUT_INHIBITION)
                logOutputDeferred("seek", commandId, queueItemId)
                return@schedule true
            }
            if (
                local.queueItemId == queueItemId &&
                    abs(local.positionMs - expectedPositionMs) <= SEEK_POSITION_TOLERANCE_MS &&
                    local.playWhenReady == resume
            ) {
                return@schedule true
            }
            if (!seekToItem(queueItemId, expectedPositionMs)) return@schedule false
            setPlaybackSpeed(1f)
            if (resume) {
                play()
            } else {
                pause(PlaybackPauseCause.SCHEDULED_TRANSPORT)
                true
            }
        }
    }

    fun cancel(reason: String = "Cancelled") {
        val (job, commandId) =
            synchronized(stateLock) {
                scheduledGeneration++
                val result = scheduled to (scheduledCommandId ?: pendingTicket?.commandId)
                scheduled = null
                scheduledCommandId = null
                invalidateTransportLocked()
                result
            }
        job?.cancel()
        if (commandId != null) onCommandPhase(commandId, TransportCommandPhase.SUPERSEDED, reason)
    }

    fun cancelIfOwned(commandId: String, publishSuperseded: Boolean = true): Boolean {
        val job =
            synchronized(stateLock) {
                if (scheduledCommandId != commandId && pendingTicket?.commandId != commandId) {
                    return false
                }
                scheduledGeneration++
                scheduled.also {
                    scheduled = null
                    scheduledCommandId = null
                    invalidateTransportLocked()
                }
            }
        job?.cancel()
        if (publishSuperseded) {
            onCommandPhase(commandId, TransportCommandPhase.SUPERSEDED, "Cancelled")
        }
        return true
    }

    fun invalidateTransport(): String? =
        synchronized(stateLock) {
            scheduledGeneration++
            scheduled?.cancel()
            scheduled = null
            scheduledCommandId = null
            invalidateTransportLocked()
        }

    private data class ImmediateReservation(
        val ticket: Ticket,
        val supersededCommandId: String?,
        val scheduledJob: Job?,
    )

    private suspend fun beginImmediateTransport(commandId: String): ImmediateReservation =
        mutationMutex.withLock {
            synchronized(stateLock) {
                // Immediate local transport is an ordering barrier: no older delayed command may
                // wake later and publish/execute after it. Invalidate both schedule ownership and
                // the old transport ticket before installing the new ticket atomically.
                scheduledGeneration++
                val oldJob = scheduled
                val superseded = scheduledCommandId ?: pendingTicket?.commandId
                scheduled = null
                scheduledCommandId = null
                transportGeneration++
                val ticket = Ticket(transportGeneration, commandId)
                pendingTicket = ticket
                ImmediateReservation(ticket, superseded, oldJob)
            }
        }

    private suspend fun beginScheduledTransport(
        scheduleGeneration: Long,
        commandId: String?,
    ): Pair<Ticket, String?>? =
        mutationMutex.withLock {
            synchronized(stateLock) {
                // A schedule can wait behind a player mutation. If a newer schedule or immediate
                // transport arrived while it waited, the stale caller must never install a ticket
                // after the newer command.
                if (scheduledGeneration != scheduleGeneration) return@withLock null
                val superseded = pendingTicket?.commandId
                val ticket = Ticket(++transportGeneration, commandId)
                pendingTicket = ticket
                ticket to superseded
            }
        }

    private suspend fun executeTransport(
        ticket: Ticket,
        action: suspend PlayerPort.() -> Boolean,
    ): ExecutionResult =
        mutationMutex.withLock {
            if (!isCurrent(ticket)) return@withLock ExecutionResult.STALE
            try {
                if (player.action()) ExecutionResult.SUCCESS else ExecutionResult.FAILED
            } finally {
                synchronized(stateLock) {
                    if (pendingTicket == ticket) pendingTicket = null
                }
            }
        }

    private fun invalidateTransportLocked(): String? {
        transportGeneration++
        return pendingTicket.also { pendingTicket = null }?.commandId
    }

    private fun isCurrent(ticket: Ticket): Boolean =
        synchronized(stateLock) {
            pendingTicket == ticket && transportGeneration == ticket.generation
        }

    private fun logOutputDeferred(
        commandType: String,
        commandId: String?,
        queueItemId: QueueItemId,
    ) {
        val local = player.state.value
        log.info(
            TAG,
            DiagnosticCategory.PLAYBACK,
            "playback.command.output_deferred",
            attributes =
                mapOf(
                    "command.type" to commandType,
                    "command.id" to commandId?.take(12),
                    "queue.item_id" to queueItemId.value.take(12),
                    "playback.inhibition_reason" to local.inhibitionReason?.name,
                ),
        )
    }

    private suspend fun schedule(
        name: String,
        commandId: String?,
        queueItemId: QueueItemId,
        executeAtCoordinatorNs: Long,
        action: suspend PlayerPort.() -> Boolean,
    ) {
        val (generation, previousJob) =
            synchronized(stateLock) {
                val nextGeneration = ++scheduledGeneration
                nextGeneration to
                    scheduled.also {
                        scheduled = null
                        scheduledCommandId = null
                    }
            }
        previousJob?.cancel()
        val reservation = beginScheduledTransport(generation, commandId)
        if (reservation == null) {
            commandId?.let {
                onCommandPhase(it, TransportCommandPhase.SUPERSEDED, "Replaced by a newer action")
            }
            return
        }
        val (ticket, supersededCommandId) = reservation
        if (supersededCommandId != null && supersededCommandId != commandId) {
            onCommandPhase(
                supersededCommandId,
                TransportCommandPhase.SUPERSEDED,
                "Replaced by a newer action",
            )
        }
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
                                invalidateIfCurrent(ticket)
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
                            // Coordinator→local mapping is an estimate that can move while the
                            // command waits (STALE → ACQUIRING → LOCKED, route changes, new RTT
                            // samples). Never sleep almost the whole remaining interval using one
                            // mapping; periodically recompute exactly like RoomRuntime's queue timer.
                            delay(
                                minOf(
                                    CLOCK_RECHECK_INTERVAL_MS,
                                    ((remainingNs - 1_000_000L) / 1_000_000L).coerceAtLeast(1L),
                                )
                            )
                        } else {
                            yield()
                        }
                    }
                    log.info(
                        TAG,
                        DiagnosticCategory.PLAYBACK,
                        "playback.command.executing",
                        attributes =
                            mapOf(
                                "command.type" to name,
                                "command.id" to commandId?.take(12),
                                "queue.item_id" to queueItemId.value.take(12),
                                "playback.late_ms" to
                                    ((clock.nowNs() - latestLocalTarget).coerceAtLeast(0) /
                                        1_000_000L),
                            ),
                    )
                    commandId?.let { onCommandPhase(it, TransportCommandPhase.EXECUTING, null) }
                    when (executeTransport(ticket, action)) {
                        ExecutionResult.SUCCESS ->
                            commandId?.let { onCommandPhase(it, TransportCommandPhase.SETTLED, null) }

                        ExecutionResult.FAILED ->
                            fail(
                                PlaybackFailure.TrackUnavailable(commandId, queueItemId),
                                "This song is not ready yet",
                            )

                        ExecutionResult.STALE ->
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
                    log.error(
                        TAG,
                        DiagnosticCategory.PLAYBACK,
                        "playback.command.failed",
                        attributes =
                            mapOf(
                                "command.type" to name,
                                "command.id" to commandId?.take(12),
                            ),
                        throwable = error,
                    )
                    fail(
                        PlaybackFailure.ActionFailed(commandId, name, error),
                        "Playback could not complete that action",
                    )
                } finally {
                    synchronized(stateLock) {
                        if (scheduledGeneration == generation) {
                            scheduledCommandId = null
                            scheduled = null
                        }
                    }
                }
            }
        val installed =
            synchronized(stateLock) {
                if (scheduledGeneration != generation) {
                    false
                } else {
                    scheduled = replacement
                    scheduledCommandId = commandId
                    commandId?.let { onCommandPhase(it, TransportCommandPhase.SCHEDULED, null) }
                    replacement.start()
                    true
                }
            }
        if (!installed) {
            replacement.cancel()
            invalidateIfCurrent(ticket)
            commandId?.let {
                onCommandPhase(it, TransportCommandPhase.SUPERSEDED, "Replaced by a newer action")
            }
        }
    }

    private fun invalidateIfCurrent(ticket: Ticket): Boolean =
        synchronized(stateLock) {
            if (pendingTicket != ticket) return@synchronized false
            transportGeneration++
            pendingTicket = null
            true
        }

    private fun isScheduleCurrent(generation: Long): Boolean =
        synchronized(stateLock) { scheduledGeneration == generation }

    private fun scheduledAttributes(
        type: String,
        commandId: String?,
        queueItemId: QueueItemId,
        positionMs: Long,
        executeAtCoordinatorNs: Long,
    ): Map<String, Any?> =
        mapOf(
            "command.type" to type,
            "command.id" to commandId?.take(12),
            "queue.item_id" to queueItemId.value.take(12),
            "playback.position_ms" to positionMs,
            "playback.execute_at_coordinator_ns" to executeAtCoordinatorNs,
        )

    private fun localTargetNs(executeAtCoordinatorNs: Long): Long =
        if (usesLocalCoordinatorClock()) executeAtCoordinatorNs
        else clockSync.toLocalTime(executeAtCoordinatorNs)

    private fun fail(failure: PlaybackFailure, message: String) {
        log.error(
            TAG,
            DiagnosticCategory.PLAYBACK,
            "playback.command.rejected",
            message,
            attributes =
                mapOf(
                    "command.id" to failure.commandId?.take(12),
                    "failure.type" to failure::class.simpleName,
                ),
            throwable = (failure as? PlaybackFailure.ActionFailed)?.cause,
        )
        failure.commandId?.let { onCommandPhase(it, TransportCommandPhase.REJECTED, message) }
        onError(failure)
    }

    private companion object {
        const val TAG = "UnisonPlayerExecutor"
        const val CLOCK_RECHECK_INTERVAL_MS = 25L
        const val DEFAULT_CLOCK_SYNC_WAIT_TIMEOUT_MS = 8_000L
        const val PLAY_POSITION_TOLERANCE_MS = 450L
        const val SEEK_POSITION_TOLERANCE_MS = 250L
    }
}
