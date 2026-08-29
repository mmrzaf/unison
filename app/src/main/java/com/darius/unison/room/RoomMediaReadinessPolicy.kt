package com.darius.unison.room

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackId

/**
 * Derives the small user-facing media state from runtime facts without polluting canonical history.
 *
 * [roomReadyQueueItemIds] is the coordinator's ephemeral projection for the active listening cohort.
 * A phone still requires its own verified local copy before it may present READY or execute Media3
 * work. [explicitPreparationQueueItemIds] represents deliberate Prepare intent; background prefetch
 * may still make an item become READY without ever exposing PREPARING.
 */
object RoomMediaReadinessPolicy {
    fun derive(
        snapshot: RoomSnapshot,
        roomReadyQueueItemIds: Set<QueueItemId>,
        explicitPreparationQueueItemIds: Set<QueueItemId>,
        locallyAvailableTrackIds: Set<TrackId>,
    ): Map<QueueItemId, RoomMediaReadiness> =
        snapshot.queue.associate { item ->
            val readiness =
                when {
                    item.queueItemId in roomReadyQueueItemIds &&
                        item.track.trackId in locallyAvailableTrackIds -> RoomMediaReadiness.READY
                    item.queueItemId in explicitPreparationQueueItemIds ->
                        RoomMediaReadiness.PREPARING
                    else -> RoomMediaReadiness.NEEDS_PREPARATION
                }
            item.queueItemId to readiness
        }

    fun canPlay(
        queueItemId: QueueItemId,
        readiness: Map<QueueItemId, RoomMediaReadiness>,
    ): Boolean = readiness[queueItemId] == RoomMediaReadiness.READY
}
