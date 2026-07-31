package com.darius.unison.room

import kotlin.math.abs

/** Pure queue drag calculations shared by Compose and deterministic JVM tests. */
object QueueDragPolicy {
    data class VisibleItem(
        val queueIndex: Int,
        val offsetPx: Float,
        val sizePx: Float,
    ) {
        val centerPx: Float
            get() = offsetPx + sizePx / 2f
    }

    fun targetIndex(
        draggedCenterPx: Float,
        visibleItems: List<VisibleItem>,
        fallbackIndex: Int,
    ): Int =
        visibleItems.minByOrNull { abs(it.centerPx - draggedCenterPx) }?.queueIndex ?: fallbackIndex

    /** Signed pixels per animation frame. Negative scrolls toward the beginning. */
    fun autoScrollPerFrame(
        pointerCenterPx: Float,
        viewportStartPx: Float,
        viewportEndPx: Float,
        edgeSizePx: Float,
        maxScrollPx: Float,
    ): Float {
        if (edgeSizePx <= 0f || maxScrollPx <= 0f || viewportEndPx <= viewportStartPx) return 0f
        val startEdge = viewportStartPx + edgeSizePx
        val endEdge = viewportEndPx - edgeSizePx
        return when {
            pointerCenterPx < startEdge -> {
                val fraction = ((startEdge - pointerCenterPx) / edgeSizePx).coerceIn(0f, 1f)
                -maxScrollPx * fraction
            }

            pointerCenterPx > endEdge -> {
                val fraction = ((pointerCenterPx - endEdge) / edgeSizePx).coerceIn(0f, 1f)
                maxScrollPx * fraction
            }

            else -> 0f
        }
    }
}
