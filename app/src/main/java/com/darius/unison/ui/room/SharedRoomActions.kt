package com.darius.unison.ui

import androidx.compose.runtime.Immutable
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.TrackId
import com.darius.unison.util.DiagnosticEvent

/** Small interaction groups keep room rendering readable without introducing an event framework. */
@Immutable
internal class SharedRoomActions(
    val playback: RoomPlaybackActions,
    val queue: RoomQueueActions,
    val session: RoomSessionUiActions,
    val diagnostics: RoomDiagnosticsActions,
)

@Immutable
internal class RoomPlaybackActions(
    val play: () -> Unit,
    val pause: () -> Unit,
    val seek: (Long) -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val playQueueItem: (QueueItemId) -> Unit,
    val prepareQueueItem: (QueueItemId) -> Unit,
)

@Immutable
internal class RoomQueueActions(
    val shuffle: () -> Unit,
    val repeat: (RepeatMode) -> Unit,
    val chooseFiles: () -> Unit,
    val importM3u: () -> Unit,
    val pickerQueryChange: (String) -> Unit,
    val selectAllTracks: (String, (Set<TrackId>) -> Unit) -> Unit,
    val addLibrarySelectionToRoom: (Boolean, List<String>, List<TrackId>, Boolean) -> Unit,
    val removeQueueItem: (QueueItemId) -> Unit,
    val moveQueueItem: (QueueItemId, Int) -> Unit,
    val moveQueueItemNext: (QueueItemId) -> Unit,
    val keepTrack: (TrackId) -> Unit,
    val saveQueue: (String, List<TrackId>) -> Unit,
    val clearPlayed: () -> Unit,
    val clearQueue: () -> Unit,
)

@Immutable
internal class RoomSessionUiActions(
    val updateOptions: (RoomOptions) -> Unit,
    val showAbout: () -> Unit,
    val leave: () -> Unit,
    val retryIssue: () -> Unit,
    val dismissIssue: (String) -> Unit,
)

@Immutable
internal class RoomDiagnosticsActions(
    val loadLogs: () -> List<DiagnosticEvent>,
    val clearLogs: () -> Unit,
)
