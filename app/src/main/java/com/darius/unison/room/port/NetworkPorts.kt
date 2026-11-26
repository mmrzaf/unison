package com.darius.unison.room.port

import com.darius.unison.model.HotspotInfo
import com.darius.unison.network.LocalNetworkSocketProvider
import com.darius.unison.network.NsdDiscoveryEvent
import java.net.InetAddress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The local network the room runs on, and route-correct sockets onto it. */
interface LocalNetworkPort : LocalNetworkSocketProvider {
    fun preferredLocalAddress(preferHotspot: Boolean): InetAddress?

    /** Forgets session-scoped route ownership when a room session ends. */
    fun resetSession()
}

/** Nearby room advertisement and bounded discovery scans. */
interface RoomDiscoveryPort {
    fun discover(): Flow<NsdDiscoveryEvent>

    fun advertise(
        roomId: String,
        roomName: String,
        port: Int,
        term: Long,
        onRegistered: (String) -> Unit = {},
        onError: (String) -> Unit = {},
    )

    fun stopAdvertising()

    fun stopDiscovery()
}

/** The phone-hosted local network a coordinator may create for a room. */
interface HotspotPort {
    val state: StateFlow<HotspotInfo?>

    fun start(onError: (String) -> Unit = {})

    fun stop()
}
