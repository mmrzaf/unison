package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.playback.PlayerExecutor
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.util.MonotonicClock

/**
 * Owns device-local participation in room playback.
 *
 * Audio-focus and noisy-route interruptions never mutate canonical room transport. An inhibited
 * device remains a silent follower until an explicit Play atomically positions it on the live
 * canonical item and projected position. Local output participation and synchronization health are
 * intentionally separate concepts: successful local playback returns to ACTIVE immediately while
 * the sync engine independently reacquires/converges.
 */
internal class LocalPlaybackParticipationCoordinator(
    private val player: PlayerPort,
    private val playerExecutor: PlayerExecutor,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val playbackSession: PlaybackSessionCoordinator,
    private val isCoordinator: () -> Boolean,
    private val refreshPlayerQueue: suspend (RoomSnapshot, QueueItemId, Long) -> Unit,
    private val executeImmediatePlay: suspend (String, suspend PlayerPort.() -> Boolean) -> Unit,
    private val resetLocalSynchronization: suspend () -> Unit,
    private val publishStatus: suspend (ProtocolBody.PlaybackStatusReport) -> Unit,
    private val onCoordinatorCohortChanged: suspend () -> Unit,
    private val setError: (String) -> Unit,
    private val diagnostics: RoomDiagnostics,
) {
    private var lastParticipation: LocalPlaybackParticipation? = null

    suspend fun observe(value: PlayerState, snapshot: RoomSnapshot?) {
        if (value.participation == lastParticipation) return
        val previous = lastParticipation
        lastParticipation = value.participation
        diagnostics.info(
            "playback.participation.changed",
            "playback.participation_from" to previous?.name,
            "playback.participation_to" to value.participation.name,
            "playback.inhibition_reason" to value.inhibitionReason?.name,
        )
        snapshot ?: return
        if (isCoordinator()) onCoordinatorCohortChanged()
        else publishStatus(statusReport(value, snapshot))
    }

    fun statusReport(value: PlayerState, snapshot: RoomSnapshot): ProtocolBody.PlaybackStatusReport {
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

    suspend fun rejoin(commandId: String, snapshot: RoomSnapshot) {
        val canonical = snapshot.playback
        val queueItemId = canonical.queueItemId ?: return
        if (!canonical.isPlaying) return
        if (!isCoordinator() && !clockSync.synchronized) {
            setError("Room clock is still synchronizing. Try Rejoin again in a moment.")
            executeImmediatePlay(commandId) { false }
            return
        }

        val targetPositionMs = canonical.projectedPositionMs(coordinatorNowNs())
        diagnostics.info(
            "playback.rejoin.requested",
            "command.id" to commandId.take(12),
            "queue.item_id" to queueItemId.value.take(12),
            "playback.position_ms" to targetPositionMs,
            "playback.inhibition_reason" to player.state.value.inhibitionReason?.name,
        )
        refreshPlayerQueue(snapshot, queueItemId, targetPositionMs)
        executeImmediatePlay(commandId) {
            setPlaybackSpeed(1f)
            rejoinLivePlayback(queueItemId, canonical.projectedPositionMs(coordinatorNowNs()))
        }
        val local = player.state.value
        if (
            local.participation == LocalPlaybackParticipation.ACTIVE &&
                local.playWhenReady &&
                local.queueItemId == queueItemId
        ) {
            resetPlaybackSynchronization()
            publishStatus(statusReport(local, snapshot))
        }
    }

    /**
     * Local interruption state belongs to one room session only. A fresh room must never inherit a
     * stale headphone/call inhibition from the previous room. This reset never starts playback.
     */
    suspend fun resetForSessionBoundary() {
        val before = player.state.value
        playerExecutor.maintenance { resetLocalPlaybackParticipation() }
        val after = player.state.value
        lastParticipation = after.participation
        if (
            before.participation != after.participation ||
                before.inhibitionReason != after.inhibitionReason
        ) {
            diagnostics.debug(
                "playback.participation.session_reset",
                "playback.participation_from" to before.participation.name,
                "playback.participation_to" to after.participation.name,
                "playback.inhibition_reason_from" to before.inhibitionReason?.name,
                "playback.inhibition_reason_to" to after.inhibitionReason?.name,
            )
        }
    }

    private suspend fun resetPlaybackSynchronization() {
        resetLocalSynchronization()
        diagnostics.info("sync.reacquire.required", "reason" to "local_output_rejoin")
    }

    private fun coordinatorNowNs(): Long =
        if (isCoordinator()) clock.nowNs() else clockSync.coordinatorNowNs()

}
