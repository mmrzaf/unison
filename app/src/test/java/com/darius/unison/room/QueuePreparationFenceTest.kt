package com.darius.unison.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueuePreparationFenceTest {
    @Test
    fun destructiveMutationInvalidatesEveryOlderOperation() {
        val fence = QueuePreparationFence()
        val first = fence.issue()
        val second = fence.issue()

        assertTrue(fence.isCurrent(first))
        assertTrue(fence.isCurrent(second))

        fence.invalidate()

        assertFalse(fence.isCurrent(first))
        assertFalse(fence.isCurrent(second))
        assertTrue(fence.isCurrent(fence.issue()))
    }
}
