package com.darius.unison.room

import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.UserCommand
import com.darius.unison.protocol.ProtocolBody

/**
 * Canonical-state holder owned exclusively by the room actor.
 *
 * It deliberately has no synchronization: every method must be called from a room actor turn. A
 * second caller would be a design error, not a locking problem, so the type makes that visible.
 */
class RoomEngine(initialSnapshot: RoomSnapshot) {
    private var current = initialSnapshot

    fun snapshot(): RoomSnapshot = current

    fun replace(snapshot: RoomSnapshot): RoomSnapshot {
        if (
            snapshot.term.number > current.term.number ||
                (snapshot.term.number == current.term.number &&
                    snapshot.sequence >= current.sequence)
        )
            current = snapshot
        return current
    }

    fun decide(
        command: UserCommand,
        coordinatorNowNs: Long,
        leadNs: Long = RoomReducer.DEFAULT_COMMAND_LEAD_NS,
        preparedQueueItemIds: Set<com.darius.unison.model.QueueItemId> = emptySet(),
        playPositionOverrideMs: Long? = null,
        acceptsSnapshot: (RoomSnapshot) -> Boolean = { true },
    ): RoomReducer.Decision {
        return when (
            val decision =
                RoomReducer.decide(
                    current,
                    command,
                    coordinatorNowNs,
                    leadNs,
                    preparedQueueItemIds,
                    playPositionOverrideMs,
                )
        ) {
            is RoomReducer.Decision.Accepted -> {
                if (decision.mutations.all { acceptsSnapshot(it.snapshot) }) {
                    current = decision.mutations.lastOrNull()?.snapshot ?: current
                    decision
                } else if (command is UserCommand.QueueAdd) {
                    val fitting =
                        largestFittingQueueAdd(command, coordinatorNowNs, acceptsSnapshot, leadNs)
                    if (fitting == null) {
                        RoomReducer.Decision.Rejected(
                            "The selected songs do not fit in the room queue"
                        )
                    } else {
                        current = fitting.mutations.last().snapshot
                        fitting
                    }
                } else {
                    RoomReducer.Decision.Rejected("This change would make the room state too large")
                }
            }

            is RoomReducer.Decision.Rejected -> decision
        }
    }

    private fun largestFittingQueueAdd(
        command: UserCommand.QueueAdd,
        coordinatorNowNs: Long,
        acceptsSnapshot: (RoomSnapshot) -> Boolean,
        leadNs: Long,
    ): RoomReducer.Decision.Accepted? {
        var low = 1
        var high =
            command.tracks.size.coerceAtMost(RoomReducer.MAX_QUEUE_ITEMS - current.queue.size)
        var best: RoomReducer.Decision.Accepted? = null
        while (low <= high) {
            val count = (low + high) ushr 1
            val candidate =
                RoomReducer.decide(
                    current,
                    command.copy(tracks = command.tracks.take(count)),
                    coordinatorNowNs,
                    leadNs,
                ) as? RoomReducer.Decision.Accepted
            if (candidate != null && candidate.mutations.all { acceptsSnapshot(it.snapshot) }) {
                best = candidate
                low = count + 1
            } else {
                high = count - 1
            }
        }
        return best
    }

    fun apply(sequence: Long, body: ProtocolBody): RoomSnapshot {
        current = RoomReducer.applyCanonical(current, sequence, body)
        return current
    }

    /**
     * Applies a local mutation only when its complete candidate snapshot passes the supplied gate.
     */
    fun applyValidated(
        sequence: Long,
        body: ProtocolBody,
        acceptsSnapshot: (RoomSnapshot) -> Boolean,
    ): RoomSnapshot? {
        val candidate = RoomReducer.applyCanonical(current, sequence, body)
        if (!acceptsSnapshot(candidate)) return null
        current = candidate
        return current
    }
}
