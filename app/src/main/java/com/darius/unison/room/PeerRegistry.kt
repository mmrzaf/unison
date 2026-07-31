package com.darius.unison.room

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.ProtocolBody
import java.util.concurrent.ConcurrentHashMap

/** Session-scoped peer, availability, and assignment indexes owned by the room actor. */
internal class PeerRegistry<C> {
    val connections = ConcurrentHashMap<PeerId, C>()
    val endpoints = ConcurrentHashMap<PeerId, PeerEndpoint>()
    val availability = ConcurrentHashMap<TrackId, MutableSet<PeerId>>()
    val waitingForSource = ConcurrentHashMap<TrackId, MutableSet<PeerId>>()
    val lastSeenElapsedMs = ConcurrentHashMap<PeerId, Long>()
    val announcedTrackIds = ConcurrentHashMap.newKeySet<TrackId>()
    val clockReadyPeers = ConcurrentHashMap.newKeySet<PeerId>()
    val clockRoundTripNs = ConcurrentHashMap<PeerId, Long>()
    val clockUncertaintyNs = ConcurrentHashMap<PeerId, Long>()
    val transferFailureCounts = ConcurrentHashMap<String, Int>()
    val pendingTransferAssignments = ConcurrentHashMap<String, ProtocolBody.TrackSourceAssigned>()

    fun markAvailable(peerId: PeerId, trackId: TrackId) {
        availability.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
        waitingForSource[trackId]?.remove(peerId)
    }

    fun markNeeded(peerId: PeerId, trackId: TrackId) {
        availability[trackId]?.remove(peerId)
        waitingForSource.computeIfAbsent(trackId) { ConcurrentHashMap.newKeySet() }.add(peerId)
    }

    fun removePeer(peerId: PeerId) {
        endpoints.remove(peerId)
        lastSeenElapsedMs.remove(peerId)
        clockReadyPeers.remove(peerId)
        clockRoundTripNs.remove(peerId)
        clockUncertaintyNs.remove(peerId)
        availability.values.forEach { it.remove(peerId) }
        waitingForSource.values.forEach { it.remove(peerId) }
        transferFailureCounts.keys.removeAll { it.contains(":$peerId") }
    }

    fun clearSession(closeConnection: (C) -> Unit) {
        connections.values.forEach(closeConnection)
        connections.clear()
        endpoints.clear()
        availability.clear()
        waitingForSource.clear()
        lastSeenElapsedMs.clear()
        announcedTrackIds.clear()
        clockReadyPeers.clear()
        clockRoundTripNs.clear()
        clockUncertaintyNs.clear()
        transferFailureCounts.clear()
        pendingTransferAssignments.clear()
    }
}
