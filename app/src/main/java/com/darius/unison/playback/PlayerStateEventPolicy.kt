package com.darius.unison.playback

import com.darius.unison.model.QueueItemId

/**
 * Extracts the part of [PlayerState] that can change canonical room behavior.
 *
 * Position and duration telemetry are intentionally excluded: they update UI directly and must not
 * occupy the serialized room actor while audio is advancing normally.
 */
object PlayerStateEventPolicy {
    fun key(state: PlayerState): Key =
        Key(
            queueItemId = state.queueItemId,
            playWhenReady = state.playWhenReady,
            ended = state.ended,
            error = state.error,
            seekRevision = state.seekRevision,
            itemTransitionRevision = state.itemTransitionRevision,
            itemTransitionReason = state.itemTransitionReason,
        )

    data class Key(
        val queueItemId: QueueItemId?,
        val playWhenReady: Boolean,
        val ended: Boolean,
        val error: String?,
        val seekRevision: Long,
        val itemTransitionRevision: Long,
        val itemTransitionReason: PlayerItemTransitionReason?,
    )
}
