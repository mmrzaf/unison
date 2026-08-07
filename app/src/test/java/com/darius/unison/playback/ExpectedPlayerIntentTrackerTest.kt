package com.darius.unison.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExpectedPlayerIntentTrackerTest {
    @Test
    fun `expected app mutation is consumed exactly once`() {
        val tracker = ExpectedPlayerIntentTracker(ttlNs = 1_000, maxPending = 4)
        tracker.expect(false, "scheduled_pause", nowNs = 100)

        assertEquals("scheduled_pause", tracker.consume(false, nowNs = 200))
        assertNull(tracker.consume(false, nowNs = 201))
    }

    @Test
    fun `unexpected value is not consumed and expiration removes stale intent`() {
        val tracker = ExpectedPlayerIntentTracker(ttlNs = 100, maxPending = 4)
        tracker.expect(false, "pause", nowNs = 100)

        assertNull(tracker.consume(true, nowNs = 150))
        assertNull(tracker.consume(false, nowNs = 201))
    }
}
