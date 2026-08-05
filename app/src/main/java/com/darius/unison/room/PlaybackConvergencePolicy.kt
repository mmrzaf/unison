package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.protocol.ProtocolBody

/**
 * Turns peer playback receipts into bounded repair actions.
 *
 * Queue identity and transport intent are repaired before position drift. Position remains owned by
 * PlaybackSyncController; this policy only guarantees that every ready peer is applying the same
 * canonical item and play/pause revision.
 */
internal class PlaybackConvergencePolicy(
    private val executionGraceNs: Long = DEFAULT_EXECUTION_GRACE_NS,
    private val minimumRepairIntervalNs: Long = DEFAULT_REPAIR_INTERVAL_NS,
) {
    sealed interface Action {
        data object None : Action

        data class SendPlaybackState(val reason: String) : Action

        data class SendSnapshot(val reason: String) : Action
    }

    private enum class RepairKind {
        PLAYBACK_STATE,
        SNAPSHOT,
    }

    private data class RepairStamp(
        val queueRevision: Long,
        val playbackRevision: Long,
        val kind: RepairKind,
        val sentAtNs: Long,
    )

    private val lock = Any()
    private val lastRepairByPeer = mutableMapOf<PeerId, RepairStamp>()

    fun decide(
        peerId: PeerId,
        snapshot: RoomSnapshot,
        report: ProtocolBody.PlaybackStatusReport,
        coordinatorNowNs: Long,
    ): Action {
        val candidate = decideUnthrottled(snapshot, report, coordinatorNowNs)
        if (candidate == Action.None) {
            synchronized(lock) { lastRepairByPeer.remove(peerId) }
            return Action.None
        }
        val kind =
            when (candidate) {
                is Action.SendPlaybackState -> RepairKind.PLAYBACK_STATE
                is Action.SendSnapshot -> RepairKind.SNAPSHOT
                Action.None -> error("None was handled before repair throttling")
            }
        val shouldSend =
            synchronized(lock) {
                val previous = lastRepairByPeer[peerId]
                val duplicate =
                    previous != null &&
                        previous.queueRevision == snapshot.queueRevision &&
                        previous.playbackRevision == snapshot.playback.revision &&
                        previous.kind == kind &&
                        coordinatorNowNs - previous.sentAtNs < minimumRepairIntervalNs
                if (!duplicate) {
                    lastRepairByPeer[peerId] =
                        RepairStamp(
                            queueRevision = snapshot.queueRevision,
                            playbackRevision = snapshot.playback.revision,
                            kind = kind,
                            sentAtNs = coordinatorNowNs,
                        )
                }
                !duplicate
            }
        return if (shouldSend) candidate else Action.None
    }

    fun forget(peerId: PeerId) {
        synchronized(lock) { lastRepairByPeer.remove(peerId) }
    }

    fun reset() {
        synchronized(lock) { lastRepairByPeer.clear() }
    }

    private fun decideUnthrottled(
        snapshot: RoomSnapshot,
        report: ProtocolBody.PlaybackStatusReport,
        coordinatorNowNs: Long,
    ): Action {
        if (
            report.canonicalSequence < 0L ||
                report.queueRevision < 0L ||
                report.playbackRevision < 0L
        ) {
            return Action.None
        }
        // A peer cannot legitimately know a future coordinator revision. Ignore the malformed or
        // cross-session receipt instead of allowing it to influence canonical state.
        if (
            report.queueRevision > snapshot.queueRevision ||
                report.playbackRevision > snapshot.playback.revision
        ) {
            return Action.None
        }
        if (report.queueRevision < snapshot.queueRevision) {
            return Action.SendSnapshot("QUEUE_REVISION_BEHIND")
        }
        if (report.playbackRevision < snapshot.playback.revision) {
            return Action.SendPlaybackState("PLAYBACK_REVISION_BEHIND")
        }

        val canonical = snapshot.playback
        if (
            canonical.coordinatorTimestampNs > 0L &&
                coordinatorNowNs < canonical.coordinatorTimestampNs + executionGraceNs
        ) {
            return Action.None
        }
        if (report.queueItemId != canonical.queueItemId) {
            return Action.SendPlaybackState("WRONG_QUEUE_ITEM")
        }
        if (report.isPlaying != canonical.isPlaying) {
            return Action.SendPlaybackState("WRONG_PLAY_STATE")
        }
        return Action.None
    }

    companion object {
        const val DEFAULT_EXECUTION_GRACE_NS = 500_000_000L
        const val DEFAULT_REPAIR_INTERVAL_NS = 750_000_000L
    }
}
