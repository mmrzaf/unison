package com.darius.unison.harness

import com.darius.unison.app.RoomStore
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.room.RoomRuntime
import com.darius.unison.room.port.DeviceInfo
import com.darius.unison.room.port.RoomPorts
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import java.io.File
import java.net.InetAddress
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestCoroutineScheduler

/** Monotonic clock driven by the test scheduler, so room time and virtual time are the same. */
class SimClock(private val scheduler: TestCoroutineScheduler) : MonotonicClock {
    override fun nowNs(): Long = BASE_NS + scheduler.currentTime * 1_000_000L

    private companion object {
        /** Wire envelopes must carry a strictly positive monotonic timestamp. */
        const val BASE_NS = 1_000_000_000L
    }
}

/** A phone's own monotonic clock: unrelated offset and a constant frequency error in ppm. */
class SkewedClock(
    private val base: MonotonicClock,
    private val offsetNs: Long,
    private val driftPpm: Int,
) : MonotonicClock {
    override fun nowNs(): Long {
        val elapsed = base.nowNs()
        return offsetNs + elapsed + elapsed / 1_000_000L * driftPpm
    }
}

class SimDevice(
    val index: Int,
    val address: String,
    val identity: LocalIdentity,
    val store: RoomStore,
    val log: DiagnosticLog,
    val network: SimPeerNetwork,
    val player: SimulatedPlayer,
    val tracks: SimTrackStore,
    val runtime: RoomRuntime,
    val localNetwork: SimLocalNetwork,
    private val directory: File,
) {
    val peerId: PeerId
        get() = identity.peerId

    /** Adds a real file of [sizeBytes] to this phone's library and returns its descriptor. */
    fun addLocalTrack(
        name: String,
        sizeBytes: Int = 64 * 1024,
        durationMs: Long = 180_000L,
    ): TrackDescriptor {
        val bytes = ByteArray(sizeBytes) { ((it * 31 + name.hashCode()) and 0xff).toByte() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val trackId = TrackId(digest.joinToString("") { "%02x".format(it) })
        val file = File(directory, "library/$name.audio").apply { parentFile?.mkdirs() }
        file.writeBytes(bytes)
        val descriptor =
            TrackDescriptor(
                trackId = trackId,
                sizeBytes = sizeBytes.toLong(),
                mimeType = "audio/mpeg",
                durationMs = durationMs,
                title = name,
                originalFileName = "$name.audio",
            )
        tracks.publish(descriptor, file)
        return descriptor
    }

    fun roomId(): String? = store.structure.value.snapshot?.roomId

    fun roomPin(): String? = store.structure.value.localRoomPin

    fun memberCount(): Int = store.structure.value.snapshot?.members?.size ?: 0

    fun discoveredRoom(): DiscoveredRoom? {
        val roomId = roomId() ?: return null
        return DiscoveredRoom(
            serviceName = "sim-$roomId",
            roomId = roomId,
            roomName = store.structure.value.snapshot?.roomName ?: roomId,
            hostAddress = address,
            port = network.port,
            protocolVersion = 1,
            term = store.structure.value.snapshot?.term?.number ?: 1L,
        )
    }
}

/**
 * A set of simulated phones on one virtual LAN, each running the real [RoomRuntime] on the test
 * scheduler. Only the host is simulated: clock, files, player, network transport, discovery.
 */
class RoomHarness(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val clock: MonotonicClock,
    private val root: File,
) {
    val hub = SimNetworkHub(dispatcher)
    val discovery = SimDiscoveryHub()
    val transfers = SimTransferHub(scope, dispatcher)
    val devices = mutableListOf<SimDevice>()

    fun device(
        displayName: String,
        clockOffsetNs: Long = 0L,
        clockDriftPpm: Int = 0,
    ): SimDevice {
        val clock = SkewedClock(this.clock, clockOffsetNs, clockDriftPpm)
        val index = devices.size + 1
        val address = "192.168.49.${index + 1}"
        val directory = File(root, "device-$index").apply { mkdirs() }
        val log = DiagnosticLog(File(directory, "diagnostics.ndjson"), clock)
        val identity = LocalIdentity(PeerId(peerIdFor(index)), displayName)
        val store = RoomStore()
        val tracks = SimTrackStore()
        val network =
            SimPeerNetwork(
                hub = hub,
                scope = scope,
                dispatcher = dispatcher,
                clock = clock,
                log = log,
                localAddress = address,
            )
        val localNetwork = SimLocalNetwork(InetAddress.getByName(address))
        val ports =
            RoomPorts(
                clock = clock,
                ioDispatcher = dispatcher,
                device = DeviceInfo(model = "sim", sdkInt = 33, appVersion = "1.2.0"),
                log = log,
                roomStore = store,
                identity = SimIdentity(identity),
                tracks = tracks,
                fileStore = ManagedFileStore(File(directory, "files")),
                network = network,
                transfers = transfers.factory(address, tracks, directory),
                localNetwork = localNetwork,
                discovery = SimDiscovery(discovery),
                hotspot = SimHotspot(),
                power = SimPower(),
            )
        val player = SimulatedPlayer(clock)
        val runtime = RoomRuntime(ports, player, scope)
        return SimDevice(
                index = index,
                address = address,
                identity = identity,
                store = store,
                log = log,
                network = network,
                player = player,
                tracks = tracks,
                runtime = runtime,
                localNetwork = localNetwork,
                directory = directory,
            )
            .also { devices += it }
    }

    fun close() {
        devices.forEach { runCatching { it.runtime.close() } }
        hub.partitionAll()
    }

    private fun peerIdFor(index: Int): String = "sim-peer-%016d".format(index)
}
