package com.darius.unison.ui

import androidx.compose.runtime.Immutable
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.TrackId
import com.darius.unison.sync.PlaybackSyncProfile
import com.darius.unison.util.DiagnosticEvent

/**
 * Stable interaction surface for the room screen.
 *
 * Keeping the large callback set behind one object materially reduces the Compose/JIT method
 * signature and keeps room rendering focused on state rather than wiring.
 */
@Immutable
internal class SharedRoomActions(
    val loadRoomLogs: () -> List<DiagnosticEvent>,
    val onPickerQueryChange: (String) -> Unit,
    val onPlay: () -> Unit,
    val onPause: () -> Unit,
    val onSeek: (Long) -> Unit,
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onPlayQueueItem: (QueueItemId) -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: (RepeatMode) -> Unit,
    val onChooseFiles: () -> Unit,
    val onImportM3u: () -> Unit,
    val onSelectAllTracks: (String, (Set<TrackId>) -> Unit) -> Unit,
    val onAddLibrarySelectionToRoom: (Boolean, List<String>, List<TrackId>) -> Unit,
    val onRemoveQueueItem: (QueueItemId) -> Unit,
    val onMoveQueueItem: (QueueItemId, Int) -> Unit,
    val onMoveQueueItemNext: (QueueItemId) -> Unit,
    val onKeepTrack: (TrackId) -> Unit,
    val onUpdateOptions: (RoomOptions) -> Unit,
    val onSetRetentionPolicy: (RetentionPolicy) -> Unit,
    val onSetPlaybackSyncProfile: (PlaybackSyncProfile) -> Unit,
    val onSaveQueue: (String, List<TrackId>) -> Unit,
    val onClearPlayed: () -> Unit,
    val onClearQueue: () -> Unit,
    val onShowAbout: () -> Unit,
    val onLeave: () -> Unit,
    val onRetryIssue: () -> Unit,
    val onDismissIssue: (String) -> Unit,
)
