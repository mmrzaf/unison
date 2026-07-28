package com.darius.unison.network

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.protocol.ChannelType
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.FrameCodec
import com.darius.unison.protocol.HandshakeCodec
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.ProtocolException
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

class PeerServer(
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val handler: Handler,
) {
    interface Handler {
        suspend fun admitControl(hello: HandshakeMessage.ClientHello, remoteAddress: String): ControlAdmission
        suspend fun onControlConnected(connection: ControlConnection)
        suspend fun onFileConnection(socket: Socket, hello: HandshakeMessage.ClientHello)
    }

    sealed interface ControlAdmission {
        data class Accepted(
            val response: HandshakeMessage.CoordinatorHello,
            val serverWriteKey: ByteArray,
            val serverReadKey: ByteArray,
            val endpoint: PeerEndpoint,
            val roomId: String,
            val onEnvelope: suspend (com.darius.unison.model.PeerId, Envelope) -> Unit,
            val onClosed: suspend (ControlConnection, Throwable?) -> Unit,
        ) : ControlAdmission

        data class Rejected(val reason: String) : ControlAdmission
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val incomingSlots = Semaphore(MAX_CONCURRENT_INCOMING)
    val port: Int get() = serverSocket?.localPort ?: 0

    @Synchronized
    fun start(): Int {
        if (serverSocket != null) return port
        val server = ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(0), 16)
        }
        serverSocket = server
        acceptJob = scope.launch(Dispatchers.IO) {
            while (isActive && !server.isClosed) {
                val socket = try {
                    server.accept()
                } catch (error: Exception) {
                    if (!server.isClosed) log.w(TAG, "Accept failed", error)
                    break
                }
                if (!incomingSlots.tryAcquire()) {
                    runCatching { socket.close() }
                    continue
                }
                launch {
                    try {
                        handle(socket)
                    } finally {
                        incomingSlots.release()
                    }
                }
            }
        }
        log.i(TAG, "Peer server listening port=${server.localPort}")
        return server.localPort
    }

    private suspend fun handle(socket: Socket) {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = 15_000
        val remote = socket.inetAddress
        if (!NetworkAddressPolicy.isAllowed(remote)) {
            socket.close()
            return
        }
        try {
            val message = HandshakeCodec.read(socket.getInputStream())
            val hello = message as? HandshakeMessage.ClientHello ?: throw ProtocolException("Expected client hello")
            when (hello.channel) {
                ChannelType.FILE -> handler.onFileConnection(socket, hello)
                ChannelType.CONTROL -> when (val admission = handler.admitControl(hello, remote.hostAddress ?: "")) {
                    is ControlAdmission.Rejected -> {
                        HandshakeCodec.write(socket.getOutputStream(), HandshakeMessage.Rejected(admission.reason))
                        socket.close()
                    }

                    is ControlAdmission.Accepted -> {
                        HandshakeCodec.write(socket.getOutputStream(), admission.response)
                        socket.soTimeout = 0
                        val connection = ControlConnection(
                            peerId = hello.peerId,
                            endpoint = admission.endpoint,
                            socket = socket,
                            codec = FrameCodec(
                                writeKey = admission.serverWriteKey,
                                readKey = admission.serverReadKey,
                                expectedRoomId = admission.roomId,
                            ),
                            parentScope = scope,
                            log = log,
                            onEnvelope = admission.onEnvelope,
                            onClosed = admission.onClosed,
                        )
                        handler.onControlConnected(connection)
                        connection.start()
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            runCatching { socket.close() }
            throw cancelled
        } catch (error: Exception) {
            log.w(TAG, "Incoming connection failed", error)
            runCatching { socket.close() }
        }
    }

    @Synchronized
    fun stop() {
        acceptJob?.cancel()
        acceptJob = null
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    companion object {
        private const val TAG = "PeerServer"
        private const val MAX_CONCURRENT_INCOMING = 24
    }
}
