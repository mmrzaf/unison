package com.darius.unison.room.port

import com.darius.unison.app.RoomStore
import com.darius.unison.network.PeerNetwork
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.util.DiagnosticLog
import com.darius.unison.util.MonotonicClock
import kotlinx.coroutines.CoroutineDispatcher

/**
 * Everything a room runtime needs from its host. The Android shell supplies platform
 * implementations; JVM tests supply simulated ones. Nothing behind these ports is room state.
 */
class RoomPorts(
    val clock: MonotonicClock,
    val ioDispatcher: CoroutineDispatcher,
    val device: DeviceInfo,
    val log: DiagnosticLog,
    val roomStore: RoomStore,
    val identity: IdentityStore,
    val tracks: TrackStore,
    val fileStore: ManagedFileStore,
    val network: PeerNetwork,
    val transfers: TransferPortFactory,
    val localNetwork: LocalNetworkPort,
    val discovery: RoomDiscoveryPort,
    val hotspot: HotspotPort,
    val power: PowerPort,
)

data class DeviceInfo(
    val model: String,
    val sdkInt: Int,
    val appVersion: String,
)
