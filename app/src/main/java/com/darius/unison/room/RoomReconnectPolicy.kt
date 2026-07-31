package com.darius.unison.room

/**
 * Bounded local-network reconnect pacing; fast first, patient enough for Wi-Fi wake/reassociation.
 */
internal object RoomReconnectPolicy {
    const val MAX_ATTEMPTS = 6
    const val NETWORK_GRACE_MS = 20_000L
    const val NETWORK_POLL_MS = 400L

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
