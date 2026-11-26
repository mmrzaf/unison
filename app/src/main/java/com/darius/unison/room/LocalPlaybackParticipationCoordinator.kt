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
 * resumable transient audio-focus suppression creates an automatic pending rejoin; explicit
 * Play/Rejoin creates a manual pending rejoin. The intent survives temporary clock/media
 * unavailability and is completed exactly once when platform suppression, clock and media
 * prerequisites converge.
 *
 * Permanent audio-focus loss, becoming-noisy, and unsuitable-output interruptions never create
 * automatic resume intent.
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

    private var nextToken = 1L
    private var pendingRejoin: PendingRejoin? = null
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
                "playback.automatic_rejoin_allowed" to value.automaticRejoinAllowed,
            )
            snapshot?.let {
                if (isCoordinator()) onCoordinatorCohortChanged()
                else publishStatus(statusReport(value, it))
            }
        }

        when {
            value.participation == LocalPlaybackParticipation.ACTIVE -> clearPendingRejoin("active")
            value.inhibitionReason == LocalPlaybackInhibitionReason.AUDIO_FOCUS &&
                value.automaticRejoinAllowed -> ensureAutomaticAudioFocusRejoin()
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
        val pending = PendingRejoin(nextToken++, LocalRejoinReason.MANUAL, commandId)
        pendingRejoin = pending
        lastAttemptAtNs = Long.MIN_VALUE
        lastWaitingReason = null
        diagnostics.info(
            "playback.rejoin.pending",
            "playback.rejoin_reason" to pending.reason.name,
            "command.id" to commandId.take(12),
        )
    }

    /**
     * Attempts the newest pending rejoin against the newest canonical snapshot. Actor-owned: this
     * is only called from a room actor turn, so it needs no internal synchronization.
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
            // may have taken long enough for the room to advance to another item.
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
                    (beforeExecution.inhibitionReason !=
                        LocalPlaybackInhibitionReason.AUDIO_FOCUS ||
                        !beforeExecution.automaticRejoinAllowed)
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
        pendingRejoin = null
        lastAttemptAtNs = Long.MIN_VALUE
        lastWaitingReason = null
        val before = player.state.value
        playerExecutor.maintenance { resetLocalPlaybackParticipation() }
        val after = player.state.value
        lastParticipation = after.participation
        if (
            before.participation != after.participation ||
                before.inhibitionReason != after.inhibitionReason ||
                before.outputResumeBlocked != after.outputResumeBlocked ||
                before.automaticRejoinAllowed != after.automaticRejoinAllowed
        ) {
            diagnostics.debug(
                "playback.participation.session_reset",
                "playback.participation_from" to before.participation.name,
                "playback.participation_to" to after.participation.name,
                "playback.inhibition_reason_from" to before.inhibitionReason?.name,
                "playback.inhibition_reason_to" to after.inhibitionReason?.name,
                "playback.resume_blocked" to after.outputResumeBlocked,
                "playback.automatic_rejoin_allowed" to after.automaticRejoinAllowed,
            )
        }
    }

    private fun ensureAutomaticAudioFocusRejoin() {
        if (pendingRejoin != null) return
        val created = PendingRejoin(nextToken++, LocalRejoinReason.AUTO_AUDIO_FOCUS, null)
        pendingRejoin = created
        lastAttemptAtNs = Long.MIN_VALUE
        lastWaitingReason = null
        diagnostics.info(
            "playback.rejoin.pending",
            "playback.rejoin_reason" to created.reason.name,
        )
    }

    private fun cancelAutomaticRejoin(reason: String) {
        val current = pendingRejoin ?: return
        if (current.reason != LocalRejoinReason.AUTO_AUDIO_FOCUS) return
        pendingRejoin = null
        lastWaitingReason = null
        diagnostics.debug(
            "playback.rejoin.cancelled",
            "playback.rejoin_reason" to current.reason.name,
            "reason" to reason,
        )
    }

    private fun clearPendingRejoin(reason: String) {
        val cleared = pendingRejoin ?: return
        pendingRejoin = null
        lastWaitingReason = null
        diagnostics.debug(
            "playback.rejoin.cleared",
            "playback.rejoin_reason" to cleared.reason.name,
            "reason" to reason,
        )
    }

    private fun claimPendingAttempt(): PendingRejoin? {
        val pending = pendingRejoin ?: return null
        val nowNs = clock.nowNs()
        if (
            lastAttemptAtNs != Long.MIN_VALUE &&
                nowNs >= lastAttemptAtNs &&
                nowNs - lastAttemptAtNs < REJOIN_RETRY_INTERVAL_NS
        )
            return null
        lastAttemptAtNs = nowNs
        return pending
    }

    private fun isAttemptCurrent(pending: PendingRejoin): Boolean =
        pendingRejoin?.token == pending.token

    private fun releasePendingAttempt(pending: PendingRejoin, completed: Boolean) {
        if (completed && pendingRejoin?.token == pending.token) {
            pendingRejoin = null
            lastWaitingReason = null
        }
    }

    private fun recordWaiting(pending: PendingRejoin, reason: String) {
        if (pendingRejoin?.token != pending.token || lastWaitingReason == reason) return
        lastWaitingReason = reason
        diagnostics.debug(
            "playback.rejoin.waiting",
            "playback.rejoin_reason" to pending.reason.name,
            "command.id" to pending.commandId?.take(12),
            "reason" to reason,
        )
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
