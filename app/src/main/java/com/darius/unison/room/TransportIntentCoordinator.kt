package com.darius.unison.room

import com.darius.unison.model.AppCommand
import com.darius.unison.model.UserCommand
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Single-owner transport intent ingress.
 *
 * Play/Pause share one replaceable debounce lane and Seek has another. Discrete navigation is an
 * ordering barrier: pending replaceable intent is flushed before Next/Previous/item selection, so
 * accepted commands preserve user order without concurrent collectors. Local and remote commands
 * share the same lanes when this runtime is the coordinator.
 */
class TransportIntentCoordinator(
    scope: CoroutineScope,
    private val onAccepted: suspend (Intent) -> Unit,
    private val onSuperseded: suspend (Intent) -> Unit,
    private val playPauseDebounceMs: Long = 45L,
    private val seekDebounceMs: Long = 90L,
    capacity: Int = DEFAULT_CAPACITY,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : AutoCloseable {
    sealed interface Intent {
        val commandId: String
        val epoch: Long

        data class Local(
            val command: AppCommand.Transport,
            val completion: CompletableDeferred<Unit>,
            override val epoch: Long,
        ) : Intent {
            override val commandId: String
                get() = command.commandId
        }

        data class Remote(
            val generation: Long,
            val command: UserCommand,
            override val epoch: Long,
        ) : Intent {
            override val commandId: String
                get() = command.commandId
        }
    }

    private sealed interface Message {
        data class Submit(val intent: Intent) : Message

        data class Invalidate(val epoch: Long) : Message
    }

    private data class Pending(
        val intent: Intent,
        val sequence: Long,
        val deadlineMs: Long,
    )

    private enum class Lane {
        PLAY_PAUSE,
        SEEK,
    }

    private val epoch = AtomicLong(0L)
    private val input = Channel<Message>(capacity)
    private var sequence = 0L
    private val worker: Job = scope.launch { runLoop() }

    fun submit(command: AppCommand.Transport, completion: CompletableDeferred<Unit>): Boolean =
        input.trySend(Message.Submit(Intent.Local(command, completion, epoch.get()))).isSuccess

    fun submit(generation: Long, command: UserCommand): Boolean =
        input.trySend(Message.Submit(Intent.Remote(generation, command, epoch.get()))).isSuccess

    /** Invalidates pending/queued intent immediately by epoch and wakes the single owner. */
    fun invalidateAll() {
        val invalidatedThrough = epoch.incrementAndGet()
        input.trySend(Message.Invalidate(invalidatedThrough))
    }

    private suspend fun runLoop() {
        val pending = mutableMapOf<Lane, Pending>()
        while (true) {
            discardInvalidPending(pending)
            val oldest = pending.values.minByOrNull(Pending::sequence)
            if (oldest == null) {
                val message = input.receiveCatching().getOrNull() ?: break
                handleMessage(message, pending)
                continue
            }

            val waitMs = (oldest.deadlineMs - nowMs()).coerceAtLeast(0L)
            val received =
                if (waitMs == 0L) {
                    null
                } else {
                    withTimeoutOrNull(waitMs) { input.receiveCatching() }
                }
            if (received == null) {
                dispatchOldestReady(pending)
                continue
            }
            val message = received.getOrNull()
            if (message == null) {
                flushPending(pending)
                break
            }
            handleMessage(message, pending)
        }
    }

    private suspend fun handleMessage(message: Message, pending: MutableMap<Lane, Pending>) {
        when (message) {
            is Message.Invalidate ->
                supersedeMatching(pending) { it.intent.epoch < message.epoch }

            is Message.Submit -> {
                val intent = message.intent
                if (intent.epoch != epoch.get()) {
                    supersede(intent)
                    return
                }
                val lane = lane(intent)
                if (lane == null) {
                    // Discrete navigation is an ordering barrier. Flush earlier effective intent
                    // immediately rather than delaying a Next/Previous press behind debounce time.
                    flushPending(pending)
                    dispatch(intent)
                    return
                }
                pending.remove(lane)?.let { supersede(it.intent) }
                pending[lane] =
                    Pending(
                        intent = intent,
                        sequence = ++sequence,
                        deadlineMs = nowMs() + debounceMs(lane),
                    )
            }
        }
    }

    private suspend fun discardInvalidPending(pending: MutableMap<Lane, Pending>) {
        val currentEpoch = epoch.get()
        supersedeMatching(pending) { it.intent.epoch != currentEpoch }
    }

    private suspend fun supersedeMatching(
        pending: MutableMap<Lane, Pending>,
        predicate: (Pending) -> Boolean,
    ) {
        val stale = pending.entries.filter { predicate(it.value) }
        stale.forEach { (lane, value) ->
            pending.remove(lane)
            supersede(value.intent)
        }
    }

    private suspend fun dispatchOldestReady(pending: MutableMap<Lane, Pending>) {
        val oldest = pending.minByOrNull { it.value.sequence } ?: return
        if (oldest.value.deadlineMs > nowMs()) return
        pending.remove(oldest.key)
        dispatch(oldest.value.intent)
    }

    private suspend fun flushPending(pending: MutableMap<Lane, Pending>) {
        val ordered = pending.values.sortedBy(Pending::sequence)
        pending.clear()
        ordered.forEach { dispatch(it.intent) }
    }

    private suspend fun dispatch(intent: Intent) {
        if (intent.epoch != epoch.get()) {
            supersede(intent)
            return
        }
        try {
            onAccepted(intent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            (intent as? Intent.Local)?.completion?.completeExceptionally(error)
        }
    }

    private suspend fun supersede(intent: Intent) {
        try {
            onSuperseded(intent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            (intent as? Intent.Local)?.completion?.completeExceptionally(error)
        }
    }

    private fun lane(intent: Intent): Lane? =
        when (intent) {
            is Intent.Local -> lane(intent.command)
            is Intent.Remote -> lane(intent.command)
        }

    private fun lane(command: AppCommand.Transport): Lane? =
        when (command) {
            is AppCommand.Play,
            is AppCommand.Pause -> Lane.PLAY_PAUSE
            is AppCommand.Seek -> Lane.SEEK
            is AppCommand.SkipNext,
            is AppCommand.SkipPrevious,
            is AppCommand.PlayQueueItem -> null
        }

    private fun lane(command: UserCommand): Lane? =
        when (command) {
            is UserCommand.Play,
            is UserCommand.Pause -> Lane.PLAY_PAUSE
            is UserCommand.Seek -> Lane.SEEK
            else -> null
        }

    private fun debounceMs(lane: Lane): Long =
        when (lane) {
            Lane.PLAY_PAUSE -> playPauseDebounceMs
            Lane.SEEK -> seekDebounceMs
        }

    override fun close() {
        invalidateAll()
        input.close()
        worker.cancel()
    }

    companion object {
        const val DEFAULT_CAPACITY = 256
    }
}
