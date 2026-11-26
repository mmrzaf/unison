package com.darius.unison.harness

import com.darius.unison.model.LocalIdentity
import com.darius.unison.network.ConnectedControl
import com.darius.unison.network.ControlConnection
import com.darius.unison.network.ControlHandshake
import com.darius.unison.network.Credential
import com.darius.unison.network.EnvelopeChannel
import com.darius.unison.network.HandshakeChannel
import com.darius.unison.network.PeerNetwork
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.ProtocolJson
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-memory peer transport shared by the simulated devices. Each device registers the port it
 * listens on; a connection pairs two [SimLink] ends and runs the real handshake on both sides.
 */
class SimNetworkHub(dispatcher: CoroutineDispatcher) {
    private val nextPort = AtomicInteger(4301)
    private val listeners = ConcurrentHashMap<Int, Listener>()
    private val activeLinks = mutableListOf<SimLink>()
    val dispatcher: CoroutineDispatcher = dispatcher

    data class Listener(
        val scope: CoroutineScope,
        val accept: suspend (HandshakeChannel, String) -> Unit,
    )

    fun allocatePort(): Int = nextPort.getAndIncrement()

    fun register(port: Int, listener: Listener) {
        listeners[port] = listener
    }

    fun unregister(port: Int) {
        listeners.remove(port)
    }

    fun listener(port: Int): Listener? = listeners[port]

    /** Drops every live control link, simulating the loss of the room network. */
    fun partitionAll(cause: Throwable = IOException("simulated partition")) {
        synchronized(activeLinks) {
            activeLinks.toList().forEach { it.close(cause) }
            activeLinks.clear()
        }
    }

    /**
     * While set, links stay open but deliver nothing: a half-open connection. Frames are held, not
     * lost, because a real TCP stream either delivers in order or fails the connection.
     */
    @Volatile var stalled = false

    fun liveLinkCount(): Int = synchronized(activeLinks) { activeLinks.count { !it.closed } }

    internal fun track(link: SimLink) {
        synchronized(activeLinks) { activeLinks += link }
    }
}

/** One paired, in-memory control link. Messages are JSON round-tripped like the real wire. */
class SimLink internal constructor(private val hub: SimNetworkHub) {
    private val clientToServerHandshake = Channel<HandshakeMessage>(Channel.UNLIMITED)
    private val serverToClientHandshake = Channel<HandshakeMessage>(Channel.UNLIMITED)
    private val clientToServerEnvelope = Channel<Envelope>(CAPACITY)
    private val serverToClientEnvelope = Channel<Envelope>(CAPACITY)
    private val closedFlag = java.util.concurrent.atomic.AtomicBoolean(false)

    val closed: Boolean
        get() = closedFlag.get()

    fun clientHandshake(remoteAddress: String): HandshakeChannel =
        SimHandshakeChannel(this, isClient = true, remoteAddress)

    fun serverHandshake(remoteAddress: String): HandshakeChannel =
        SimHandshakeChannel(this, isClient = false, remoteAddress)

    internal suspend fun writeHandshake(isClient: Boolean, message: HandshakeMessage) {
        val wire = roundTripHandshake(message)
        if (isClient) clientToServerHandshake.send(wire) else serverToClientHandshake.send(wire)
    }

    internal suspend fun readHandshake(isClient: Boolean): HandshakeMessage =
        (if (isClient) serverToClientHandshake else clientToServerHandshake).receive()

    internal suspend fun writeEnvelope(isClient: Boolean, envelope: Envelope) {
        while (hub.stalled) delay(STALL_POLL_MS)
        val wire = ProtocolJson.decodeFromString<Envelope>(ProtocolJson.encodeToString(envelope))
        if (isClient) clientToServerEnvelope.send(wire) else serverToClientEnvelope.send(wire)
    }

    internal suspend fun readEnvelope(isClient: Boolean): Envelope? =
        (if (isClient) serverToClientEnvelope else clientToServerEnvelope)
            .receiveCatching()
            .getOrNull()

    fun close(cause: Throwable? = null) {
        if (!closedFlag.compareAndSet(false, true)) return
        clientToServerHandshake.close(cause)
        serverToClientHandshake.close(cause)
        clientToServerEnvelope.close(cause)
        serverToClientEnvelope.close(cause)
    }

    private fun roundTripHandshake(message: HandshakeMessage): HandshakeMessage =
        ProtocolJson.decodeFromString(ProtocolJson.encodeToString(message))

    private companion object {
        const val CAPACITY = 64
        const val STALL_POLL_MS = 50L
    }
}

private class SimHandshakeChannel(
    private val link: SimLink,
    private val isClient: Boolean,
    override val remoteHostAddress: String,
) : HandshakeChannel {

    override suspend fun read(): HandshakeMessage = link.readHandshake(isClient)

    override suspend fun write(message: HandshakeMessage) = link.writeHandshake(isClient, message)

    override fun upgrade(
        writeKey: ByteArray,
        readKey: ByteArray,
        expectedRoomId: String,
    ): EnvelopeChannel = SimEnvelopeChannel(link, isClient, remoteHostAddress)

    override fun close() = link.close()
}

private class SimEnvelopeChannel(
    private val link: SimLink,
    private val isClient: Boolean,
    override val remoteHostAddress: String,
) : EnvelopeChannel {

    override suspend fun receive(): Envelope? = link.readEnvelope(isClient)

    override suspend fun send(envelope: Envelope) = link.writeEnvelope(isClient, envelope)

    override fun close(cause: Throwable?) = link.close(cause)
}

/**
 * [PeerNetwork] over [SimNetworkHub]. The handshake, admission, routing, and connection lifetime
 * are the production ones; only the transport is simulated.
 */
class SimPeerNetwork(
    private val hub: SimNetworkHub,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val clock: MonotonicClock,
    private val log: DiagnosticLog,
    val localAddress: String,
) : PeerNetwork {
    private val handshake = ControlHandshake(scope, dispatcher, clock, log)
    private var listenPort: Int = 0

    override val port: Int
        get() = listenPort

    override fun listen(handler: PeerNetwork.Handler): Int {
        if (listenPort != 0) return listenPort
        val port = hub.allocatePort()
        listenPort = port
        hub.register(
            port,
            SimNetworkHub.Listener(scope) { channel, _ ->
                val hello =
                    channel.read() as? HandshakeMessage.ControlHello
                        ?: throw IOException("expected a control hello")
                handshake.accept(handler, hello, channel)
            },
        )
        return port
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
        val listener = hub.listener(port) ?: throw IOException("No simulated peer on port $port")
        val link = SimLink(hub)
        hub.track(link)
        listener.scope.launch(dispatcher) {
            runCatching { listener.accept(link.serverHandshake(localAddress), localAddress) }
        }
        return handshake.connect(
            channel = link.clientHandshake(host),
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
    }

    override fun stop() {
        if (listenPort != 0) {
            hub.unregister(listenPort)
            listenPort = 0
        }
    }

    override suspend fun stopAndJoin(timeoutMs: Long): Boolean {
        stop()
        return true
    }
}
