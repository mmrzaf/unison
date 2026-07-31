package com.darius.unison.room

import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase

/**
 * Bounded coordinator-side routing table for transport command feedback.
 *
 * Canonical transport events execute on every peer, but lifecycle feedback belongs only to the
 * device that submitted the command. This tracker keeps that routing explicit and prevents stale
 * command IDs from accumulating during long rooms.
 */
class TransportCommandTracker(private val maxEntries: Int = 256) {
    @JvmInline value class Ticket(val generation: Long)

    data class Route(
        val requestedBy: PeerId,
        val action: TransportAction,
        val queueItemId: QueueItemId? = null,
        val requestedPositionMs: Long? = null,
        val phase: TransportCommandPhase = TransportCommandPhase.SUBMITTED,
        val ticket: Ticket = Ticket(0L),
    )

    private val routes = LinkedHashMap<String, Route>()
    private val completed = LinkedHashMap<String, TransportCommandPhase>()
    private var nextTicket = 0L

    fun remember(commandId: String, route: Route): Ticket {
        require(commandId.isNotBlank()) { "commandId must not be blank" }
        routes[commandId]?.let {
            return it.ticket
        }
        completed[commandId]?.let {
            return Ticket(0L)
        }
        val ticket = Ticket(++nextTicket)
        routes[commandId] =
            route.copy(
                phase = TransportCommandPhase.SUBMITTED,
                ticket = ticket,
            )
        return ticket
    }

    fun updateTarget(
        commandId: String,
        queueItemId: QueueItemId? = null,
        requestedPositionMs: Long? = null,
    ): Route? {
        val existing = routes[commandId] ?: return null
        val updated =
            existing.copy(
                queueItemId = queueItemId ?: existing.queueItemId,
                requestedPositionMs = requestedPositionMs ?: existing.requestedPositionMs,
            )
        routes[commandId] = updated
        return updated
    }

    fun route(commandId: String): Route? = routes[commandId]

    fun route(commandId: String, ticket: Ticket): Route? =
        routes[commandId]?.takeIf { it.ticket == ticket }

    fun activeRoutes(): List<Pair<String, Route>> = routes.toList()

    fun isCompleted(commandId: String): Boolean = commandId in completed

    fun activeForQueueItem(
        queueItemId: QueueItemId,
        excludingCommandId: String? = null,
    ): Pair<String, Route>? =
        routes.entries
            .firstOrNull { (commandId, route) ->
                commandId != excludingCommandId && route.queueItemId == queueItemId
            }
            ?.let { it.key to it.value }

    sealed interface Transition {
        data class Applied(val route: Route) : Transition

        data object Duplicate : Transition

        data object Invalid : Transition

        data object AlreadyTerminal : Transition

        data object Unknown : Transition
    }

    fun transition(
        commandId: String,
        phase: TransportCommandPhase,
        queueItemId: QueueItemId? = null,
        requestedPositionMs: Long? = null,
    ): Transition {
        val current =
            routes[commandId]
                ?: return if (commandId in completed) Transition.AlreadyTerminal
                else Transition.Unknown
        if (phase == current.phase) return Transition.Duplicate
        if (!canAdvance(current.phase, phase)) return Transition.Invalid
        val updated =
            current.copy(
                phase = phase,
                queueItemId = queueItemId ?: current.queueItemId,
                requestedPositionMs = requestedPositionMs ?: current.requestedPositionMs,
            )
        if (phase.isTerminal) {
            routes.remove(commandId)
            rememberCompleted(commandId, phase)
        } else {
            routes[commandId] = updated
        }
        return Transition.Applied(updated)
    }

    fun complete(commandId: String): Route? =
        routes.remove(commandId)?.also {
            rememberCompleted(commandId, TransportCommandPhase.SETTLED)
        }

    fun drain(): List<Pair<String, Route>> =
        routes.toList().also { pending ->
            routes.clear()
            pending.forEach { (commandId, _) ->
                rememberCompleted(commandId, TransportCommandPhase.SUPERSEDED)
            }
        }

    fun clear() {
        routes.clear()
        completed.clear()
    }

    val size: Int
        get() = routes.size

    val completedSize: Int
        get() = completed.size

    private fun rememberCompleted(commandId: String, phase: TransportCommandPhase) {
        completed[commandId] = phase
        while (completed.size > maxEntries) completed.remove(completed.keys.first())
    }

    private fun canAdvance(from: TransportCommandPhase, to: TransportCommandPhase): Boolean {
        if (from.isTerminal) return false
        if (to.isTerminal) return true
        return phaseOrder(to) > phaseOrder(from)
    }

    private fun phaseOrder(phase: TransportCommandPhase): Int =
        when (phase) {
            TransportCommandPhase.SUBMITTED -> 0
            TransportCommandPhase.ACCEPTED -> 1
            TransportCommandPhase.SCHEDULED -> 2
            TransportCommandPhase.EXECUTING -> 3
            TransportCommandPhase.SETTLED,
            TransportCommandPhase.SUPERSEDED,
            TransportCommandPhase.REJECTED -> 4
        }
}
