package com.darius.unison.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.darius.unison.room.QueueDragPolicy
import kotlin.math.abs

/**
 * Long-press drag reordering for a [LazyListState]: pointer tracking, drop-target calculation and
 * edge auto-scroll. The caller supplies only how a visible list item maps to its data index, so the
 * room queue and playlist screens share one implementation.
 */
@Stable
class DragReorderState
internal constructor(
    private val listState: LazyListState,
    private val edgePx: Float,
    private val maxScrollPx: Float,
    private val itemCount: () -> Int,
    private val indexOfItem: (LazyListItemInfo) -> Int?,
) {
    var draggedIndex by mutableStateOf<Int?>(null)
        private set

    var targetIndex by mutableStateOf<Int?>(null)
        private set

    var offsetPx by mutableFloatStateOf(0f)
        private set

    private var pointerCenterPx by mutableStateOf<Float?>(null)
    private var autoScrollPx by mutableFloatStateOf(0f)

    fun start(index: Int) {
        val visibleItem =
            listState.layoutInfo.visibleItemsInfo.firstOrNull { indexOfItem(it) == index }
        draggedIndex = index
        targetIndex = index
        offsetPx = 0f
        pointerCenterPx = visibleItem?.let { it.offset + it.size / 2f }
        autoScrollPx = 0f
        recalculate()
    }

    fun update(deltaY: Float) {
        offsetPx += deltaY
        pointerCenterPx = (pointerCenterPx ?: 0f) + deltaY
        recalculate()
    }

    fun reset() {
        draggedIndex = null
        targetIndex = null
        offsetPx = 0f
        pointerCenterPx = null
        autoScrollPx = 0f
    }

    /** Ends the drag and returns the index the item was dropped on, or null if it did not move. */
    fun finish(): Int? {
        val origin = draggedIndex
        val target = targetIndex
        reset()
        return target?.takeIf { origin != null && it != origin }
    }

    internal suspend fun autoScrollWhileDragging() {
        while (draggedIndex != null) {
            withFrameNanos {}
            val requested = autoScrollPx
            if (abs(requested) < MIN_SCROLL_PX) continue
            val consumed = listState.scrollBy(requested)
            if (abs(consumed) < MIN_SCROLL_PX) {
                autoScrollPx = 0f
                continue
            }
            offsetPx += consumed
            recalculate()
        }
    }

    private fun recalculate() {
        val count = itemCount()
        if (count == 0) return
        val origin = draggedIndex ?: return
        val pointerCenter = pointerCenterPx ?: return
        val layout = listState.layoutInfo
        val visible =
            layout.visibleItemsInfo.mapNotNull { item ->
                val index = indexOfItem(item) ?: return@mapNotNull null
                QueueDragPolicy.VisibleItem(
                    queueIndex = index,
                    offsetPx = item.offset.toFloat(),
                    sizePx = item.size.toFloat(),
                )
            }
        targetIndex =
            QueueDragPolicy.targetIndex(pointerCenter, visible, origin).coerceIn(0, count - 1)
        autoScrollPx =
            QueueDragPolicy.autoScrollPerFrame(
                pointerCenterPx = pointerCenter,
                viewportStartPx = layout.viewportStartOffset.toFloat(),
                viewportEndPx = layout.viewportEndOffset.toFloat(),
                edgeSizePx = edgePx,
                maxScrollPx = maxScrollPx,
            )
    }

    private companion object {
        const val MIN_SCROLL_PX = 0.5f
    }
}

/**
 * Remembers a [DragReorderState] for [listState] and runs its auto-scroll while an item is held.
 * [indexOfItem] returns the data index of a visible item, or null for rows that are not draggable.
 */
@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    itemCount: Int,
    indexOfItem: (LazyListItemInfo) -> Int?,
): DragReorderState {
    val density = LocalDensity.current
    val latestItemCount by rememberUpdatedState(itemCount)
    val latestIndexOfItem by rememberUpdatedState(indexOfItem)
    val state =
        remember(listState) {
            DragReorderState(
                listState = listState,
                edgePx = with(density) { 72.dp.toPx() },
                maxScrollPx = with(density) { 28.dp.toPx() },
                itemCount = { latestItemCount },
                indexOfItem = { latestIndexOfItem(it) },
            )
        }
    LaunchedEffect(state.draggedIndex) { state.autoScrollWhileDragging() }
    return state
}

/** The long-press drag handle shared by every reorderable row. */
@Composable
fun DragHandleIcon(
    size: Dp,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onDragEnd: () -> Unit,
) {
    val latestStart by rememberUpdatedState(onDragStart)
    val latestDelta by rememberUpdatedState(onDragDelta)
    val latestCancel by rememberUpdatedState(onDragCancel)
    val latestEnd by rememberUpdatedState(onDragEnd)
    Icon(
        Icons.Default.DragHandle,
        "Hold and drag to reorder",
        modifier =
            Modifier.size(size).padding(10.dp).pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { latestStart() },
                    onDragCancel = { latestCancel() },
                    onDragEnd = { latestEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        latestDelta(dragAmount.y)
                    },
                )
            },
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
