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
}
