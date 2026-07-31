package com.darius.unison.playback

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single serialization and stale-generation boundary for every Media3 mutation.
 * Explicit/scheduled transport owns priority over synchronization and timeline maintenance.
 */
class PlayerMutationCoordinator(private val player: PlayerPort) {
    class Ticket internal constructor(val generation: Long, val commandId: String?)

    enum class ExecutionResult {
        SUCCESS,
        FAILED,
        STALE,
    }

    private val mutationMutex = Mutex()
    private val stateLock = Any()
    private var generation = 0L
    private var pendingTicket: Ticket? = null

    val hasPendingTransport: Boolean
        get() = synchronized(stateLock) { pendingTicket != null }

    fun beginTransport(commandId: String?): Pair<Ticket, String?> =
        synchronized(stateLock) {
            val superseded = pendingTicket?.commandId
            val ticket = Ticket(++generation, commandId)
            pendingTicket = ticket
            ticket to superseded
        }

    fun invalidateTransport(): String? =
        synchronized(stateLock) {
            generation++
            pendingTicket.also { pendingTicket = null }?.commandId
        }

    suspend fun executeTransport(
        ticket: Ticket,
        action: suspend PlayerPort.() -> Boolean,
    ): ExecutionResult = mutationMutex.withLock {
        if (!isCurrent(ticket)) return@withLock ExecutionResult.STALE
        try {
            if (player.action()) ExecutionResult.SUCCESS else ExecutionResult.FAILED
        } finally {
            synchronized(stateLock) {
                if (pendingTicket == ticket) pendingTicket = null
            }
        }
    }

    suspend fun maintenance(action: suspend PlayerPort.() -> Unit) = mutationMutex.withLock {
        player.action()
    }

    /**
     * Runs non-urgent timeline maintenance only when no explicit transport operation owns the
     * player. This prevents a queue refresh from slipping between a synchronized command being
     * scheduled and executed. The caller may retry after the transport generation settles.
     */
    suspend fun maintenanceIfTransportIdle(action: suspend PlayerPort.() -> Unit): Boolean =
        mutationMutex.withLock {
            if (hasPendingTransport) return@withLock false
            player.action()
            true
        }

    suspend fun synchronize(action: suspend PlayerPort.() -> Unit): Boolean =
        mutationMutex.withLock {
            if (hasPendingTransport) return@withLock false
            player.action()
            true
        }

    private fun isCurrent(ticket: Ticket): Boolean =
        synchronized(stateLock) {
            pendingTicket == ticket && generation == ticket.generation
        }
}
