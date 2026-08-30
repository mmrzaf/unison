package com.darius.unison.room

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot

/**
 * Runtime-only proof that the current paused playback state came from the natural terminal boundary
 * of the final queue item. The marker never enters canonical state or the wire protocol.
 */
data class TerminalNaturalPause(
    val queueItemId: QueueItemId,
    val queueRevision: Long,
    val playbackRevision: Long,
)

/** Canonical replay policy for Play after a genuine final natural boundary. */
object TerminalReplayPolicy {
    fun capture(snapshot: RoomSnapshot, endedQueueItemId: QueueItemId): TerminalNaturalPause? {
        if (snapshot.playback.queueItemId != endedQueueItemId) return null
        if (snapshot.playback.isPlaying) return null
        if (PlaybackQueuePolicy.naturalSuccessorQueueItemId(snapshot, endedQueueItemId) != null) {
            return null
        }
        return TerminalNaturalPause(
            queueItemId = endedQueueItemId,
            queueRevision = snapshot.queueRevision,
            playbackRevision = snapshot.playback.revision,
        )
    }

    fun playPositionOverrideMs(
        snapshot: RoomSnapshot,
        marker: TerminalNaturalPause?,
    ): Long? {
        val terminal = marker ?: return null
        if (snapshot.playback.isPlaying) return null
        if (snapshot.playback.queueItemId != terminal.queueItemId) return null
        if (snapshot.queueRevision != terminal.queueRevision) return null
        if (snapshot.playback.revision != terminal.playbackRevision) return null
        if (PlaybackQueuePolicy.naturalSuccessorQueueItemId(snapshot, terminal.queueItemId) != null) {
            return null
        }
        return 0L
    }

    fun isStillValid(snapshot: RoomSnapshot, marker: TerminalNaturalPause?): Boolean =
        playPositionOverrideMs(snapshot, marker) != null
}
