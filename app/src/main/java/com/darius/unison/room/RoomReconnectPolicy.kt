package com.darius.unison.room

/**
 * Bounded local-network reconnect pacing; fast first, patient enough for Wi-Fi wake/reassociation.
 */
internal object RoomReconnectPolicy {
    const val MAX_ATTEMPTS = 6
    const val NETWORK_GRACE_MS = 20_000L
    const val NETWORK_POLL_MS = 400L
    /**
     * One bounded NSD refresh lets a participant follow the same room after host IP reassignment.
     */
    const val ENDPOINT_REDISCOVERY_MS = 3_000L
    /**
     * Coordinator-local route loss may be a brief Android network transition, but never indefinite.
     */
    const val LOCAL_NETWORK_GRACE_MS = NETWORK_GRACE_MS
    /**
     * Ungraceful peer loss is kept briefly for fast reconnect, then removed from canonical
     * membership.
     */
    const val PEER_DISCONNECT_GRACE_MS = 10_000L

    fun delayBeforeAttemptMs(attempt: Int): Long {
        require(attempt in 1..MAX_ATTEMPTS)
        return when (attempt) {
            1 -> 250L
            2 -> 650L
            3 -> 1_200L
            4 -> 2_200L
            5 -> 3_800L
            else -> 6_000L
        }
    }
}
