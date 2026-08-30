package com.darius.unison.room

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.UserCommand

/**
 * Resolves relative transport intent to a stable absolute queue target before canonical mutation.
 *
 * Arbitrary item selection remains explicit: unavailable items must be prepared first. Next is
 * stronger sequential intent, so an unavailable immediate successor is returned as a preparation
 * target without skipping ahead. RoomRuntime owns the single pending-successor lifecycle.
 */
object TransportTargetPolicy {
    data class Resolution(
        val command: UserCommand? = null,
        val alreadyAligned: Boolean = false,
        val waitForPreparationQueueItemId: QueueItemId? = null,
        val resumeWhenReady: Boolean = false,
        val rejection: String? = null,
    )

    fun resolve(
        command: UserCommand,
        snapshot: RoomSnapshot,
        coordinatorNowNs: Long,
        preparedQueueItemIds: Set<QueueItemId> = emptySet(),
    ): Resolution {
        return when (command) {
            is UserCommand.SkipNext ->
                resolveRelative(
                    command = command,
                    snapshot = snapshot,
                    delta = 1,
                    baseId = snapshot.playback.queueItemId,
                    emptyOrBoundaryMessage = "Already at the end of the queue",
                    preparedQueueItemIds = preparedQueueItemIds,
                    waitForPreparation = true,
                )

            is UserCommand.SkipPrevious -> {
                if (
                    snapshot.playback.projectedPositionMs(coordinatorNowNs) > RESTART_THRESHOLD_MS
                ) {
                    Resolution(
                        command = UserCommand.Seek(command.commandId, command.requestedBy, 0L)
                    )
                } else {
                    resolvePrevious(command, snapshot, preparedQueueItemIds)
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
                if (snapshot.requiresPreparation(target.queueItemId, preparedQueueItemIds)) {
                    Resolution(rejection = "Prepare this song before playing it")
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
        preparedQueueItemIds: Set<QueueItemId>,
    ): Resolution {
        if (snapshot.queue.isEmpty()) return Resolution(rejection = "The queue is empty")
        val baseIndex =
            snapshot.queue
                .indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
                .let { if (it < 0) 0 else it }
        val target = snapshot.queue[(baseIndex - 1).coerceAtLeast(0)]
        if (target.queueItemId == snapshot.playback.queueItemId) {
            return Resolution(
                command = UserCommand.Seek(command.commandId, command.requestedBy, 0L)
            )
        }
        return targetResolution(command, snapshot, target.queueItemId, preparedQueueItemIds)
    }

    private fun resolveRelative(
        command: UserCommand,
        snapshot: RoomSnapshot,
        delta: Int,
        baseId: QueueItemId?,
        emptyOrBoundaryMessage: String,
        preparedQueueItemIds: Set<QueueItemId>,
        waitForPreparation: Boolean = false,
    ): Resolution {
        if (snapshot.queue.isEmpty()) return Resolution(rejection = "The queue is empty")
        val baseIndex = snapshot.queue.indexOfFirst { it.queueItemId == baseId }
        val targetIndex = (baseIndex + delta).coerceAtLeast(0)
        val target =
            snapshot.queue.getOrNull(targetIndex)
                ?: return Resolution(rejection = emptyOrBoundaryMessage)
        return targetResolution(
            command,
            snapshot,
            target.queueItemId,
            preparedQueueItemIds,
            waitForPreparation,
        )
    }

    private fun targetResolution(
        command: UserCommand,
        snapshot: RoomSnapshot,
        queueItemId: QueueItemId,
        preparedQueueItemIds: Set<QueueItemId>,
        waitForPreparation: Boolean = false,
    ): Resolution =
        if (snapshot.requiresPreparation(queueItemId, preparedQueueItemIds)) {
            if (waitForPreparation) {
                Resolution(
                    waitForPreparationQueueItemId = queueItemId,
                    resumeWhenReady = snapshot.playback.isPlaying,
                )
            } else {
                Resolution(rejection = "Prepare this song before playing it")
            }
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

    private fun RoomSnapshot.requiresPreparation(
        queueItemId: QueueItemId,
        preparedQueueItemIds: Set<QueueItemId>,
    ): Boolean = queueItemId !in preparedQueueItemIds

    private const val RESTART_THRESHOLD_MS = 4_000L
}
