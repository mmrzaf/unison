package com.darius.unison.network

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.protocol.Envelope
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull

class ControlConnection(
    val peerId: PeerId,
    val endpoint: PeerEndpoint,
    private val channel: EnvelopeChannel,
    parentScope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val clock: MonotonicClock,
    private val log: DiagnosticLog,
    private val onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
    private val onClosed: suspend (ControlConnection, Throwable?) -> Unit,
) {
    /** Remote host observed on the authenticated control socket; never participant-announced. */
    val authenticatedRemoteHostAddress: String = channel.remoteHostAddress

    private val callbackScope = parentScope
    private val supervisorJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + supervisorJob)

    /**
     * Ordered state and commands. Saturation is fatal because dropping one creates a history gap.
     */
    private data class Outgoing(
        val envelope: Envelope,
        val written: CompletableDeferred<Boolean>? = null,
    )

    private val guaranteed = Channel<Outgoing>(capacity = GUARANTEED_CAPACITY)

    /** Pings, pongs, heartbeats, and ACKs. Old timing samples are less useful than current ones. */
    private val clockTraffic =
        Channel<Outgoing>(
            capacity = CLOCK_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** Only the latest canonical playback reference matters to a lagging peer. */
    private val playbackReference = Channel<Outgoing>(capacity = Channel.CONFLATED)

    /** UI-only status is explicitly replaceable. */
    private val telemetry = Channel<Outgoing>(capacity = Channel.CONFLATED)

    /** Transfer coordination is bounded independently from room state and timing traffic. */
    private val transfer = Channel<Outgoing>(capacity = TRANSFER_CAPACITY)

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val pendingWrites = mutableSetOf<CompletableDeferred<Boolean>>()

    fun start() {
        if (!started.compareAndSet(false, true) || closed.get()) return
        scope.launch(dispatcher) {
            try {
                while (isActive) {
                    val outgoing = nextOutgoing() ?: break
                    channel.send(outgoing.envelope)
                    outgoing.written?.let { ack ->
                        synchronized(pendingWrites) { pendingWrites.remove(ack) }
                        ack.complete(true)
                    }
                }
            } catch (cancelled: CancellationException) {
                close(cancelled)
                throw cancelled
            } catch (error: Exception) {
                close(error)
            }
        }
        scope.launch(dispatcher) {
            try {
                while (isActive) {
                    val envelope = channel.receive() ?: break
                    val dispatchStartedNs = clock.nowNs()
                    try {
                        onEnvelope(this@ControlConnection, envelope)
                    } finally {
                        val dispatchDurationNs =
                            (clock.nowNs() - dispatchStartedNs).coerceAtLeast(0L)
                        if (dispatchDurationNs >= SLOW_INGRESS_DISPATCH_NS) {
                            log.warn(
                                TAG,
                                DiagnosticCategory.NETWORK,
                                "network.control_ingress.slow",
                                attributes =
                                    mapOf(
                                        "peer.id" to peerId.value.take(12),
                                        "mutation.type" to envelope.body::class.simpleName,
                                        "message.id" to envelope.messageId.take(12),
                                        "room.sequence" to envelope.sequence,
                                        "operation.duration_ms" to dispatchDurationNs / 1_000_000L,
                                    ),
                            )
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                close(cancelled)
                throw cancelled
            } catch (error: Exception) {
                close(error)
            }
        }
    }

    suspend fun send(envelope: Envelope) {
        if (closed.get()) return
        val trafficClass = ControlTrafficClassifier.classify(envelope)
        val delivered =
            try {
                when (trafficClass) {
                    ControlTrafficClass.GUARANTEED ->
                        timedSend(guaranteed, Outgoing(envelope), GUARANTEED_SEND_TIMEOUT_MS)
                    ControlTrafficClass.CLOCK -> clockTraffic.trySend(Outgoing(envelope)).isSuccess
                    ControlTrafficClass.PLAYBACK_REFERENCE ->
                        playbackReference.trySend(Outgoing(envelope)).isSuccess
                    ControlTrafficClass.TELEMETRY -> telemetry.trySend(Outgoing(envelope)).isSuccess
                    ControlTrafficClass.TRANSFER ->
                        timedSend(transfer, Outgoing(envelope), TRANSFER_SEND_TIMEOUT_MS)
                }
            } catch (_: ClosedSendChannelException) {
                false
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                close(error)
                false
            }
        if (!delivered && !closed.get()) {
            val reason =
                when (trafficClass) {
                    ControlTrafficClass.GUARANTEED -> "Guaranteed control queue is full"
                    ControlTrafficClass.TRANSFER -> "Transfer control queue is full"
                    else -> "Control queue is closed"
                }
            close(IllegalStateException(reason))
        }
    }

    fun trySend(envelope: Envelope): Boolean {
        if (closed.get()) return false
        return when (ControlTrafficClassifier.classify(envelope)) {
            ControlTrafficClass.GUARANTEED -> guaranteed.trySend(Outgoing(envelope)).isSuccess
            ControlTrafficClass.CLOCK -> clockTraffic.trySend(Outgoing(envelope)).isSuccess
            ControlTrafficClass.PLAYBACK_REFERENCE ->
                playbackReference.trySend(Outgoing(envelope)).isSuccess
            ControlTrafficClass.TELEMETRY -> telemetry.trySend(Outgoing(envelope)).isSuccess
            ControlTrafficClass.TRANSFER -> transfer.trySend(Outgoing(envelope)).isSuccess
        }
    }

    private suspend fun timedSend(
        target: Channel<Outgoing>,
        outgoing: Outgoing,
        timeoutMs: Long,
    ): Boolean =
        withTimeoutOrNull(timeoutMs) {
            target.send(outgoing)
            true
        } ?: false

    /**
     * Enqueues a guaranteed envelope and suspends until the transport has written it. Used for
     * terminal messages that must be flushed before the connection is closed.
     */
    suspend fun sendAndAwaitWrite(envelope: Envelope): Boolean {
        if (closed.get()) return false
        val ack = CompletableDeferred<Boolean>()
        synchronized(pendingWrites) { pendingWrites += ack }
        val queued =
            withTimeoutOrNull(GUARANTEED_SEND_TIMEOUT_MS) {
                guaranteed.send(Outgoing(envelope, ack))
                true
            } ?: false
        if (!queued) {
            synchronized(pendingWrites) { pendingWrites.remove(ack) }
            return false
        }
        return withTimeoutOrNull(GUARANTEED_SEND_TIMEOUT_MS) { ack.await() } ?: false
    }

    /**
     * Drain ready high-priority queues before suspending. Canonical ordering is preserved because
     * every ordered envelope uses the same single-consumer [guaranteed] channel.
     */
    /** Test seam for exercising queue arbitration without starting reader/writer jobs. */
    internal suspend fun nextOutgoingForTest(): Envelope? = nextOutgoing()?.envelope

    private suspend fun nextOutgoing(): Outgoing? {
        guaranteed.tryReceive().getOrNull()?.let {
            return it
        }
        clockTraffic.tryReceive().getOrNull()?.let {
            return it
        }
        playbackReference.tryReceive().getOrNull()?.let {
            return it
        }
        transfer.tryReceive().getOrNull()?.let {
            return it
        }
        telemetry.tryReceive().getOrNull()?.let {
            return it
        }

        return select {
            guaranteed.onReceiveCatching { it.getOrNull() }
            clockTraffic.onReceiveCatching { it.getOrNull() }
            playbackReference.onReceiveCatching { it.getOrNull() }
            transfer.onReceiveCatching { it.getOrNull() }
            telemetry.onReceiveCatching { it.getOrNull() }
        }
    }

    fun close(cause: Throwable? = null) {
        shutdown(cause, notifyClosed = true)
    }

    fun closeSilently() {
        shutdown(cause = null, notifyClosed = false)
    }

    suspend fun closeAndJoin(notifyClosed: Boolean = false) {
        shutdown(cause = null, notifyClosed = notifyClosed)
        withTimeoutOrNull(CLOSE_JOIN_TIMEOUT_MS) { supervisorJob.join() }
    }

    private fun shutdown(cause: Throwable?, notifyClosed: Boolean) {
        if (!closed.compareAndSet(false, true)) return
        guaranteed.close()
        clockTraffic.close()
        playbackReference.close()
        telemetry.close()
        transfer.close()
        runCatching { channel.close(cause) }
        scope.cancel()
        synchronized(pendingWrites) {
            pendingWrites.forEach { it.complete(false) }
            pendingWrites.clear()
        }
        when (cause) {
            null,
            is CancellationException ->
                log.info(
                    TAG,
                    DiagnosticCategory.NETWORK,
                    "network.control_connection.closed",
                    attributes = mapOf("peer.id" to peerId.value.take(12)),
                )

            else ->
                log.error(
                    TAG,
                    DiagnosticCategory.NETWORK,
                    "network.control_connection.failed",
                    attributes = mapOf("peer.id" to peerId.value.take(12)),
                    throwable = cause,
                )
        }
        if (notifyClosed) {
            callbackScope.launch(dispatcher) { onClosed(this@ControlConnection, cause) }
        }
    }

    companion object {
        private const val TAG = "ControlConnection"
        private const val GUARANTEED_CAPACITY = 128
        private const val CLOCK_CAPACITY = 32
        private const val TRANSFER_CAPACITY = 64
        private const val GUARANTEED_SEND_TIMEOUT_MS = 2_000L
        private const val TRANSFER_SEND_TIMEOUT_MS = 1_000L
        private const val CLOSE_JOIN_TIMEOUT_MS = 2_000L
        private const val SLOW_INGRESS_DISPATCH_NS = 100_000_000L
    }
}
