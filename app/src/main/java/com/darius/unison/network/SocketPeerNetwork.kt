package com.darius.unison.network

import com.darius.unison.model.LocalIdentity
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.FrameCodec
import com.darius.unison.protocol.HandshakeCodec
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.HandshakeRejectionCode
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.ProtocolException
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.io.EOFException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Socket transport for [PeerNetwork]. All blocking socket work runs on [dispatcher]. */
class SocketPeerNetwork(
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val handshake: ControlHandshake,
    private val dispatcher: CoroutineDispatcher,
    private val socketProvider: LocalNetworkSocketProvider,
) : PeerNetwork {
    private val serverLock = Any()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null
    private val incomingSlots = Semaphore(MAX_CONCURRENT_INCOMING)

    override val port: Int
        get() = synchronized(serverLock) { serverSocket?.localPort ?: 0 }

    override fun listen(handler: PeerNetwork.Handler): Int {
        synchronized(serverLock) {
            serverSocket?.let {
                return it.localPort
            }
            val server =
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(0), 16)
                }
            serverSocket = server
            acceptJob =
                scope.launch(dispatcher) {
                    while (isActive && !server.isClosed) {
                        val socket =
                            try {
                                server.accept()
                            } catch (error: Exception) {
                                if (!server.isClosed)
                                    log.warn(
                                        TAG,
                                        DiagnosticCategory.NETWORK,
                                        "network.peer_server.accept_failed",
                                        throwable = error,
                                    )
                                break
                            }
                        if (!incomingSlots.tryAcquire()) {
                            runCatching { socket.close() }
                            continue
                        }
                        launch(dispatcher) {
                            try {
                                handleInbound(socket, handler)
                            } finally {
                                incomingSlots.release()
                            }
                        }
                    }
                }
            log.info(
                TAG,
                DiagnosticCategory.NETWORK,
                "network.peer_server.started",
                attributes = mapOf("network.listen_port" to server.localPort),
            )
            return server.localPort
        }
    }

    private suspend fun handleInbound(socket: Socket, handler: PeerNetwork.Handler) {
        socketProvider.observeInboundSocket(socket)
        withContext(dispatcher) {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = HANDSHAKE_TIMEOUT_MS
        }
        val remote = socket.inetAddress
        if (!NetworkAddressPolicy.isAllowed(remote)) {
            runCatching { socket.close() }
            return
        }
        val channel = SocketHandshakeChannel(socket, remote.hostAddress ?: "", dispatcher)
        try {
            when (val message = channel.read()) {
                is HandshakeMessage.FileClientHello -> {
                    if (rejectProtocolMismatch(channel, socket, message.protocolVersion)) return
                    handler.onFileConnection(socket, message)
                }

                is HandshakeMessage.ControlHello -> {
                    if (rejectProtocolMismatch(channel, socket, message.protocolVersion)) return
                    handshake.accept(handler, message, channel)
                }

                else -> throw ProtocolException("Expected client hello")
            }
        } catch (cancelled: CancellationException) {
            channel.close()
            throw cancelled
        } catch (error: Exception) {
            log.warn(
                TAG,
                DiagnosticCategory.NETWORK,
                "network.peer_server.connection_failed",
                throwable = error,
            )
            channel.close()
        }
    }

    private suspend fun rejectProtocolMismatch(
        channel: HandshakeChannel,
        socket: Socket,
        version: Int,
    ): Boolean {
        if (version == PROTOCOL_VERSION) return false
        channel.write(
            HandshakeMessage.Rejected(
                reason = "App versions are incompatible",
                code = HandshakeRejectionCode.PROTOCOL_MISMATCH,
            )
        )
        runCatching { socket.close() }
        return true
    }

    override suspend fun connectWithPin(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        pin: String,
        appVersion: String,
        onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl =
        connect(
            identity = identity,
            roomId = roomId,
            host = host,
            port = port,
            listeningPort = listeningPort,
            credential = Credential.Pin(pin),
            appVersion = appVersion,
            onEnvelope = onEnvelope,
            onClosed = onClosed,
        )

    override suspend fun reconnectWithRoomSecret(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        roomSecret: ByteArray,
        appVersion: String,
        onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl =
        connect(
            identity = identity,
            roomId = roomId,
            host = host,
            port = port,
            listeningPort = listeningPort,
            credential = Credential.RoomSecret(roomSecret.copyOf()),
            appVersion = appVersion,
            onEnvelope = onEnvelope,
            onClosed = onClosed,
        )

    private suspend fun connect(
        identity: LocalIdentity,
        roomId: String,
        host: String,
        port: Int,
        listeningPort: Int,
        credential: Credential,
        appVersion: String,
        onEnvelope: suspend (ControlConnection, Envelope) -> Unit,
        onClosed: suspend (ControlConnection, Throwable?) -> Unit,
    ): ConnectedControl {
        val address =
            NetworkAddressPolicy.parseAllowedAddress(host)
                ?: throw IllegalArgumentException("Invalid local room endpoint")
        val route = withContext(dispatcher) { socketProvider.createSocket(address, "control") }
        val socket = route.socket
        try {
            log.debug(
                TAG,
                DiagnosticCategory.NETWORK,
                "network.control.connecting",
                attributes = route.diagnosticAttributes() + mapOf("network.remote_port" to port),
            )
            withContext(dispatcher) {
                socket.tcpNoDelay = true
                socket.keepAlive = true
                socket.connect(InetSocketAddress(address, port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = HANDSHAKE_TIMEOUT_MS
            }
            socketProvider.onConnected(route, socket)
            log.debug(
                TAG,
                DiagnosticCategory.NETWORK,
                "network.control.connected",
                attributes = route.diagnosticAttributes() + mapOf("network.remote_port" to port),
            )
            return handshake.connect(
                channel =
                    SocketHandshakeChannel(
                        socket,
                        socket.inetAddress?.hostAddress ?: host,
                        dispatcher,
                    ),
                identity = identity,
                roomId = roomId,
                host = host,
                port = port,
                listeningPort = listeningPort,
                credential = credential,
                appVersion = appVersion,
                onEnvelope = onEnvelope,
                onClosed = onClosed,
            )
        } catch (error: Exception) {
            runCatching { socket.close() }
            throw error
        } finally {
            (credential as? Credential.RoomSecret)?.value?.fill(0)
        }
    }

    private fun detachForStop(): Job? =
        synchronized(serverLock) {
            val job = acceptJob
            acceptJob = null
            runCatching { serverSocket?.close() }
            serverSocket = null
            job
        }

    override fun stop() {
        detachForStop()?.cancel()
    }

    override suspend fun stopAndJoin(timeoutMs: Long): Boolean {
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

    private companion object {
        const val TAG = "SocketPeerNetwork"
        const val MAX_CONCURRENT_INCOMING = 24
        const val CONNECT_TIMEOUT_MS = 4_500
        const val HANDSHAKE_TIMEOUT_MS = 10_000
    }
}

private class SocketHandshakeChannel(
    private val socket: Socket,
    override val remoteHostAddress: String,
    private val dispatcher: CoroutineDispatcher,
) : HandshakeChannel {
    override suspend fun read(): HandshakeMessage =
        withContext(dispatcher) { HandshakeCodec.read(socket.getInputStream()) }

    override suspend fun write(message: HandshakeMessage) =
        withContext(dispatcher) { HandshakeCodec.write(socket.getOutputStream(), message) }

    override fun upgrade(
        writeKey: ByteArray,
        readKey: ByteArray,
        expectedRoomId: String,
    ): EnvelopeChannel {
        val codec =
            FrameCodec(writeKey = writeKey, readKey = readKey, expectedRoomId = expectedRoomId)
        runCatching { socket.soTimeout = 0 }
        return SocketEnvelopeChannel(socket, codec, remoteHostAddress, dispatcher)
    }

    override fun close() {
        runCatching { socket.close() }
    }
}

private class SocketEnvelopeChannel(
    private val socket: Socket,
    private val codec: FrameCodec,
    override val remoteHostAddress: String,
    private val dispatcher: CoroutineDispatcher,
) : EnvelopeChannel {
    override suspend fun receive(): Envelope? =
        try {
            withContext(dispatcher) { codec.read(socket.getInputStream()) }
        } catch (_: EOFException) {
            null
        }

    override suspend fun send(envelope: Envelope) {
        withContext(dispatcher) { codec.write(socket.getOutputStream(), envelope) }
    }

    override fun close(cause: Throwable?) {
        runCatching { socket.close() }
        codec.close()
    }
}
