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
        if (snapshot.term.number > current.term.number ||
            (snapshot.term.number == current.term.number && snapshot.sequence >= current.sequence)
        ) current = snapshot
        current
    }

    suspend fun decide(
        command: UserCommand,
        coordinatorNowNs: Long,
        acceptsSnapshot: (RoomSnapshot) -> Boolean = { true },
    ): RoomReducer.Decision = mutex.withLock {
        when (val decision = RoomReducer.decide(current, command, coordinatorNowNs)) {
            is RoomReducer.Decision.Accepted -> {
                if (decision.mutations.any { !acceptsSnapshot(it.snapshot) }) {
                    RoomReducer.Decision.Rejected("This change would make the room state too large")
                } else {
                    current = decision.mutations.lastOrNull()?.snapshot ?: current
                    decision
                }
            }

            is RoomReducer.Decision.Rejected -> decision
        }
    }

    suspend fun apply(sequence: Long, body: ProtocolBody): RoomSnapshot = mutex.withLock {
        current = RoomReducer.applyCanonical(current, sequence, body)
        current
    }

    /** Applies a local mutation only when its complete candidate snapshot passes the supplied gate. */
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
