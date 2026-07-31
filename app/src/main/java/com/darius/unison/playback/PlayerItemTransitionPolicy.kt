package com.darius.unison.playback

/**
 * Converts an observed Media3 item transition into canonical-room intent.
 *
 * Programmatic seeks and playlist reconciliation are observations of work Unison already requested.
 * Only automatic progression and repeat callbacks may create new canonical mutations.
 */
object PlayerItemTransitionPolicy {
    fun evaluate(lastHandledRevision: Long, state: PlayerState): Decision {
        if (state.itemTransitionRevision <= lastHandledRevision) {
            return Decision(lastHandledRevision, Action.NONE)
        }
        val action =
            when (state.itemTransitionReason) {
                PlayerItemTransitionReason.AUTO -> Action.NATURAL_ADVANCE
                PlayerItemTransitionReason.REPEAT -> Action.NATURAL_REPEAT
                PlayerItemTransitionReason.SEEK,
                PlayerItemTransitionReason.PLAYLIST_CHANGED,
                PlayerItemTransitionReason.UNKNOWN,
                null -> Action.NONE
            }
        return Decision(state.itemTransitionRevision, action)
    }

    data class Decision(
        val handledRevision: Long,
        val action: Action,
    )

    enum class Action {
        NONE,
        NATURAL_ADVANCE,
        NATURAL_REPEAT,
    }
}
