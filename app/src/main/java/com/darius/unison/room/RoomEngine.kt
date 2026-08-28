package com.darius.unison.room

import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.UserCommand
import com.darius.unison.protocol.ProtocolBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Atomic canonical-state holder. All work performed while its mutex is held is pure in-memory
 * reduction; network, player, database, channel, and file effects are executed by RoomRuntime only
 * after the transition has committed.
 */
class RoomEngine(initialSnapshot: RoomSnapshot) {
    private val mutex = Mutex()
    private var current = initialSnapshot

    suspend fun snapshot(): RoomSnapshot = mutex.withLock { current }

    suspend fun replace(snapshot: RoomSnapshot): RoomSnapshot = mutex.withLock {
        if (
            snapshot.term.number > current.term.number ||
                (snapshot.term.number == current.term.number &&
                    snapshot.sequence >= current.sequence)
        )
            current = snapshot
        current
    }

    suspend fun decide(
        command: UserCommand,
        coordinatorNowNs: Long,
        leadNs: Long = RoomReducer.DEFAULT_COMMAND_LEAD_NS,
        preparedQueueItemIds: Set<com.darius.unison.model.QueueItemId> = emptySet(),
        acceptsSnapshot: (RoomSnapshot) -> Boolean = { true },
    ): RoomReducer.Decision = mutex.withLock {
        when (val decision = RoomReducer.decide(
            current, command, coordinatorNowNs, leadNs, preparedQueueItemIds
        )) {
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

    suspend fun apply(sequence: Long, body: ProtocolBody): RoomSnapshot = mutex.withLock {
        current = RoomReducer.applyCanonical(current, sequence, body)
        current
    }

    /**
     * Applies a local mutation only when its complete candidate snapshot passes the supplied gate.
     */
    suspend fun applyValidated(
        sequence: Long,
        body: ProtocolBody,
        acceptsSnapshot: (RoomSnapshot) -> Boolean,
    ): RoomSnapshot? = mutex.withLock {
        val candidate = RoomReducer.applyCanonical(current, sequence, body)
        if (!acceptsSnapshot(candidate)) return@withLock null
        current = candidate
        current
    }
}
