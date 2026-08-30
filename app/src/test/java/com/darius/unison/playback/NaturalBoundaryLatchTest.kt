package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalBoundaryLatchTest {
    private val first = QueueItemId("first")
    private val second = QueueItemId("second")

    @Test
    fun duplicateBoundaryForSameFinishedItemIsIgnored() {
        val latch = NaturalBoundaryLatch()
        assertTrue(latch.tryLatch(first))
        assertFalse(latch.tryLatch(first))
    }

    @Test
    fun reconciliationSeekNearOldEndDoesNotRearmBoundary() {
        val latch = NaturalBoundaryLatch()
        assertTrue(latch.tryLatch(first))
        latch.onSeek(first, positionMs = 9_900, durationMs = 10_000)
        assertFalse(latch.tryLatch(first))
    }

    @Test
    fun genuineReplayFromBeginningCanReachBoundaryAgain() {
        val latch = NaturalBoundaryLatch()
        assertTrue(latch.tryLatch(first))
        latch.onSeek(first, positionMs = 0, durationMs = 10_000)
        assertTrue(latch.tryLatch(first))
    }

    @Test
    fun selectingAnotherItemRearmsOldItem() {
        val latch = NaturalBoundaryLatch()
        assertTrue(latch.tryLatch(first))
        latch.onSelectedItemChanged(second)
        assertTrue(latch.tryLatch(first))
    }
}
