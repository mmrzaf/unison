package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot

/** Resolves synchronized transport lead time from only the healthy playback cohort. */
internal object RoomTransportTiming {
    fun leadNs(
        snapshot: RoomSnapshot,
        connectedPeers: Set<PeerId>,
        peerHealth: PeerPlaybackHealthRegistry,
        localCoordinatorPeerId: PeerId,
        localParticipation: LocalPlaybackParticipation,
        nowNs: Long,
        reconnecting: Boolean,
    ): Long {
        val readyPeers =
            peerHealth.readyPeers(
                connectedPeers = connectedPeers,
                localCoordinatorPeerId = localCoordinatorPeerId,
                localParticipation = localParticipation,
                nowNs = nowNs,
            )
        val qualities =
            peerHealth.readyClockQualities(
                connectedPeers = connectedPeers,
                localCoordinatorPeerId = localCoordinatorPeerId,
                nowNs = nowNs,
            )
        return TransportLeadTimePolicy.leadNs(
            readyPeerCount = readyPeers.size,
            peerRoundTripsNs = qualities.mapNotNull(PeerClockHealth::roundTripNs),
            peerUncertaintiesNs = qualities.mapNotNull(PeerClockHealth::uncertaintyNs),
            reconnecting = reconnecting,
        )
    }
}
