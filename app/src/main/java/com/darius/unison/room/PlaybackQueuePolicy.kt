package com.darius.unison.room

import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.ProtocolBody

/** Pure queue decisions used by the Android playback runtime and unit tests. */
object PlaybackQueuePolicy {
    data class NaturalEndPlan(
        val mutation: ProtocolBody,
        val waitForQueueItemId: QueueItemId? = null,
    )

    /**
     * Returns a player timeline that can never jump over a missing song. Readable history remains
     * available for Previous, while the future is a contiguous run from the canonical current item.
     */
    fun playableItems(snapshot: RoomSnapshot, readableTrackIds: Set<TrackId>): List<QueueItem> {
        if (snapshot.queue.isEmpty()) return emptyList()
        val currentIndex =
            snapshot.queue
                .indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
                .let { if (it < 0) 0 else it }
        val roomAllowed =
            if (snapshot.options.waitAtTrackBoundary) {
                snapshot.queue.filter {
                    it.queueItemId == snapshot.playback.queueItemId ||
                        it.queueItemId in snapshot.preparedQueueItemIds
                }
            } else {
                val history =
                    snapshot.queue.take(currentIndex).filter {
                        it.track.trackId in readableTrackIds
                    }
                val future =
                    snapshot.queue.drop(currentIndex).takeWhile {
                        it.track.trackId in readableTrackIds
                    }
                history + future
            }
        return roomAllowed.filter { it.track.trackId in readableTrackIds }
    }

    fun playerWindow(
        snapshot: RoomSnapshot,
        historyCount: Int = 2,
        upcomingCount: Int = 12,
    ): List<QueueItem> {
        if (snapshot.queue.isEmpty()) return emptyList()
        val currentIndex =
            snapshot.queue
                .indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
                .let { if (it < 0) 0 else it }
        val start = (currentIndex - historyCount.coerceAtLeast(0)).coerceAtLeast(0)
        val endExclusive =
            (currentIndex + upcomingCount.coerceAtLeast(1) + 1).coerceAtMost(snapshot.queue.size)
        return snapshot.queue.subList(start, endExclusive)
    }

    fun planNaturalEnd(
        snapshot: RoomSnapshot,
        endedQueueItemId: QueueItemId,
        positionMs: Long,
        durationMs: Long,
        coordinatorNowNs: Long,
        leadNs: Long = RoomReducer.DEFAULT_COMMAND_LEAD_NS,
    ): NaturalEndPlan? {
        if (!snapshot.playback.isPlaying || snapshot.playback.queueItemId != endedQueueItemId)
            return null
        val currentIndex = snapshot.queue.indexOfFirst { it.queueItemId == endedQueueItemId }
        if (currentIndex < 0) return null
        val next =
            when (snapshot.repeatMode) {
                RepeatMode.ONE -> snapshot.queue[currentIndex]
                RepeatMode.ALL ->
                    snapshot.queue.getOrNull(currentIndex + 1) ?: snapshot.queue.firstOrNull()
                RepeatMode.OFF -> snapshot.queue.getOrNull(currentIndex + 1)
            }
        if (next == null) {
            return NaturalEndPlan(
                mutation =
                    ProtocolBody.PauseScheduled(
                        queueItemId = endedQueueItemId,
                        positionMs = maxOf(positionMs, durationMs).coerceAtLeast(0),
                        executeAtCoordinatorNs = coordinatorNowNs,
                    )
            )
        }
        val ready = next.queueItemId in snapshot.preparedQueueItemIds
        return NaturalEndPlan(
            mutation =
                ProtocolBody.CurrentItemChanged(
                    queueItemId = next.queueItemId,
                    positionMs = 0,
                    executeAtCoordinatorNs =
                        if (ready) coordinatorNowNs + leadNs else coordinatorNowNs,
                    resumePlayback = ready,
                ),
            waitForQueueItemId = next.queueItemId.takeUnless { ready },
        )
    }

    fun planRepeatTransition(
        snapshot: RoomSnapshot,
        repeatedQueueItemId: QueueItemId,
        positionMs: Long,
        coordinatorNowNs: Long,
    ): ProtocolBody.CurrentItemChanged? {
        if (
            snapshot.repeatMode != RepeatMode.ONE ||
                !snapshot.playback.isPlaying ||
                snapshot.playback.queueItemId != repeatedQueueItemId
        ) {
            return null
        }
        return ProtocolBody.CurrentItemChanged(
            queueItemId = repeatedQueueItemId,
            positionMs = 0,
            executeAtCoordinatorNs = coordinatorNowNs - positionMs.coerceAtLeast(0) * 1_000_000L,
            resumePlayback = true,
        )
    }
}
