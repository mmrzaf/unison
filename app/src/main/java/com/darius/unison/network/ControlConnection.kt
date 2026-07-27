package com.darius.unison.network

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.FrameCodec
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

class ControlConnection(
    val peerId: PeerId,
    val endpoint: PeerEndpoint,
    private val socket: Socket,
    private val codec: FrameCodec,
    parentScope: CoroutineScope,
    private val log: DiagnosticLog,
    private val onEnvelope: suspend (PeerId, Envelope) -> Unit,
    private val onClosed: suspend (ControlConnection, Throwable?) -> Unit,
) {
    private val callbackScope = parentScope
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext[Job]))

    // One writer coroutine preserves frame order. A bounded queue applies backpressure instead
    // of allowing an unreachable peer to consume memory without limit; peer timeouts close it.
    private val outgoing = Channel<Envelope>(capacity = OUTGOING_CAPACITY)
    private val started = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true) || closed.get()) return
        scope.launch(Dispatchers.IO) {
            try {
                for (envelope in outgoing) codec.write(socket.getOutputStream(), envelope)
            } catch (t: Throwable) {
                close(t)
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                while (isActive && !socket.isClosed) onEnvelope(peerId, codec.read(socket.getInputStream()))
            } catch (t: Throwable) {
                close(t)
            }
        }
    }

    suspend fun send(envelope: Envelope) {
        if (closed.get()) return
        val delivered = try {
            withTimeoutOrNull(SEND_TIMEOUT_MS) {
                outgoing.send(envelope)
                true
            } ?: false
        } catch (_: ClosedSendChannelException) {
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            close(error)
            false
        }
        if (!delivered && !closed.get()) {
            close(IllegalStateException("Control send timed out"))
        }
    }

    fun trySend(envelope: Envelope): Boolean = !closed.get() && outgoing.trySend(envelope).isSuccess

    fun close(cause: Throwable? = null) {
        if (!closed.compareAndSet(false, true)) return
        outgoing.close()
        runCatching { socket.close() }
        scope.cancel()
        log.i(TAG, "Control connection closed peer=${peerId.value.take(8)} cause=${cause?.javaClass?.simpleName}")
        callbackScope.launch(Dispatchers.Default) { onClosed(this@ControlConnection, cause) }
    }

    companion object {
        private const val TAG = "ControlConnection"
        private const val OUTGOING_CAPACITY = 256
        private const val SEND_TIMEOUT_MS = 2_000L
    }
}
