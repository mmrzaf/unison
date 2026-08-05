package com.darius.unison.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.TransportCommandStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

@Composable
internal fun RoomSeekSlider(
    playbackPositionFlow: StateFlow<Long>,
    durationMs: Long,
    enabled: Boolean,
    transportStatus: TransportCommandStatus?,
    onSeek: (Long) -> Unit,
) {
    val playbackPositionMs by playbackPositionFlow.collectAsStateWithLifecycle()
    var dragPreview by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var submittedPositionMs by remember { mutableStateOf<Long?>(null) }
    val canonicalSeekStatus = transportStatus?.takeIf { it.action == TransportAction.SEEK }

    LaunchedEffect(canonicalSeekStatus?.commandId, canonicalSeekStatus?.phase, durationMs) {
        when (canonicalSeekStatus?.phase) {
            TransportCommandPhase.SUBMITTED,
            TransportCommandPhase.ACCEPTED,
            TransportCommandPhase.SCHEDULED,
            TransportCommandPhase.EXECUTING ->
                canonicalSeekStatus.requestedPositionMs?.let { requested ->
                    submittedPositionMs = requested.coerceIn(0, durationMs)
                }
            TransportCommandPhase.SETTLED,
            TransportCommandPhase.SUPERSEDED,
            TransportCommandPhase.REJECTED -> submittedPositionMs = null
            null -> Unit
        }
    }
    LaunchedEffect(submittedPositionMs, canonicalSeekStatus?.commandId) {
        val pending = submittedPositionMs ?: return@LaunchedEffect
        delay(1_500)
        if (submittedPositionMs == pending && canonicalSeekStatus?.active != true) {
            submittedPositionMs = null
        }
    }

    val livePosition = playbackPositionMs.coerceIn(0, durationMs).toFloat()
    val displayedPosition =
        when {
            dragging -> dragPreview
            submittedPositionMs != null -> submittedPositionMs!!.toFloat()
            else -> livePosition
        }
    val progress = (displayedPosition / durationMs.toFloat()).coerceIn(0f, 1f)

    val updateFromX: (Float, Float) -> Unit = { x, width ->
        if (width > 0f) {
            dragPreview =
                ((x / width).coerceIn(0f, 1f) * durationMs).coerceIn(0f, durationMs.toFloat())
        }
    }
    val gestures =
        if (enabled) {
            Modifier.pointerInput(durationMs) {
                    detectTapGestures { offset ->
                        updateFromX(offset.x, size.width.toFloat())
                        val requested = dragPreview.toLong()
                        submittedPositionMs = requested
                        onSeek(requested)
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            updateFromX(offset.x, size.width.toFloat())
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            updateFromX(change.position.x, size.width.toFloat())
                        },
                        onDragCancel = { dragging = false },
                        onDragEnd = {
                            dragging = false
                            val requested = dragPreview.toLong()
                            submittedPositionMs = requested
                            onSeek(requested)
                        },
                    )
                }
        } else Modifier

    Column(Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .height(30.dp)
                .progressSemantics(displayedPosition, 0f..durationMs.toFloat())
                .then(gestures)
        ) {
            Box(
                Modifier.fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            )
            Box(
                Modifier.fillMaxWidth(progress)
                    .height(4.dp)
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            if (dragging) {
                Box(
                    Modifier.offset(x = (maxWidth - 10.dp) * progress)
                        .size(10.dp)
                        .align(Alignment.CenterStart)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatDuration(displayedPosition.toLong()),
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                if (enabled) formatDuration(durationMs) else "—:—",
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
internal fun TransportControlButton(
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val controlScale =
        when {
            pressed -> 0.86f
            active -> 0.94f
            else -> 1f
        }
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = controlScale
                    scaleY = controlScale
                },
        ) {
            Box(Modifier.semantics { this.contentDescription = contentDescription }) { content() }
        }
        if (active) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
internal fun TransportPlayPauseButton(
    isPlaying: Boolean,
    pending: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val controlScale =
        when {
            pressed -> 0.86f
            pending -> 0.95f
            else -> 1f
        }
    val outerSize = if (compact) 48.dp else 64.dp
    val buttonSize = if (compact) 44.dp else 58.dp
    val iconSize = if (compact) 24.dp else 32.dp
    val contentDescription =
        when {
            isPlaying && pending -> "Pause; play is scheduled"
            isPlaying -> "Pause"
            pending -> "Play; pause is scheduled"
            else -> "Play"
        }
    Box(Modifier.size(outerSize), contentAlignment = Alignment.Center) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interactionSource,
            modifier =
                Modifier.size(buttonSize).graphicsLayer {
                    scaleX = controlScale
                    scaleY = controlScale
                },
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription,
                Modifier.size(iconSize),
            )
        }
        if (pending) {
            CircularProgressIndicator(
                modifier = Modifier.size(outerSize),
                strokeWidth = if (compact) 2.dp else 2.5.dp,
            )
        }
    }
}

