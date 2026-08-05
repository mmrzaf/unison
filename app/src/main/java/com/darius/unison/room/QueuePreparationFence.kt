package com.darius.unison.room

/**
 * Revision fence for asynchronous local queue preparation.
 *
 * Several Add music operations may run concurrently within one epoch. A destructive command such as
 * Clear queue advances the epoch, making every result from the previous epoch stale without
 * coupling repository work to the room actor or relying on cancellation timing.
 */
internal class QueuePreparationFence {
    data class Ticket(val epoch: Long, val operation: Long)

    private val lock = Any()
    private var epoch = 0L
    private var nextOperation = 0L

    fun issue(): Ticket = synchronized(lock) { Ticket(epoch, ++nextOperation) }

    fun invalidate(): Long =
        synchronized(lock) {
            epoch++
            epoch
        }

    fun isCurrent(ticket: Ticket): Boolean = synchronized(lock) { ticket.epoch == epoch }

    fun currentEpoch(): Long = synchronized(lock) { epoch }
}
