package com.darius.unison.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueDragPolicyTest {
    @Test
    fun targetUsesActualVisibleCenters() {
        val visible =
            listOf(
                QueueDragPolicy.VisibleItem(20, 0f, 50f),
                QueueDragPolicy.VisibleItem(21, 50f, 90f),
                QueueDragPolicy.VisibleItem(22, 140f, 60f),
            )
        assertEquals(21, QueueDragPolicy.targetIndex(112f, visible, 20))
        assertEquals(22, QueueDragPolicy.targetIndex(185f, visible, 20))
    }

    @Test
    fun edgeScrollAcceleratesTowardViewportBoundary() {
        val nearBottom = QueueDragPolicy.autoScrollPerFrame(940f, 0f, 1_000f, 100f, 30f)
        val atBottom = QueueDragPolicy.autoScrollPerFrame(1_000f, 0f, 1_000f, 100f, 30f)
        val nearTop = QueueDragPolicy.autoScrollPerFrame(40f, 0f, 1_000f, 100f, 30f)
        assertTrue(nearBottom > 0f)
        assertTrue(atBottom > nearBottom)
        assertTrue(nearTop < 0f)
        assertEquals(0f, QueueDragPolicy.autoScrollPerFrame(500f, 0f, 1_000f, 100f, 30f))
    }
}
