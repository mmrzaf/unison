package com.darius.unison.harness

import com.darius.unison.model.HotspotInfo
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.network.NsdDiscoveryEvent
import com.darius.unison.network.RoutedSocket
import com.darius.unison.room.port.HotspotPort
import com.darius.unison.room.port.IdentityStore
import com.darius.unison.room.port.LocalNetworkPort
import com.darius.unison.room.port.PowerDemand
import com.darius.unison.room.port.PowerPort
import com.darius.unison.room.port.RoomDiscoveryPort
import com.darius.unison.room.port.TrackStore
import com.darius.unison.storage.ManagedFileLeaseReason
import com.darius.unison.storage.ManagedFileStore
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class SimIdentity(private var identity: LocalIdentity) : IdentityStore {
    override val retentionPolicy: Flow<RetentionPolicy> =
        MutableStateFlow(RetentionPolicy.KEEP_IN_LIBRARY)

    override suspend fun ensureIdentity(): LocalIdentity = identity

    override suspend fun rotateIdentity(): LocalIdentity {
        identity = identity.copy(peerId = com.darius.unison.model.PeerId(randomPeerId()))
        return identity
    }

    override suspend fun saveDisplayName(name: String) {
        identity = identity.copy(displayName = name)
    }

    private fun randomPeerId(): String =
        (1..16).joinToString("") { "%02x".format((0..255).random()) }
}

/** In-memory library. Readable tracks are real files so playback paths stay realistic. */
class SimTrackStore : TrackStore {
    private val descriptors = ConcurrentHashMap<TrackId, TrackDescriptor>()
    private val files = ConcurrentHashMap<TrackId, File>()

    fun publish(descriptor: TrackDescriptor, file: File) {
        descriptors[descriptor.trackId] = descriptor
        files[descriptor.trackId] = file
    }

    fun fileOf(trackId: TrackId): File? = files[trackId]?.takeIf { it.isFile }

    fun has(trackId: TrackId): Boolean = fileOf(trackId) != null

    override suspend fun getMany(trackIds: List<TrackId>): List<TrackDescriptor> =
        trackIds.mapNotNull {
            descriptors[it]
        }

    override suspend fun requireReadableFile(trackId: TrackId): File? =
        files[trackId]?.takeIf { it.isFile }

    override suspend fun requireReadableFileWithLease(
        trackId: TrackId,
        reason: ManagedFileLeaseReason,
    ): ManagedFileStore.LeasedManagedFile? = null

    override suspend fun registerManagedFile(
        descriptor: TrackDescriptor,
        retentionPolicy: RetentionPolicy,
    ) {
        descriptors[descriptor.trackId] = descriptor
    }

    override suspend fun hasVerifiedSource(trackId: TrackId): Boolean = files.containsKey(trackId)

    override suspend fun touchTemporary(trackId: TrackId) = Unit

    override suspend fun markPlayed(trackId: TrackId) = Unit
}

/** Advertisement registry shared by every simulated device on the same "LAN". */
class SimDiscoveryHub {
    private val rooms =
        MutableStateFlow<Map<String, com.darius.unison.model.DiscoveredRoom>>(emptyMap())

    fun advertise(room: com.darius.unison.model.DiscoveredRoom) {
        rooms.value = rooms.value + (room.roomId to room)
    }

    fun stopAdvertising(roomId: String) {
        rooms.value = rooms.value - roomId
    }

    fun current(): List<com.darius.unison.model.DiscoveredRoom> = rooms.value.values.toList()

    fun updates(): Flow<List<com.darius.unison.model.DiscoveredRoom>> = rooms.map {
        it.values.toList()
    }
}

class SimDiscovery(private val hub: SimDiscoveryHub) : RoomDiscoveryPort {
    private var advertisedRoomId: String? = null

    override fun discover(): Flow<NsdDiscoveryEvent> = flow {
        hub.updates().collect { rooms ->
            rooms.forEach { emit(NsdDiscoveryEvent.Found(it)) }
        }
    }

    override fun advertise(
        roomId: String,
        roomName: String,
        port: Int,
        term: Long,
        onRegistered: (String) -> Unit,
        onError: (String) -> Unit,
    ) {
        advertisedRoomId = roomId
        hub.advertise(
            com.darius.unison.model.DiscoveredRoom(
                serviceName = "sim-$roomId",
                roomId = roomId,
                roomName = roomName,
                hostAddress = "192.168.49.1",
                port = port,
                protocolVersion = 1,
                term = term,
            )
        )
        onRegistered(roomId)
    }

    override fun stopAdvertising() {
        advertisedRoomId?.let(hub::stopAdvertising)
        advertisedRoomId = null
    }

    override fun stopDiscovery() = Unit
}

class SimHotspot : HotspotPort {
    private val mutable = MutableStateFlow<HotspotInfo?>(null)
    override val state: StateFlow<HotspotInfo?> = mutable.asStateFlow()

    override fun start(onError: (String) -> Unit) {
        mutable.value = HotspotInfo(ssid = "unison-sim", passphrase = "simulated", securityType = 1)
    }

    override fun stop() {
        mutable.value = null
    }
}

class SimPower : PowerPort {
    var lastDemand: PowerDemand = PowerDemand.NONE
        private set

    override fun apply(demand: PowerDemand) {
        lastDemand = demand
    }

    override fun close() = Unit
}

/** The phone's Wi-Fi address; set it to null to model losing the local network. */
class SimLocalNetwork(@Volatile var address: InetAddress?) : LocalNetworkPort {
    override fun preferredLocalAddress(preferHotspot: Boolean): InetAddress? = address

    override fun resetSession() = Unit

    override fun createSocket(remoteAddress: InetAddress, purpose: String): RoutedSocket =
        error("Simulated transfers are not part of this harness yet")

    override fun onConnected(route: RoutedSocket, socket: Socket) = Unit

    override fun observeInboundSocket(socket: Socket) = Unit
}
