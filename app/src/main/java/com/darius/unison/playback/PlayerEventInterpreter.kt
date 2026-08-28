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

    private var lastHandledBoundaryRevision = 0L
    private var lastHandledFinalEndedItem: QueueItemId? = null

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
                lastHandledFinalEndedItem = ended
                return Action.PlaybackEnded(
                    queueItemId = ended,
                    positionMs = state.boundaryEndedPositionMs,
                    durationMs = state.boundaryEndedDurationMs,
                )
            }
        }

        val itemId = state.queueItemId
        if (!state.ended || itemId == null || lastHandledFinalEndedItem == itemId) return Action.None
        lastHandledFinalEndedItem = itemId
        return Action.PlaybackEnded(itemId, state.positionMs, state.durationMs)
    }

    fun reset(currentState: PlayerState) {
        lastHandledBoundaryRevision = currentState.itemBoundaryRevision
        lastHandledFinalEndedItem = null
    }
}
