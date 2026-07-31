package com.darius.unison.playback

import com.darius.unison.model.QueueItemId

/**
 * Stateful interpreter for Media3 observations. It owns callback revision tracking, end-of-item
 * deduplication, and automatic-transition loop detection so room orchestration receives explicit
 * domain decisions instead of inferring intent from raw player state.
 */
class PlayerEventInterpreter(
    private val transitionCircuitBreaker: PlayerTransitionCircuitBreaker =
        PlayerTransitionCircuitBreaker()
) {
    sealed interface Action {
        data object None : Action

        data class NaturalRepeat(val queueItemId: QueueItemId, val positionMs: Long) : Action

        data class NaturalAdvance(val queueItemId: QueueItemId, val positionMs: Long) : Action

        data class PlaybackEnded(
            val queueItemId: QueueItemId,
            val positionMs: Long,
            val durationMs: Long,
        ) : Action

        data object TransitionLoopDetected : Action
    }

    private var lastObservedPlayerItem: QueueItemId? = null
    private var lastHandledEndedItem: QueueItemId? = null
    private var lastObservedSeekRevision = 0L
    private var lastObservedItemTransitionRevision = 0L

    fun observe(state: PlayerState, coordinator: Boolean, nowNs: Long): Action {
        if (state.seekRevision > lastObservedSeekRevision) {
            lastObservedSeekRevision = state.seekRevision
        }
        val previous = lastObservedPlayerItem
        lastObservedPlayerItem = state.queueItemId
        val transition =
            PlayerItemTransitionPolicy.evaluate(lastObservedItemTransitionRevision, state)
        lastObservedItemTransitionRevision = transition.handledRevision
        if (previous != state.queueItemId || !state.ended) lastHandledEndedItem = null

        if (!coordinator) return Action.None
        val itemId = state.queueItemId ?: return Action.None
        return when {
            transition.action == PlayerItemTransitionPolicy.Action.NATURAL_REPEAT ->
                Action.NaturalRepeat(itemId, state.positionMs)

            transition.action == PlayerItemTransitionPolicy.Action.NATURAL_ADVANCE &&
                state.playWhenReady ->
                when (transitionCircuitBreaker.record(nowNs)) {
                    PlayerTransitionCircuitBreaker.Result.ALLOW ->
                        Action.NaturalAdvance(itemId, state.positionMs)
                    PlayerTransitionCircuitBreaker.Result.TRIPPED -> Action.TransitionLoopDetected
                    PlayerTransitionCircuitBreaker.Result.BLOCKED -> Action.None
                }

            state.ended && lastHandledEndedItem != itemId -> {
                lastHandledEndedItem = itemId
                Action.PlaybackEnded(itemId, state.positionMs, state.durationMs)
            }

            else -> Action.None
        }
    }

    fun reset(currentState: PlayerState) {
        lastObservedPlayerItem = null
        lastHandledEndedItem = null
        lastObservedSeekRevision = currentState.seekRevision
        lastObservedItemTransitionRevision = currentState.itemTransitionRevision
        transitionCircuitBreaker.reset()
    }
}
