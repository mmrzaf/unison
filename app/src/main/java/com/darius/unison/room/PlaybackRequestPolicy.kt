package com.darius.unison.room

import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomSnapshot

/**
 * Pure admission policy for a Play request; runtime side effects are deliberately kept elsewhere.
 */
object PlaybackRequestPolicy {
    fun currentItem(snapshot: RoomSnapshot): QueueItem? =
        snapshot.playback.queueItemId?.let { id ->
            snapshot.queue.firstOrNull { it.queueItemId == id }
        } ?: snapshot.queue.firstOrNull()

    fun requiresPreparationForPlay(snapshot: RoomSnapshot): Boolean = requiresPreparationForPlay(snapshot, emptySet())

    fun requiresPreparationForPlay(
        snapshot: RoomSnapshot,
        preparedQueueItemIds: Set<com.darius.unison.model.QueueItemId>,
    ): Boolean {
        val current = currentItem(snapshot) ?: return false
        return current.queueItemId !in preparedQueueItemIds
    }
}
