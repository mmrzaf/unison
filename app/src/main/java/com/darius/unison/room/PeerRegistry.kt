package com.darius.unison.room

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.ProtocolBody

/** Session-scoped peer, availability, and assignment indexes owned by the room actor. */
internal class PeerRegistry<C> {
    val connections = mutableMapOf<PeerId, C>()
    val endpoints = mutableMapOf<PeerId, PeerEndpoint>()
    val availability = mutableMapOf<TrackId, MutableSet<PeerId>>()
    val waitingForSource = mutableMapOf<TrackId, MutableSet<PeerId>>()
    val lastSeenElapsedMs = mutableMapOf<PeerId, Long>()
    val announcedTrackIds = mutableSetOf<TrackId>()
    val pendingTransferAssignments = mutableMapOf<String, ProtocolBody.TrackSourceAssigned>()

    fun markAvailable(peerId: PeerId, trackId: TrackId) {
        availability.getOrPut(trackId) { mutableSetOf() }.add(peerId)
        waitingForSource[trackId]?.remove(peerId)
    }

    fun markNeeded(peerId: PeerId, trackId: TrackId) {
        availability[trackId]?.remove(peerId)
        waitingForSource.getOrPut(trackId) { mutableSetOf() }.add(peerId)
    }

    fun removePeer(peerId: PeerId) {
        endpoints.remove(peerId)
        lastSeenElapsedMs.remove(peerId)
        availability.values.forEach { it.remove(peerId) }
        waitingForSource.values.forEach { it.remove(peerId) }
    }

    fun clearSession(closeConnection: (C) -> Unit) {
        connections.values.forEach(closeConnection)
        connections.clear()
        endpoints.clear()
        availability.clear()
        waitingForSource.clear()
        lastSeenElapsedMs.clear()
        announcedTrackIds.clear()
        pendingTransferAssignments.clear()
    }
}
