package com.darius.unison.network

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.FrameCodec
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    private val socket: Socket,
    private val codec: FrameCodec,
    parentScope: CoroutineScope,
    private val log: DiagnosticLog,
    private val onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
    private val onClosed: suspend (ControlConnection, Throwable?) -> Unit,
) {
    /** Remote host observed on the authenticated control socket; never participant-announced. */
    val authenticatedRemoteHostAddress: String =
        requireNotNull(socket.inetAddress?.hostAddress) { "Control socket is not connected" }

    private val callbackScope = parentScope
    private val supervisorJob = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + supervisorJob)

    /**
     * Ordered state and commands. Saturation is fatal because dropping one creates a history gap.
     */
    private val guaranteed = Channel<Envelope>(capacity = GUARANTEED_CAPACITY)

    /** Pings, pongs, heartbeats, and ACKs. Old timing samples are less useful than current ones. */
    private val clock =
        Channel<Envelope>(
            capacity = CLOCK_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /** Only the latest canonical playback reference matters to a lagging peer. */
    private val playbackReference = Channel<Envelope>(capacity = Channel.CONFLATED)

    /** UI-only status is explicitly replaceable. */
    private val telemetry = Channel<Envelope>(capacity = Channel.CONFLATED)

    /** Transfer coordination is bounded independently from room state and timing traffic. */
    private val transfer = Channel<Envelope>(capacity = TRANSFER_CAPACITY)

    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true) || closed.get()) return
        scope.launch(Dispatchers.IO) {
            try {
                while (isActive && !socket.isClosed) {
                    val envelope = nextOutgoing() ?: break
                    codec.write(socket.getOutputStream(), envelope)
                }
            } catch (cancelled: CancellationException) {
                close(cancelled)
                throw cancelled
            } catch (error: Exception) {
                close(error)
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                while (isActive && !socket.isClosed) onEnvelope(
                    this@ControlConnection,
                    codec.read(socket.getInputStream()),
                )
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
                        timedSend(guaranteed, envelope, GUARANTEED_SEND_TIMEOUT_MS)
                    ControlTrafficClass.CLOCK -> clock.trySend(envelope).isSuccess
                    ControlTrafficClass.PLAYBACK_REFERENCE ->
                        playbackReference.trySend(envelope).isSuccess
                    ControlTrafficClass.TELEMETRY -> telemetry.trySend(envelope).isSuccess
                    ControlTrafficClass.TRANSFER ->
                        timedSend(transfer, envelope, TRANSFER_SEND_TIMEOUT_MS)
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
            ControlTrafficClass.GUARANTEED -> guaranteed.trySend(envelope).isSuccess
            ControlTrafficClass.CLOCK -> clock.trySend(envelope).isSuccess
            ControlTrafficClass.PLAYBACK_REFERENCE -> playbackReference.trySend(envelope).isSuccess
            ControlTrafficClass.TELEMETRY -> telemetry.trySend(envelope).isSuccess
            ControlTrafficClass.TRANSFER -> transfer.trySend(envelope).isSuccess
        }
    }

    private suspend fun timedSend(
        channel: Channel<Envelope>,
        envelope: Envelope,
        timeoutMs: Long,
    ): Boolean =
        withTimeoutOrNull(timeoutMs) {
            channel.send(envelope)
            true
        } ?: false

    /**
     * Drain ready high-priority queues before suspending. Canonical ordering is preserved because
     * every ordered envelope uses the same single-consumer [guaranteed] channel.
     */
    /** Test seam for exercising queue arbitration without starting socket reader/writer jobs. */
    internal suspend fun nextOutgoingForTest(): Envelope? = nextOutgoing()

    private suspend fun nextOutgoing(): Envelope? {
        guaranteed.tryReceive().getOrNull()?.let {
            return it
        }
        clock.tryReceive().getOrNull()?.let {
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
            clock.onReceiveCatching { it.getOrNull() }
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
        clock.close()
        playbackReference.close()
        telemetry.close()
        transfer.close()
        runCatching { socket.close() }
        scope.cancel()
        codec.close()
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
            callbackScope.launch(Dispatchers.Default) { onClosed(this@ControlConnection, cause) }
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
    }
}
