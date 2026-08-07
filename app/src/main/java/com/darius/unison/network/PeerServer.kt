package com.darius.unison.network

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.FrameCodec
import com.darius.unison.protocol.HandshakeCodec
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.HandshakeRejectionCode
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.ProtocolException
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull

class PeerServer(
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val handler: Handler,
    private val networkRouter: LocalNetworkSocketProvider,
) {
    interface Handler {
        suspend fun admitControl(
            hello: HandshakeMessage.ControlHello,
            remoteAddress: String,
        ): ControlAdmission

        suspend fun onControlConnected(connection: ControlConnection)

        suspend fun onFileConnection(socket: Socket, hello: HandshakeMessage.FileClientHello)
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

        data class PinChallenge(
            val response: HandshakeMessage.PinChallenge,
            val complete: suspend (HandshakeMessage.PinResponse) -> ControlAdmission,
        ) : ControlAdmission

        data class ReconnectChallenge(
            val response: HandshakeMessage.ReconnectChallenge,
            val complete: suspend (HandshakeMessage.ReconnectResponse) -> ControlAdmission,
        ) : ControlAdmission

        data class Rejected(val reason: String, val code: HandshakeRejectionCode) : ControlAdmission
    }

    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val incomingSlots = Semaphore(MAX_CONCURRENT_INCOMING)
    val port: Int
        get() = serverSocket?.localPort ?: 0

    @Synchronized
    fun start(): Int {
        if (serverSocket != null) return port
        val server =
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(0), 16)
            }
        serverSocket = server
        acceptJob =
            scope.launch(Dispatchers.IO) {
                while (isActive && !server.isClosed) {
                    val socket =
                        try {
                            server.accept()
                        } catch (error: Exception) {
                            if (!server.isClosed) log.warn(
                                TAG, DiagnosticCategory.NETWORK, "network.peer_server.accept_failed",
                                throwable = error,
                            )
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
        log.info(
            TAG, DiagnosticCategory.NETWORK, "network.peer_server.started",
            attributes = mapOf("network.listen_port" to server.localPort),
        )
        return server.localPort
    }

    private suspend fun handle(socket: Socket) {
        networkRouter.observeInboundSocket(socket)
        socket.tcpNoDelay = true
        socket.keepAlive = true
        socket.soTimeout = 15_000
        val remote = socket.inetAddress
        if (!NetworkAddressPolicy.isAllowed(remote)) {
            socket.close()
            return
        }
        try {
            when (val message = HandshakeCodec.read(socket.getInputStream())) {
                is HandshakeMessage.FileClientHello -> {
                    if (rejectProtocolMismatch(socket, message.protocolVersion)) return
                    handler.onFileConnection(socket, message)
                }

                is HandshakeMessage.ControlHello -> {
                    if (rejectProtocolMismatch(socket, message.protocolVersion)) return
                    val initial = handler.admitControl(message, remote.hostAddress ?: "")
                    val admission =
                        when (initial) {
                            is ControlAdmission.PinChallenge -> {
                                HandshakeCodec.write(socket.getOutputStream(), initial.response)
                                val response =
                                    HandshakeCodec.read(socket.getInputStream())
                                        as? HandshakeMessage.PinResponse
                                        ?: throw ProtocolException("Expected PIN response")
                                initial.complete(response)
                            }

                            is ControlAdmission.ReconnectChallenge -> {
                                HandshakeCodec.write(socket.getOutputStream(), initial.response)
                                val response =
                                    HandshakeCodec.read(socket.getInputStream())
                                        as? HandshakeMessage.ReconnectResponse
                                        ?: throw ProtocolException("Expected reconnect response")
                                initial.complete(response)
                            }

                            else -> initial
                        }
                    finishControlAdmission(socket, message, admission)
                }

                else -> throw ProtocolException("Expected client hello")
            }
        } catch (cancelled: CancellationException) {
            runCatching { socket.close() }
            throw cancelled
        } catch (error: Exception) {
            log.warn(
                TAG, DiagnosticCategory.NETWORK, "network.peer_server.connection_failed",
                throwable = error,
            )
            runCatching { socket.close() }
        }
    }

    private fun rejectProtocolMismatch(socket: Socket, version: Int): Boolean {
        if (version == PROTOCOL_VERSION) return false
        HandshakeCodec.write(
            socket.getOutputStream(),
            HandshakeMessage.Rejected(
                reason = "App versions are incompatible",
                code = HandshakeRejectionCode.PROTOCOL_MISMATCH,
            ),
        )
        socket.close()
        return true
    }

    private suspend fun finishControlAdmission(
        socket: Socket,
        hello: HandshakeMessage.ControlHello,
        admission: ControlAdmission,
    ) {
        when (admission) {
            is ControlAdmission.Rejected -> {
                HandshakeCodec.write(
                    socket.getOutputStream(),
                    HandshakeMessage.Rejected(admission.reason, admission.code),
                )
                socket.close()
            }

            is ControlAdmission.PinChallenge -> throw ProtocolException("Nested PIN challenge")
            is ControlAdmission.ReconnectChallenge ->
                throw ProtocolException("Nested reconnect challenge")

            is ControlAdmission.Accepted ->
                try {
                    HandshakeCodec.write(socket.getOutputStream(), admission.response)
                    socket.soTimeout = 0
                    val codec =
                        FrameCodec(
                            writeKey = admission.serverWriteKey,
                            readKey = admission.serverReadKey,
                            expectedRoomId = admission.roomId,
                        )
                    val connection =
                        try {
                            ControlConnection(
                                peerId = hello.peerId,
                                endpoint = admission.endpoint,
                                socket = socket,
                                codec = codec,
                                parentScope = scope,
                                log = log,
                                onEnvelope = admission.onEnvelope,
                                onClosed = admission.onClosed,
                            )
                        } catch (error: Exception) {
                            codec.close()
                            throw error
                        }
                    try {
                        handler.onControlConnected(connection)
                        connection.start()
                    } catch (error: Exception) {
                        connection.closeSilently()
                        throw error
                    }
                } finally {
                    admission.serverWriteKey.fill(0)
                    admission.serverReadKey.fill(0)
                }
        }
    }

    private fun detachForStop(): Job? =
        synchronized(this) {
            val job = acceptJob
            acceptJob = null
            runCatching { serverSocket?.close() }
            serverSocket = null
            job
        }

    fun stop() {
        detachForStop()?.cancel()
    }

    suspend fun stopAndJoin(timeoutMs: Long = STOP_TIMEOUT_MS): Boolean {
        val job = detachForStop() ?: return true
        job.cancel()
        return withTimeoutOrNull(timeoutMs) {
            job.join()
            true
        }
            ?: run {
                job.cancelAndJoin()
                false
            }
    }

    companion object {
        private const val TAG = "PeerServer"
        private const val MAX_CONCURRENT_INCOMING = 24
        private const val STOP_TIMEOUT_MS = 2_000L
    }
}
