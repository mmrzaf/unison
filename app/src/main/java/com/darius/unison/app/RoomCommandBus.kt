package com.darius.unison.app

import com.darius.unison.model.AppCommand
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Process-local command mailbox for the room service.
 *
 * Transport has an independent high-priority lane, so a large library or room-management burst
 * cannot delay or reject Pause, Play, Seek, Next, or Previous. [outstandingCount] includes queued
 * and currently executing commands and therefore remains a service-lifecycle barrier.
 */
class RoomCommandBus {
    private val generalCommands = Channel<AppCommand>(capacity = GENERAL_CAPACITY)
    private val transportCommands = Channel<AppCommand.Transport>(capacity = TRANSPORT_CAPACITY)
    private val outstanding = AtomicInteger(0)

    val transportFlow: Flow<AppCommand.Transport> = transportCommands.receiveAsFlow()
    val generalFlow: Flow<AppCommand> = generalCommands.receiveAsFlow()

    val outstandingCount: Int
        get() = outstanding.get()

    val hasOutstandingCommands: Boolean
        get() = outstandingCount > 0

    suspend fun send(command: AppCommand) {
        outstanding.incrementAndGet()
        try {
            when (command) {
                is AppCommand.Transport -> transportCommands.send(command)
                else -> generalCommands.send(command)
            }
        } catch (error: Throwable) {
            outstanding.decrementAndGet()
            throw error
        }
    }

    fun trySend(command: AppCommand): ChannelResult<Unit> {
        outstanding.incrementAndGet()
        val result =
            when (command) {
                is AppCommand.Transport -> transportCommands.trySend(command)
                else -> generalCommands.trySend(command)
            }
        if (result.isFailure) outstanding.decrementAndGet()
        return result
    }

    /**
     * Must be called exactly once after each command emitted by a service lane, including
     * cancellation.
     */
    fun complete() {
        val remaining = outstanding.decrementAndGet()
        check(remaining >= 0) { "Room command completion was not paired with an accepted command" }
    }

    companion object {
        const val GENERAL_CAPACITY = 64
        const val TRANSPORT_CAPACITY = 256
    }
}
