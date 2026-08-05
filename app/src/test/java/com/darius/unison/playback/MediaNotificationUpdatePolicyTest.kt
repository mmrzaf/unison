package com.darius.unison.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationUpdatePolicyTest {
    @Test
    fun firstUpdateRunsImmediately() {
        val decision = MediaNotificationUpdatePolicy.decide(100, null, 300, false)
        assertTrue(decision.updateNow)
        assertEquals(0L, decision.delayMs)
    }

    @Test
    fun repeatedUpdateIsDeferredToTrailingEdge() {
        val decision = MediaNotificationUpdatePolicy.decide(250, 100, 300, false)
        assertFalse(decision.updateNow)
        assertEquals(150L, decision.delayMs)
    }

    @Test
    fun updateAfterIntervalRunsImmediately() {
        val decision = MediaNotificationUpdatePolicy.decide(400, 100, 300, false)
        assertTrue(decision.updateNow)
    }

    @Test
    fun urgentForegroundStartBypassesThrottle() {
        val decision = MediaNotificationUpdatePolicy.decide(120, 100, 300, true)
        assertTrue(decision.updateNow)
    }

    @Test
    fun identicalRenderedContentIsDropped() {
        val decision =
            MediaNotificationUpdatePolicy.decide(
                nowElapsedMs = 500,
                lastUpdateElapsedMs = 100,
                minimumIntervalMs = 300,
                urgentForegroundStart = false,
                renderedContentChanged = false,
            )

        assertFalse(decision.updateNow)
        assertEquals(null, decision.delayMs)
    }

    @Test
    fun rapidTransportUpdatesStayWithinConfiguredEnqueueRate() {
        val minimumIntervalMs = 300L
        var lastUpdateMs: Long? = null
        var renderedState = 0
        var enqueued = 0
        for (nowMs in 0L..3_000L step 10L) {
            val requestedState = (nowMs / 10L).toInt() % 3
            val decision =
                MediaNotificationUpdatePolicy.decide(
                    nowElapsedMs = nowMs,
                    lastUpdateElapsedMs = lastUpdateMs,
                    minimumIntervalMs = minimumIntervalMs,
                    urgentForegroundStart = false,
                    renderedContentChanged =
                        requestedState != renderedState || lastUpdateMs == null,
                )
            if (decision.updateNow) {
                lastUpdateMs = nowMs
                renderedState = requestedState
                enqueued++
            }
        }

        assertTrue(enqueued <= 1 + 3_000L / minimumIntervalMs)
    }
}
