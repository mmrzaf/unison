package com.darius.unison.room

import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.UserCommand
import com.darius.unison.protocol.ProtocolBody
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes all canonical room mutations, making races reproducible and testable. */
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

    suspend fun decide(command: UserCommand, coordinatorNowNs: Long): RoomReducer.Decision = mutex.withLock {
        when (val decision = RoomReducer.decide(current, command, coordinatorNowNs)) {
            is RoomReducer.Decision.Accepted -> {
                current = decision.mutations.lastOrNull()?.snapshot ?: current
                decision
            }

            is RoomReducer.Decision.Rejected -> decision
        }
    }

    suspend fun apply(sequence: Long, body: ProtocolBody): RoomSnapshot = mutex.withLock {
        current = RoomReducer.applyCanonical(current, sequence, body)
        current
    }
}
