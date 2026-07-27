package com.darius.unison.room

import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomSnapshot

/** Pure admission policy for a Play request; runtime side effects are deliberately kept elsewhere. */
object PlaybackRequestPolicy {
    fun currentItem(snapshot: RoomSnapshot): QueueItem? = snapshot.playback.queueItemId
        ?.let { id -> snapshot.queue.firstOrNull { it.queueItemId == id } }
        ?: snapshot.queue.firstOrNull()

    fun shouldDeferPlay(snapshot: RoomSnapshot): Boolean {
        val current = currentItem(snapshot) ?: return false
        return snapshot.options.waitAtTrackBoundary && current.queueItemId !in snapshot.preparedQueueItemIds
    }
}
