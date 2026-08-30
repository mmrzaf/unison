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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TransferProgress
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.TransportCommandStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow


@Composable
internal fun PlaybackTransitionStatus(
    transition: RoomPlaybackUiPolicy.TransitionPresentation,
) {
    val progress = transition.progressFraction
    val failed = transition.kind == RoomPlaybackUiPolicy.TransitionKind.FAILED
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color =
            if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        contentColor =
            if (failed) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            buildString {
                append(transition.message)
                if (progress != null) append(" · ${(progress * 100).toInt()}%")
            },
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
        )
    }
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        )
    }
}

@Composable
internal fun UpNextStatus(
    track: TrackDescriptor,
    transfer: TransferProgress?,
    readiness: RoomMediaReadiness,
) {
    val suffix =
        when {
            transfer?.state == MemberTrackState.RECEIVING -> " · Preparing"
            transfer?.state == MemberTrackState.VERIFYING ||
                transfer?.state == MemberTrackState.PREPARING_PLAYER -> " · Almost ready"
            transfer?.state == MemberTrackState.FAILED -> " · Needs attention"
            readiness == RoomMediaReadiness.NEEDS_PREPARATION -> " · Not ready"
            readiness == RoomMediaReadiness.PREPARING -> " · Preparing"
            else -> ""
        }
    val failed = transfer?.state == MemberTrackState.FAILED
    Surface(
        shape = MaterialTheme.shapes.medium,
        color =
            if (failed) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor =
            if (failed) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                "Up next",
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (failed) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${track.displayTitle}$suffix",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

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
    val controlScale = if (pressed) 0.9f else 1f
    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
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
            PendingActionIndicator(
                modifier = Modifier.align(Alignment.TopEnd),
                size = 10.dp,
            )
        }
    }
}

@Composable
internal fun TransportPrimaryButton(
    control: RoomPlaybackUiPolicy.PrimaryControl,
    pending: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val controlScale = if (pressed) 0.9f else 1f
    val outerSize = if (compact) 48.dp else 72.dp
    val buttonSize = if (compact) 44.dp else 64.dp
    val iconSize = if (compact) 24.dp else 34.dp
    val busy =
        control == RoomPlaybackUiPolicy.PrimaryControl.PREPARING ||
            control == RoomPlaybackUiPolicy.PrimaryControl.WAITING_FOR_NEXT ||
            control == RoomPlaybackUiPolicy.PrimaryControl.RECOVERING
    val contentDescription =
        when (control) {
            RoomPlaybackUiPolicy.PrimaryControl.NONE -> "Playback unavailable"
            RoomPlaybackUiPolicy.PrimaryControl.PLAY -> "Play"
            RoomPlaybackUiPolicy.PrimaryControl.PAUSE -> "Pause"
            RoomPlaybackUiPolicy.PrimaryControl.PREPARE -> "Prepare song"
            RoomPlaybackUiPolicy.PrimaryControl.PREPARING -> "Preparing song"
            RoomPlaybackUiPolicy.PrimaryControl.WAITING_FOR_NEXT -> "Preparing next song"
            RoomPlaybackUiPolicy.PrimaryControl.REJOIN -> "Rejoin playback"
            RoomPlaybackUiPolicy.PrimaryControl.RECOVERING -> "Recovering playback"
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
            if (busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (compact) 20.dp else 28.dp),
                    strokeWidth = if (compact) 2.dp else 3.dp,
                )
            } else {
                Icon(
                    when (control) {
                        RoomPlaybackUiPolicy.PrimaryControl.PAUSE -> Icons.Default.Pause
                        RoomPlaybackUiPolicy.PrimaryControl.PREPARE -> Icons.Default.Download
                        RoomPlaybackUiPolicy.PrimaryControl.REJOIN -> Icons.Default.Refresh
                        RoomPlaybackUiPolicy.PrimaryControl.NONE,
                        RoomPlaybackUiPolicy.PrimaryControl.PLAY,
                        RoomPlaybackUiPolicy.PrimaryControl.PREPARING,
                        RoomPlaybackUiPolicy.PrimaryControl.WAITING_FOR_NEXT,
                        RoomPlaybackUiPolicy.PrimaryControl.RECOVERING -> Icons.Default.PlayArrow
                    },
                    contentDescription,
                    Modifier.size(iconSize),
                )
            }
        }
        if (pending && !busy) {
            PendingActionIndicator(
                modifier = Modifier.align(Alignment.TopEnd),
                size = if (compact) 9.dp else 11.dp,
            )
        }
    }
}

@Composable
private fun PendingActionIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val primary = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            modifier
                .size(size)
                .background(primary.copy(alpha = 0.18f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size((size.value * 0.42f).dp)
                .background(primary, CircleShape)
        )
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
    transfer: TransferProgress? = null,
    readiness: RoomMediaReadiness = RoomMediaReadiness.NEEDS_PREPARATION,
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
    val mediaPresentation =
        RoomQueueUiPolicy.mediaPresentation(
            readiness = readiness,
            transfer = transfer,
            current = current,
            playing = playing,
        )
    val primaryActionLabel =
        when (mediaPresentation.tapAction) {
            RoomQueueUiPolicy.TapAction.PLAY -> "Play song"
            RoomQueueUiPolicy.TapAction.PREPARE -> "Prepare song"
            RoomQueueUiPolicy.TapAction.NONE -> null
        }
    val dragged = draggedIndex
    val target = dragTargetIndex
    val dragActive = dragged != null && target != null
    val estimatedRowHeightPx = if (dragActive) with(LocalDensity.current) { 58.dp.toPx() } else 0f
    val displacedOffsetPx =
        if (dragged == null || target == null || dragged == index) {
            0f
        } else {
            when {
                dragged < target && index in (dragged + 1)..target -> -estimatedRowHeightPx
                dragged > target && index in target until dragged -> estimatedRowHeightPx
                else -> 0f
            }
        }
    val dragModifier =
        if (dragActive && (dragged == index || displacedOffsetPx != 0f)) {
            Modifier.zIndex(if (dragged == index) 1f else 0f).graphicsLayer {
                translationY = if (dragged == index) dragOffsetPx else displacedOffsetPx
                shadowElevation = if (dragged == index && dragOffsetPx != 0f) 8.dp.toPx() else 0f
            }
        } else {
            Modifier
        }
    Row(
        Modifier.fillMaxWidth()
            .heightIn(min = 58.dp)
            .background(
                if (current) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
                else androidx.compose.ui.graphics.Color.Transparent,
                MaterialTheme.shapes.medium,
            )
            .then(dragModifier)
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
                        CustomAccessibilityAction("Move next") {
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
            .clickable(
                enabled = draggedIndex == null && playEnabled && primaryActionLabel != null,
                onClickLabel = primaryActionLabel,
                onClick = onPlay,
            )
            .padding(start = 4.dp, end = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            when {
                pending -> PendingActionIndicator(size = 12.dp)
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
            val readinessDetail = mediaPresentation.detail
            val detail =
                remember(track.artist, readinessDetail) {
                    listOfNotNull(
                            track.artist?.takeIf(String::isNotBlank),
                            readinessDetail,
                        )
                        .joinToString(" · ")
                }
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
        val durationLabel = remember(track.durationMs) { formatDuration(track.durationMs) }
        Text(
            durationLabel,
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
                if (primaryActionLabel != null) {
                    DropdownMenuItem(
                        text = { Text(primaryActionLabel) },
                        enabled = playEnabled,
                        onClick = {
                            menu = false
                            onPlay()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Move next") },
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
