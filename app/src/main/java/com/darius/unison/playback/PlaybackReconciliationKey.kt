package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomSnapshot

/**
 * Minimal identity for replaceable Media3 timeline/preparation reconciliation.
 *
 * Exact transport commands are ordered separately. This key intentionally contains only state that
 * can change the local timeline or preparation window; membership, clock telemetry, and
 * playback-position revisions must not manufacture redundant Media3 work.
 */
data class PlaybackReconciliationKey(
    val queueRevision: Long,
    val queueItemIds: List<QueueItemId>,
    val currentQueueItemId: QueueItemId?,
    val preparedQueueItemIds: Set<QueueItemId>,
    val repeatMode: RepeatMode,
    val preloadCount: Int,
) {
    companion object {
        fun from(
            snapshot: RoomSnapshot,
            preparedQueueItemIds: Set<QueueItemId>,
        ): PlaybackReconciliationKey =
            PlaybackReconciliationKey(
                queueRevision = snapshot.queueRevision,
                queueItemIds = snapshot.queue.map { it.queueItemId },
                currentQueueItemId = snapshot.playback.queueItemId,
                preparedQueueItemIds = preparedQueueItemIds.toSet(),
                repeatMode = snapshot.repeatMode,
                preloadCount = snapshot.options.preloadCount,
            )
    }
}
