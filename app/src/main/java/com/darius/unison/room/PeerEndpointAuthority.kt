package com.darius.unison.room

import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.network.NetworkAddressPolicy

internal data class PeerEndpointUpdate(
    val endpoint: PeerEndpoint,
    val announcedHostMatchesAuthenticatedHost: Boolean,
)

/** Normalizes participant endpoint announcements without allowing host ownership to move. */
internal object PeerEndpointAuthority {
    fun normalizeAnnouncement(
        peerId: PeerId,
        authenticatedHostAddress: String,
        announced: PeerEndpoint,
        lastSeenElapsedMs: Long,
    ): PeerEndpointUpdate? {
        if (announced.peerId != peerId || announced.port !in 1..65535) return null

        val authenticatedAddress =
            NetworkAddressPolicy.parseAllowedAddress(authenticatedHostAddress) ?: return null
        val announcedAddress =
            NetworkAddressPolicy.parseAllowedAddress(announced.hostAddress) ?: return null

        return PeerEndpointUpdate(
            endpoint =
                announced.copy(
                    displayName = announced.displayName.trim().take(40).ifBlank { "Friend" },
                    hostAddress = authenticatedAddress.hostAddress ?: return null,
                    lastSeenElapsedMs = lastSeenElapsedMs,
                ),
            announcedHostMatchesAuthenticatedHost = authenticatedAddress == announcedAddress,
        )
    }
}
