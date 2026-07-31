package com.darius.unison.room

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.UserCommand

/**
 * Resolves relative transport intent to a stable absolute queue target before canonical mutation.
 *
 * Pending navigation is represented by queue-item ID rather than index, so concurrent queue edits
 * cannot silently retarget a user's command. The coordinator remains responsible for requesting
 * preparation and re-running the returned absolute command when the target becomes ready.
 */
object TransportTargetPolicy {
    data class Resolution(
        val command: UserCommand? = null,
        val pendingTarget: QueueItemId? = null,
        val pendingResumePlayback: Boolean? = null,
        val alreadyAligned: Boolean = false,
        val rejection: String? = null,
    )

    fun resolve(
        command: UserCommand,
        snapshot: RoomSnapshot,
        coordinatorNowNs: Long,
        pendingTarget: QueueItemId? = null,
    ): Resolution {
        return when (command) {
            is UserCommand.SkipNext ->
                resolveRelative(
                    command = command,
                    snapshot = snapshot,
                    delta = 1,
                    baseId = pendingTarget ?: snapshot.playback.queueItemId,
                    emptyOrBoundaryMessage = "Already at the end of the queue",
                )

            is UserCommand.SkipPrevious -> {
                if (
                    pendingTarget == null &&
                        snapshot.playback.projectedPositionMs(coordinatorNowNs) >
                            RESTART_THRESHOLD_MS
                ) {
                    Resolution(
                        command = UserCommand.Seek(command.commandId, command.requestedBy, 0L)
                    )
                } else {
                    resolvePrevious(command, snapshot, pendingTarget)
                }
            }

            is UserCommand.PlayQueueItem -> {
                val target =
                    snapshot.queue.firstOrNull { it.queueItemId == command.queueItemId }
                        ?: return Resolution(rejection = "That song is no longer in the queue")
                if (target.queueItemId == snapshot.playback.queueItemId) {
                    return when {
                        command.resumePlayback && snapshot.playback.isPlaying ->
                            Resolution(alreadyAligned = true)
                        command.resumePlayback ->
                            Resolution(
                                command = UserCommand.Play(command.commandId, command.requestedBy)
                            )
                        !snapshot.playback.isPlaying -> Resolution(alreadyAligned = true)
                        else ->
                            Resolution(
                                command = UserCommand.Pause(command.commandId, command.requestedBy)
                            )
                    }
                }
                if (snapshot.requiresPreparation(target.queueItemId)) {
                    Resolution(
                        pendingTarget = target.queueItemId,
                        pendingResumePlayback = command.resumePlayback,
                    )
                } else {
                    Resolution(command = command)
                }
            }

            else -> Resolution(command = command)
        }
    }

    private fun resolvePrevious(
        command: UserCommand.SkipPrevious,
        snapshot: RoomSnapshot,
        pendingTarget: QueueItemId?,
    ): Resolution {
        if (snapshot.queue.isEmpty()) return Resolution(rejection = "The queue is empty")
        val baseId = pendingTarget ?: snapshot.playback.queueItemId
        val baseIndex =
            snapshot.queue.indexOfFirst { it.queueItemId == baseId }.let { if (it < 0) 0 else it }
        val target = snapshot.queue[(baseIndex - 1).coerceAtLeast(0)]
        if (target.queueItemId == snapshot.playback.queueItemId && pendingTarget == null) {
            return Resolution(
                command = UserCommand.Seek(command.commandId, command.requestedBy, 0L)
            )
        }
        return targetResolution(command, snapshot, target.queueItemId)
    }

    private fun resolveRelative(
        command: UserCommand,
        snapshot: RoomSnapshot,
        delta: Int,
        baseId: QueueItemId?,
        emptyOrBoundaryMessage: String,
    ): Resolution {
        if (snapshot.queue.isEmpty()) return Resolution(rejection = "The queue is empty")
        val baseIndex = snapshot.queue.indexOfFirst { it.queueItemId == baseId }
        val targetIndex = (baseIndex + delta).coerceAtLeast(0)
        val target =
            snapshot.queue.getOrNull(targetIndex)
                ?: return Resolution(rejection = emptyOrBoundaryMessage)
        return targetResolution(command, snapshot, target.queueItemId)
    }

    private fun targetResolution(
        command: UserCommand,
        snapshot: RoomSnapshot,
        queueItemId: QueueItemId,
    ): Resolution =
        if (snapshot.requiresPreparation(queueItemId)) {
            Resolution(
                pendingTarget = queueItemId,
                pendingResumePlayback = snapshot.playback.isPlaying,
            )
        } else {
            Resolution(
                command =
                    UserCommand.PlayQueueItem(
                        commandId = command.commandId,
                        requestedBy = command.requestedBy,
                        queueItemId = queueItemId,
                        resumePlayback = snapshot.playback.isPlaying,
                    )
            )
        }

    private fun RoomSnapshot.requiresPreparation(queueItemId: QueueItemId): Boolean =
        options.waitAtTrackBoundary && queueItemId !in preparedQueueItemIds

    private const val RESTART_THRESHOLD_MS = 4_000L
}
