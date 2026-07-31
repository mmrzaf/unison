package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransportAction
import com.darius.unison.model.UserCommand

/**
 * Actor-owned pending navigation state.
 *
 * Relative navigation may use the current pending target as its base so rapid Next/Previous input
 * remains deterministic. The previous request must then be consumed and terminally superseded;
 * otherwise a prepared target can remain hidden and make later navigation restart the same item.
 */
internal data class PendingTrackTransition(
    val commandId: String,
    val action: TransportAction,
    val requestedBy: PeerId,
    val queueItemId: QueueItemId,
    val trackId: TrackId,
    val resumePlayback: Boolean,
)

internal class PendingTrackTransitionRegistry {
    private var current: PendingTrackTransition? = null

    fun peek(): PendingTrackTransition? = current

    fun activeForTarget(queueItemId: QueueItemId): PendingTrackTransition? = current?.takeIf {
        it.queueItemId == queueItemId
    }

    fun relativeNavigationBase(command: UserCommand): QueueItemId? =
        when (command) {
            is UserCommand.SkipNext,
            is UserCommand.SkipPrevious -> current?.queueItemId
            else -> null
        }

    fun replace(next: PendingTrackTransition): PendingTrackTransition? {
        val previous = current
        current = next
        return previous
    }

    fun updateResumePlayback(resumePlayback: Boolean): PendingTrackTransition? {
        val updated = current?.copy(resumePlayback = resumePlayback)
        current = updated
        return updated
    }

    fun clear(): PendingTrackTransition? {
        val previous = current
        current = null
        return previous
    }

    fun clearIfCommand(commandId: String): PendingTrackTransition? =
        current?.takeIf { it.commandId == commandId }?.also { current = null }

    fun matches(commandId: String, queueItemId: QueueItemId? = null): Boolean =
        current?.let {
            it.commandId == commandId && (queueItemId == null || it.queueItemId == queueItemId)
        } == true
}