@Composable
internal fun TransferStatusCard(
    transfers: List<TransferProgress>,
    titles: Map<TrackId, String>,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            transfers.take(3).forEach { transfer ->
                val title = titles[transfer.trackId] ?: "Music"
                val status =
                    when (transfer.state) {
                        MemberTrackState.RECEIVING -> "Receiving $title"
                        MemberTrackState.VERIFYING -> "Verifying $title"
                        MemberTrackState.CANCELLED -> "Cancelled $title"
                        MemberTrackState.FAILED -> "Could not receive $title"
                        else -> title
                    }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            status,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (transfer.state == MemberTrackState.RECEIVING) {
                            Text(
                                "${(transfer.fraction * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (
                        transfer.state == MemberTrackState.CANCELLED ||
                            transfer.state == MemberTrackState.FAILED
                    ) {
                        Text(
                            "Check the Wi-Fi connection. Unison can try another source.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { transfer.fraction },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (transfers.size > 3) {
                Text(
                    "${transfers.size - 3} more transfer${if (transfers.size == 4) "" else "s"} in progress",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun QueueRow(
    index: Int,
    lastIndex: Int,
    track: TrackDescriptor,
    current: Boolean,
    playing: Boolean,
    temporary: Boolean,
    pending: Boolean,
    canReorder: Boolean,
    draggedIndex: Int?,
    dragTargetIndex: Int?,
    dragOffsetPx: Float,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragCancel: () -> Unit,
    onDragEnd: () -> Unit,
    onMove: (Int) -> Unit,
    playEnabled: Boolean = true,
    onPlay: () -> Unit,
    onMoveNext: () -> Unit,
    onRemove: () -> Unit,
    onKeep: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val estimatedRowHeightPx = with(LocalDensity.current) { 56.dp.toPx() }
    val displacedOffsetPx =
        when {
            draggedIndex == null || dragTargetIndex == null || draggedIndex == index -> 0f
            draggedIndex < dragTargetIndex && index in (draggedIndex + 1)..dragTargetIndex ->
                -estimatedRowHeightPx
            draggedIndex > dragTargetIndex && index in dragTargetIndex until draggedIndex ->
                estimatedRowHeightPx
            else -> 0f
        }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 54.dp)
            .zIndex(if (draggedIndex == index) 1f else 0f)
            .graphicsLayer {
                translationY = if (draggedIndex == index) dragOffsetPx else displacedOffsetPx
                shadowElevation = if (dragOffsetPx == 0f) 0f else 8.dp.toPx()
            }
            .semantics {
                customActions = buildList {
                    if (canReorder && index > 0)
                        add(
                            CustomAccessibilityAction("Move up") {
                                onMove(index - 1)
                                true
                            }
                        )
                    if (canReorder && index < lastIndex)
                        add(
                            CustomAccessibilityAction("Move down") {
                                onMove(index + 1)
                                true
                            }
                        )
                    add(
                        CustomAccessibilityAction("Play next") {
                            onMoveNext()
                            true
                        }
                    )
                    if (temporary)
                        add(
                            CustomAccessibilityAction("Keep on this phone") {
                                onKeep()
                                true
                            }
                        )
                    add(
                        CustomAccessibilityAction("Remove from queue") {
                            onRemove()
                            true
                        }
                    )
                }
            }
            .clickable(enabled = draggedIndex == null && playEnabled, onClick = onPlay)
            .padding(start = 4.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            when {
                pending -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                playing ->
                    Icon(
                        Icons.Default.Pause,
                        "Playing",
                        Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                current ->
                    Icon(
                        Icons.Default.PlayArrow,
                        "Paused",
                        Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                else ->
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
        }
        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
            Text(
                track.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail =
                listOfNotNull(
                        track.artist?.takeIf(String::isNotBlank),
                        "Temporary".takeIf { temporary },
                    )
                    .joinToString(" • ")
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            formatDuration(track.durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canReorder) {
            Icon(
                Icons.Default.DragHandle,
                "Hold and drag to reorder",
                modifier =
                    Modifier.size(40.dp).padding(10.dp).pointerInput(index, lastIndex) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { onDragStart() },
                            onDragCancel = onDragCancel,
                            onDragEnd = onDragEnd,
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            },
                        )
                    },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.MoreVert, "Queue actions", Modifier.size(20.dp))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Play next") },
                    onClick = {
                        menu = false
                        onMoveNext()
                    },
                )
                if (temporary) {
                    DropdownMenuItem(
                        text = { Text("Keep on this phone") },
                        onClick = {
                            menu = false
                            onKeep()
                        },
                    )
                }
                if (canReorder) {
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = index > 0,
                        onClick = {
                            menu = false
                            onMove(index - 1)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = index < lastIndex,
                        onClick = {
                            menu = false
                            onMove(index + 1)
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove from queue") },
                    onClick = {
                        menu = false
                        onRemove()
                    },
                )
            }
        }
    }
}
