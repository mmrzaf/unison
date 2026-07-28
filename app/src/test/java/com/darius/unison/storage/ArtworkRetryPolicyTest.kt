package com.darius.unison.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkRetryPolicyTest {
    @Test
    fun retryDelayBacksOffAndCaps() {
        assertEquals(30_000L, ArtworkRetryPolicy.delayMs(1))
        assertEquals(60_000L, ArtworkRetryPolicy.delayMs(2))
        assertEquals(120_000L, ArtworkRetryPolicy.delayMs(3))
        assertEquals(ArtworkRetryPolicy.MAX_DELAY_MS, ArtworkRetryPolicy.delayMs(100))
    }
}
