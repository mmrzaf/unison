package com.darius.unison.playback

import com.darius.unison.model.QueueItemId

/**
 * Converts Media3 observations into boundary events. Canonical room state -- never Media3's newly
 * selected playlist item -- decides the successor. Programmatic item transitions remain telemetry.
 */
class PlayerEventInterpreter {
    sealed interface Action {
        data object None : Action

        data class PlaybackEnded(
            val queueItemId: QueueItemId,
            val positionMs: Long,
            val durationMs: Long,
        ) : Action
    }

    private data class EndCycleKey(
        val queueItemId: QueueItemId,
        val seekRevision: Long,
        val itemTransitionRevision: Long,
    )

    private var lastHandledBoundaryRevision = 0L
    private var lastHandledEndCycle: EndCycleKey? = null

    fun observe(state: PlayerState, coordinator: Boolean, nowNs: Long): Action {
        @Suppress("UNUSED_VARIABLE") val observedAtNs = nowNs
        if (!coordinator) {
            lastHandledBoundaryRevision =
                maxOf(lastHandledBoundaryRevision, state.itemBoundaryRevision)
            return Action.None
        }

        if (state.itemBoundaryRevision > lastHandledBoundaryRevision) {
            lastHandledBoundaryRevision = state.itemBoundaryRevision
            val ended = state.boundaryEndedQueueItemId
            if (ended != null) {
                val cycle = state.endCycleKey(ended)
                // Some Media3 timelines report STATE_ENDED immediately before the explicit
                // END_OF_MEDIA_ITEM callback. Both observations describe the same physical
                // boundary and must collapse to one canonical PlaybackEnded event.
                if (lastHandledEndCycle == cycle) return Action.None
                lastHandledEndCycle = cycle
                return Action.PlaybackEnded(
                    queueItemId = ended,
                    positionMs = state.boundaryEndedPositionMs,
                    durationMs = state.boundaryEndedDurationMs,
                )
            }
        }

        val itemId = state.queueItemId
        if (!state.ended || itemId == null) return Action.None
        val cycle = state.endCycleKey(itemId)
        if (lastHandledEndCycle == cycle) return Action.None
        lastHandledEndCycle = cycle
        return Action.PlaybackEnded(itemId, state.positionMs, state.durationMs)
    }

    fun reset(currentState: PlayerState) {
        lastHandledBoundaryRevision = currentState.itemBoundaryRevision
        lastHandledEndCycle = null
    }

    private fun PlayerState.endCycleKey(queueItemId: QueueItemId): EndCycleKey =
        EndCycleKey(
            queueItemId = queueItemId,
            seekRevision = seekRevision,
            itemTransitionRevision = itemTransitionRevision,
        )
}
