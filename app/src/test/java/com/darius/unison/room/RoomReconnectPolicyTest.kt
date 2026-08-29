package com.darius.unison.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomReconnectPolicyTest {
    @Test
    fun delaysIncreaseAndStayBounded() {
        val delays =
            (1..RoomReconnectPolicy.MAX_ATTEMPTS).map(RoomReconnectPolicy::delayBeforeAttemptMs)
        assertEquals(delays.sorted(), delays)
        assertTrue(delays.last() <= 6_000L)
        assertTrue(delays.sum() < 15_000L)
    }
    @Test
    fun terminalRecoveryWindowsStayShortAndBounded() {
        assertTrue(RoomReconnectPolicy.LOCAL_NETWORK_GRACE_MS in 1_000L..5_000L)
        assertTrue(RoomReconnectPolicy.PEER_DISCONNECT_GRACE_MS in 1_000L..15_000L)
        assertTrue(RoomReconnectPolicy.PEER_DISCONNECT_GRACE_MS >= RoomReconnectPolicy.LOCAL_NETWORK_GRACE_MS)
        assertTrue(RoomReconnectPolicy.NETWORK_POLL_MS in 100L..1_000L)
    }

}
