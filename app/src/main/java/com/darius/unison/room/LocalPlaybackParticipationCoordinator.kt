package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.playback.PlayerMutationCoordinator
import com.darius.unison.playback.PlayerPort
import com.darius.unison.playback.PlayerState
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.sync.ClockSyncEngine
import com.darius.unison.sync.PlaybackSyncState
import com.darius.unison.sync.SynchronizationDiagnostics
import com.darius.unison.util.MonotonicClock

/**
 * Owns device-local participation in room playback.
 *
 * Audio-focus and noisy-route interruptions never mutate canonical room transport. An inhibited
 * device remains a silent follower until an explicit Play asks to rejoin the live canonical item
 * and projected position. REJOINING devices stay outside the READY timing cohort until fresh sync
 * samples converge.
 */
internal class LocalPlaybackParticipationCoordinator(
    private val player: PlayerPort,
    private val playerMutations: PlayerMutationCoordinator,
    private val clock: MonotonicClock,
    private val clockSync: ClockSyncEngine,
    private val playbackSession: PlaybackSessionCoordinator,
    private val isCoordinator: () -> Boolean,
    private val refreshPlayerQueue: suspend (RoomSnapshot, QueueItemId, Long) -> Unit,
    private val executeImmediatePlay: suspend (String, suspend PlayerPort.() -> Boolean) -> Unit,
    private val playbackSynchronization: PlaybackSynchronizationRuntime,
    private val syncDiagnostics: SynchronizationDiagnostics,
    private val clearLocalDrift: suspend () -> Unit,
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
            beginLocalRejoin()
            setPlaybackSpeed(1f)
            val positioned = seekToItem(queueItemId, canonical.projectedPositionMs(coordinatorNowNs()))
            if (!positioned) return@executeImmediatePlay false
            play()
        }
        if (player.state.value.participation == LocalPlaybackParticipation.REJOINING) {
            resetPlaybackSynchronization()
            publishStatus(statusReport(player.state.value, snapshot))
        }
    }

    suspend fun completeRejoinIfSynchronized(syncState: PlaybackSyncState, snapshot: RoomSnapshot) {
        if (
            syncState != PlaybackSyncState.TRACKING ||
                player.state.value.participation != LocalPlaybackParticipation.REJOINING
        ) {
            return
        }
        playerMutations.synchronize { completeLocalRejoin() }
        publishStatus(statusReport(player.state.value, snapshot))
    }

    private suspend fun resetPlaybackSynchronization() {
        playbackSynchronization.reset(preserveLearnedBaseline = false)
        syncDiagnostics.clear()
        clearLocalDrift()
        if (kotlin.math.abs(player.state.value.playbackSpeed - 1f) > PLAYBACK_SPEED_EPSILON) {
            playerMutations.synchronize { setPlaybackSpeed(1f) }
        }
        diagnostics.info("sync.reacquire.required", "reason" to "local_output_rejoin")
    }

    private fun coordinatorNowNs(): Long =
        if (isCoordinator()) clock.nowNs() else clockSync.coordinatorNowNs()

    companion object {
        private const val PLAYBACK_SPEED_EPSILON = 0.0001f
    }
}
