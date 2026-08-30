package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.playback.PlayerExecutor
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.util.MonotonicClock

internal enum class LocalRejoinReason {
    AUTO_AUDIO_FOCUS,
    MANUAL,
}

/**
 * Owns device-local participation in room playback.
 *
 * Canonical room transport never changes because one phone temporarily loses audio output. A
 * transient audio-focus interruption creates an automatic pending rejoin; explicit Play/Rejoin
 * creates a manual pending rejoin. The intent survives temporary clock/media unavailability and is
 * completed exactly once when platform suppression, clock and media prerequisites converge.
 *
 * Becoming-noisy and unsuitable-output interruptions never create automatic resume intent.
 */
internal class LocalPlaybackParticipationCoordinator(
    private val player: PlayerPort,
    private val playerExecutor: PlayerExecutor,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val playbackSession: PlaybackSessionCoordinator,
    private val isCoordinator: () -> Boolean,
    private val snapshotProvider: suspend () -> RoomSnapshot?,
    private val isQueueItemExecutable: suspend (QueueItemId) -> Boolean,
    private val refreshPlayerQueue: suspend (RoomSnapshot, QueueItemId, Long) -> Unit,
    private val executeRejoin:
        suspend (
            commandId: String,
            publishTransportStatus: Boolean,
            block: suspend PlayerPort.() -> Boolean,
        ) -> Unit,
    private val resetLocalSynchronization: suspend () -> Unit,
    private val publishStatus: suspend (ProtocolBody.PlaybackStatusReport) -> Unit,
    private val onCoordinatorCohortChanged: suspend () -> Unit,
    private val diagnostics: RoomDiagnostics,
) {
    private data class PendingRejoin(
        val token: Long,
        val reason: LocalRejoinReason,
        val commandId: String?,
    )

    private val rejoinLock = Any()
    private var nextToken = 1L
    private var pendingRejoin: PendingRejoin? = null
    private var attemptInFlightToken: Long? = null
    private var lastAttemptAtNs = Long.MIN_VALUE
    private var lastWaitingReason: String? = null
    private var lastParticipation: LocalPlaybackParticipation? = null

    suspend fun observe(value: PlayerState, snapshot: RoomSnapshot?) {
        val previous = lastParticipation
        if (value.participation != previous) {
            lastParticipation = value.participation
            diagnostics.info(
                "playback.participation.changed",
                "playback.participation_from" to previous?.name,
                "playback.participation_to" to value.participation.name,
                "playback.inhibition_reason" to value.inhibitionReason?.name,
                "playback.resume_blocked" to value.outputResumeBlocked,
            )
            snapshot?.let {
                if (isCoordinator()) onCoordinatorCohortChanged()
                else publishStatus(statusReport(value, it))
            }
        }

        when {
            value.participation == LocalPlaybackParticipation.ACTIVE -> clearPendingRejoin("active")
            value.inhibitionReason == LocalPlaybackInhibitionReason.AUDIO_FOCUS ->
                ensureAutomaticAudioFocusRejoin()
            else -> cancelAutomaticRejoin("non_auto_inhibition")
        }
    }

    fun statusReport(
        value: PlayerState,
        snapshot: RoomSnapshot,
    ): ProtocolBody.PlaybackStatusReport {
        val canonical = playbackSession.canonicalForTick(snapshot, isCoordinator())
        return ProtocolBody.PlaybackStatusReport(
            queueItemId = value.queueItemId,
            positionMs = value.positionMs,
            isPlaying = value.playWhenReady,
            participation = value.participation,
            driftMs = null,
            playbackRevision = canonical.revision,
            queueRevision = snapshot.queueRevision,
            canonicalSequence = snapshot.sequence,
        )
    }

    fun requestManualRejoin(commandId: String) {
        val pending =
            synchronized(rejoinLock) {
                val next = PendingRejoin(nextToken++, LocalRejoinReason.MANUAL, commandId)
                pendingRejoin = next
                lastAttemptAtNs = Long.MIN_VALUE
                lastWaitingReason = null
                next
            }
        diagnostics.info(
            "playback.rejoin.pending",
            "playback.rejoin_reason" to pending.reason.name,
            "command.id" to commandId.take(12),
        )
    }

    /**
     * Attempts the newest pending rejoin against the newest canonical snapshot.
     *
     * Safe to call from both player-transition and independent sync jobs. A small synchronized
     * claim prevents duplicate player mutations; no room/canonical state is mutated here.
     */
    suspend fun tryPendingRejoin() {
        val pending = claimPendingAttempt() ?: return
        var completed = false
        try {
            val local = player.state.value
            if (local.participation == LocalPlaybackParticipation.ACTIVE) {
                completed = true
                return
            }
            if (local.participation != LocalPlaybackParticipation.OUTPUT_INHIBITED) return
            if (!isAttemptCurrent(pending)) return
            if (local.outputResumeBlocked) {
                recordWaiting(pending, "platform_suppression")
                return
            }

            val snapshot = snapshotProvider()
            if (snapshot == null) {
                recordWaiting(pending, "no_room")
                return
            }
            val canonical = snapshot.playback
            val queueItemId = canonical.queueItemId
            if (queueItemId == null) {
                recordWaiting(pending, "no_canonical_item")
                return
            }
            if (!canonical.isPlaying) {
                recordWaiting(pending, "canonical_paused")
                return
            }
            if (!isCoordinator() && !clockSync.synchronized) {
                recordWaiting(pending, "clock_unavailable")
                return
            }
            if (!isQueueItemExecutable(queueItemId)) {
                recordWaiting(pending, "media_unavailable")
                return
            }
            if (!isAttemptCurrent(pending)) return

            val targetPositionMs = canonical.projectedPositionMs(coordinatorNowNs())
            diagnostics.info(
                "playback.rejoin.started",
                "playback.rejoin_reason" to pending.reason.name,
                "command.id" to pending.commandId?.take(12),
                "queue.item_id" to queueItemId.value.take(12),
                "playback.position_ms" to targetPositionMs,
                "playback.inhibition_reason" to local.inhibitionReason?.name,
            )
            refreshPlayerQueue(snapshot, queueItemId, targetPositionMs)
            if (!isAttemptCurrent(pending)) return

            // Re-read the latest canonical state after queue preparation. Disk/player work above
            // may
            // have taken long enough for the room to advance to another item.
            val latest = snapshotProvider() ?: return
            val latestCanonical = latest.playback
            val latestItemId = latestCanonical.queueItemId ?: return
            if (!latestCanonical.isPlaying) {
                recordWaiting(pending, "canonical_paused")
                return
            }
            if (!isCoordinator() && !clockSync.synchronized) {
                recordWaiting(pending, "clock_unavailable")
                return
            }
            if (!isQueueItemExecutable(latestItemId)) {
                recordWaiting(pending, "media_unavailable")
                return
            }
            if (!isAttemptCurrent(pending)) return
            if (latestItemId != queueItemId) {
                val latestPosition = latestCanonical.projectedPositionMs(coordinatorNowNs())
                refreshPlayerQueue(latest, latestItemId, latestPosition)
                if (!isAttemptCurrent(pending)) return
            }

            // A newer local inhibition (for example becoming-noisy) must be able to cancel an
            // automatic focus rejoin even if it arrived while queue preparation was suspended.
            val beforeExecution = player.state.value
            if (beforeExecution.participation != LocalPlaybackParticipation.OUTPUT_INHIBITED) return
            if (beforeExecution.outputResumeBlocked || !isAttemptCurrent(pending)) return
            if (
                pending.reason == LocalRejoinReason.AUTO_AUDIO_FOCUS &&
                    beforeExecution.inhibitionReason != LocalPlaybackInhibitionReason.AUDIO_FOCUS
            )
                return

            val livePositionMs = latestCanonical.projectedPositionMs(coordinatorNowNs())
            val executionId = pending.commandId ?: "auto-rejoin-${pending.token}"
            executeRejoin(executionId, pending.commandId != null) {
                setPlaybackSpeed(1f)
                rejoinLivePlayback(latestItemId, livePositionMs)
            }
            val after = player.state.value
            if (
                after.participation == LocalPlaybackParticipation.ACTIVE &&
                    after.playWhenReady &&
                    after.queueItemId == latestItemId
            ) {
                resetPlaybackSynchronization()
                publishStatus(statusReport(after, latest))
                diagnostics.info(
                    "playback.rejoin.completed",
                    "playback.rejoin_reason" to pending.reason.name,
                    "command.id" to pending.commandId?.take(12),
                    "queue.item_id" to latestItemId.value.take(12),
                    "playback.position_ms" to livePositionMs,
                )
                completed = true
            } else {
                recordWaiting(pending, "player_not_active")
            }
        } finally {
            releasePendingAttempt(pending, completed)
        }
    }

    /** Local interruption state belongs to one room session only. This reset never starts audio. */
    suspend fun resetForSessionBoundary() {
        synchronized(rejoinLock) {
            pendingRejoin = null
            attemptInFlightToken = null
            lastAttemptAtNs = Long.MIN_VALUE
            lastWaitingReason = null
        }
        val before = player.state.value
        playerExecutor.maintenance { resetLocalPlaybackParticipation() }
        val after = player.state.value
        lastParticipation = after.participation
        if (
            before.participation != after.participation ||
                before.inhibitionReason != after.inhibitionReason ||
                before.outputResumeBlocked != after.outputResumeBlocked
        ) {
            diagnostics.debug(
                "playback.participation.session_reset",
                "playback.participation_from" to before.participation.name,
                "playback.participation_to" to after.participation.name,
                "playback.inhibition_reason_from" to before.inhibitionReason?.name,
                "playback.inhibition_reason_to" to after.inhibitionReason?.name,
                "playback.resume_blocked" to after.outputResumeBlocked,
            )
        }
    }

    private fun ensureAutomaticAudioFocusRejoin() {
        val created =
            synchronized(rejoinLock) {
                val existing = pendingRejoin
                if (existing?.reason == LocalRejoinReason.MANUAL || existing != null)
                    return@synchronized null
                PendingRejoin(nextToken++, LocalRejoinReason.AUTO_AUDIO_FOCUS, null).also {
                    pendingRejoin = it
                    lastAttemptAtNs = Long.MIN_VALUE
                    lastWaitingReason = null
                }
            } ?: return
        diagnostics.info(
            "playback.rejoin.pending",
            "playback.rejoin_reason" to created.reason.name,
        )
    }

    private fun cancelAutomaticRejoin(reason: String) {
        val cancelled =
            synchronized(rejoinLock) {
                val current = pendingRejoin ?: return@synchronized null
                if (current.reason != LocalRejoinReason.AUTO_AUDIO_FOCUS) return@synchronized null
                pendingRejoin = null
                lastWaitingReason = null
                current
            } ?: return
        diagnostics.debug(
            "playback.rejoin.cancelled",
            "playback.rejoin_reason" to cancelled.reason.name,
            "reason" to reason,
        )
    }

    private fun clearPendingRejoin(reason: String) {
        val cleared =
            synchronized(rejoinLock) {
                val current = pendingRejoin ?: return@synchronized null
                pendingRejoin = null
                if (attemptInFlightToken == current.token) attemptInFlightToken = null
                lastWaitingReason = null
                current
            } ?: return
        diagnostics.debug(
            "playback.rejoin.cleared",
            "playback.rejoin_reason" to cleared.reason.name,
            "reason" to reason,
        )
    }

    private fun claimPendingAttempt(): PendingRejoin? =
        synchronized(rejoinLock) {
            val pending = pendingRejoin ?: return@synchronized null
            if (attemptInFlightToken != null) return@synchronized null
            val nowNs = clock.nowNs()
            if (
                lastAttemptAtNs != Long.MIN_VALUE &&
                    nowNs >= lastAttemptAtNs &&
                    nowNs - lastAttemptAtNs < REJOIN_RETRY_INTERVAL_NS
            )
                return@synchronized null
            attemptInFlightToken = pending.token
            lastAttemptAtNs = nowNs
            pending
        }

    private fun isAttemptCurrent(pending: PendingRejoin): Boolean =
        synchronized(rejoinLock) {
            pendingRejoin?.token == pending.token && attemptInFlightToken == pending.token
        }

    private fun releasePendingAttempt(pending: PendingRejoin, completed: Boolean) {
        synchronized(rejoinLock) {
            if (attemptInFlightToken == pending.token) attemptInFlightToken = null
            if (completed && pendingRejoin?.token == pending.token) {
                pendingRejoin = null
                lastWaitingReason = null
            }
        }
    }

    private fun recordWaiting(pending: PendingRejoin, reason: String) {
        val shouldLog =
            synchronized(rejoinLock) {
                if (pendingRejoin?.token != pending.token || lastWaitingReason == reason) false
                else {
                    lastWaitingReason = reason
                    true
                }
            }
        if (shouldLog) {
            diagnostics.debug(
                "playback.rejoin.waiting",
                "playback.rejoin_reason" to pending.reason.name,
                "command.id" to pending.commandId?.take(12),
                "reason" to reason,
            )
        }
    }

    private suspend fun resetPlaybackSynchronization() {
        resetLocalSynchronization()
        diagnostics.info("sync.reacquire.required", "reason" to "local_output_rejoin")
    }

    private fun coordinatorNowNs(): Long =
        if (isCoordinator()) clock.nowNs() else clockSync.coordinatorNowNs()

    private companion object {
        const val REJOIN_RETRY_INTERVAL_NS = 250_000_000L
    }
}
