package com.darius.unison.room

import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferPriority

/** Selects deterministic playback demand around the canonical current item. */
object TrackPrefetchPolicy {
    const val DEFAULT_UPCOMING_COUNT = 3
    const val NEXT_TRACK_SAFETY_MARGIN_MS = 8_000L

    fun desiredItems(
        snapshot: RoomSnapshot,
        upcomingCount: Int = DEFAULT_UPCOMING_COUNT,
    ): List<QueueItem> {
        if (snapshot.queue.isEmpty()) return emptyList()
        val currentIndex =
            snapshot.queue
                .indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
                .let { if (it < 0) 0 else it }
        return snapshot.queue
            .drop(currentIndex)
            .asSequence()
            .distinctBy { it.track.trackId }
            .take(upcomingCount.coerceAtLeast(0) + 1)
            .toList()
    }

    /**
     * Converts queue look-ahead into playback-aware transfer demand. The next boundary gets a real
     * deadline; farther items are runway rather than pretending every prefetched file is equally
     * urgent.
     */
    internal fun transferDemands(
        snapshot: RoomSnapshot,
        destinationPeerId: com.darius.unison.model.PeerId,
        coordinatorNowNs: Long,
        priorityQueueItemId: QueueItemId? = null,
        upcomingCount: Int = DEFAULT_UPCOMING_COUNT,
    ): List<TransferDemand> {
        if (snapshot.queue.isEmpty()) return emptyList()
        val regular = desiredItems(snapshot, upcomingCount)
        val explicit = priorityQueueItemId?.let { requestedId ->
            snapshot.queue.firstOrNull { it.queueItemId == requestedId }
        }
        val currentId = snapshot.playback.queueItemId
        val currentIndex = snapshot.queue.indexOfFirst { it.queueItemId == currentId }
        val current = snapshot.queue.getOrNull(if (currentIndex >= 0) currentIndex else 0)
        val projectedPosition = snapshot.playback.projectedPositionMs(coordinatorNowNs)
        val remainingCurrentMs =
            current
                ?.track
                ?.durationMs
                ?.takeIf { it > 0L }
                ?.let { (it - projectedPosition).coerceAtLeast(0L) }
        val nextBoundaryNs = remainingCurrentMs?.let { remaining ->
            coordinatorNowNs +
                (remaining - NEXT_TRACK_SAFETY_MARGIN_MS).coerceAtLeast(0L) * 1_000_000L
        }

        val result = linkedMapOf<TrackId, TransferDemand>()
        fun add(item: QueueItem, priority: TransferPriority, deadline: Long?) {
            val demand =
                TransferDemand(
                    trackId = item.track.trackId,
                    destinationPeerId = destinationPeerId,
                    priority = priority,
                    neededByCoordinatorNs = deadline,
                    requestedAtCoordinatorNs = coordinatorNowNs,
                )
            val old = result[item.track.trackId]
            if (old == null || priority.ordinal < old.priority.ordinal) {
                result[item.track.trackId] = demand
            }
        }

        explicit?.let { add(it, TransferPriority.USER_SELECTED, coordinatorNowNs) }
        regular.forEachIndexed { index, item ->
            val priority =
                when {
                    item.queueItemId == currentId -> TransferPriority.CURRENT_REQUIRED
                    index == 1 -> TransferPriority.NEXT_BOUNDARY
                    index <= upcomingCount -> TransferPriority.PLAYBACK_RUNWAY
                    else -> TransferPriority.BACKGROUND
                }
            val deadline = if (priority == TransferPriority.NEXT_BOUNDARY) nextBoundaryNs else null
            add(item, priority, deadline)
        }
        return result.values.toList()
    }
}
