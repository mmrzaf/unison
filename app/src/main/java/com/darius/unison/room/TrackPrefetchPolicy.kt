package com.darius.unison.room

import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress

/** Selects a small deterministic transfer window around the canonical current item. */
object TrackPrefetchPolicy {
    const val DEFAULT_UPCOMING_COUNT = 3
    const val KEEP_NEAR_COMPLETION_FRACTION = 0.85f

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
     * Builds the active transfer window with an explicitly requested item first. A rapid series of
     * Next/Previous actions therefore replaces obsolete speculative work instead of accumulating
     * every intermediate target for the lifetime of the room.
     */
    fun prioritizedDesiredItems(
        snapshot: RoomSnapshot,
        priorityQueueItemId: QueueItemId?,
        upcomingCount: Int = DEFAULT_UPCOMING_COUNT,
    ): List<QueueItem> {
        val regular = desiredItems(snapshot, upcomingCount)
        val priority = priorityQueueItemId?.let { requestedId ->
            snapshot.queue.firstOrNull { it.queueItemId == requestedId }
        }
        return buildList {
            priority?.let(::add)
            regular.forEach { candidate ->
                if (none { it.track.trackId == candidate.track.trackId }) add(candidate)
            }
        }
    }

    fun cancellableObsoleteTracks(
        previousDesired: Set<TrackId>,
        nextDesired: Set<TrackId>,
        progressByTrack: Map<TrackId, TransferProgress>,
    ): Set<TrackId> =
        (previousDesired - nextDesired).filterTo(linkedSetOf()) { trackId ->
            val progress = progressByTrack[trackId]
            progress == null || progress.fraction < KEEP_NEAR_COMPLETION_FRACTION
        }
}
