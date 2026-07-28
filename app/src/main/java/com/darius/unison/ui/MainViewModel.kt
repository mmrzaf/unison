package com.darius.unison.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.darius.unison.app.unisonContainer
import com.darius.unison.library.LibrarySort
import com.darius.unison.library.M3uCodec
import com.darius.unison.library.M3uEntry
import com.darius.unison.library.PlaylistDetail
import com.darius.unison.library.StorageSummary
import com.darius.unison.model.AppCommand
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomUiState
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.network.NetworkAddressPolicy
import com.darius.unison.playback.UnisonRoomService
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.room.RoomReducer
import com.darius.unison.storage.PlaylistSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ShareDestination { ROOM, LIBRARY, BOTH }
private enum class ImportCompletion { ROOM, LIBRARY, BOTH }

data class ImportProgress(
    val completed: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total <= 0) 0f else completed.toFloat() / total
}

data class PendingShare(
    val uris: List<Uri>,
    val isM3u: Boolean,
)

data class PendingM3uResolution(
    val sourceUri: Uri,
    val playlistId: String,
    val toRoom: Boolean,
    val availableTracks: List<TrackDescriptor>,
    val unresolvedCount: Int,
)

private data class LibraryControls(
    val query: String,
    val sort: LibrarySort,
)

private data class LibraryUiData(
    val totalCount: Int,
    val visibleCount: Int,
    val temporaryTrackIds: Set<TrackId>,
    val storageSummary: StorageSummary,
    val controls: LibraryControls,
)

private data class OperationState(
    val busy: Boolean,
    val importProgress: ImportProgress?,
)

private data class TransientUiState(
    val operation: OperationState,
    val message: String?,
    val pendingM3uResolution: PendingM3uResolution?,
    val selectedPlaylist: PlaylistDetail?,
    val pendingShare: PendingShare?,
)

data class MainUiState(
    val room: RoomUiState = RoomUiState(),
    val libraryTotalCount: Int = 0,
    val libraryVisibleCount: Int = 0,
    val libraryQuery: String = "",
    val librarySort: LibrarySort = LibrarySort.RECENT,
    val temporaryTrackIds: Set<TrackId> = emptySet(),
    val storageSummary: StorageSummary = StorageSummary(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val settingsLoaded: Boolean = false,
    val onboardingComplete: Boolean = false,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.TEMPORARY_24_HOURS,
    val busy: Boolean = false,
    val importProgress: ImportProgress? = null,
    val message: String? = null,
    val pendingM3uResolution: PendingM3uResolution? = null,
    val selectedPlaylist: PlaylistDetail? = null,
    val pendingShare: PendingShare? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.unisonContainer
    private val activeOperationCount = MutableStateFlow(0)
    private val busy = activeOperationCount.map { it > 0 }.distinctUntilChanged()
    private val importProgress = MutableStateFlow<ImportProgress?>(null)
    private var importJob: Job? = null
    private val message = MutableStateFlow<String?>(null)
    private val pendingM3uResolution = MutableStateFlow<PendingM3uResolution?>(null)
    private val selectedPlaylist = MutableStateFlow<PlaylistDetail?>(null)
    private val pendingShare = MutableStateFlow<PendingShare?>(null)
    private val libraryQuery = MutableStateFlow("")
    private val librarySort = MutableStateFlow(LibrarySort.RECENT)
    private val _pickerQuery = MutableStateFlow("")
    val pickerQuery: StateFlow<String> = _pickerQuery.asStateFlow()

    /**
     * Player position changes many times per second. Keep those ticks out of [MainUiState] so the
     * library, queue, playlists, and navigation do not all recompose while a song is playing.
     */
    private val roomStructure = container.roomStore.state
        .map { room ->
            val snapshot = room.snapshot
            room.copy(
                localPlaybackPositionMs = 0L,
                localDriftMs = 0L,
                snapshot = snapshot?.copy(
                    members = snapshot.members.map { member ->
                        member.copy(playbackPositionMs = null, driftMs = null)
                    },
                ),
            )
        }
        .distinctUntilChanged()

    val playbackPositionMs: StateFlow<Long> = container.roomStore.state
        .map { it.localPlaybackPositionMs }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    private val operation = combine(busy, importProgress, ::OperationState)
    private val transient =
        combine(
            operation,
            message,
            pendingM3uResolution,
            selectedPlaylist,
            pendingShare
        ) { operationState, notice, pending, playlist, share ->
            TransientUiState(operationState, notice, pending, playlist, share)
        }
    private val preferences = combine(
        container.settings.onboardingComplete,
        container.settings.retentionPolicy,
    ) { onboarded, retention -> onboarded to retention }
    private val debouncedLibraryQuery = libraryQuery.debounce(180).distinctUntilChanged()
    private val libraryControls = combine(libraryQuery, librarySort, ::LibraryControls)

    val libraryTracks: Flow<PagingData<TrackDescriptor>> =
        combine(debouncedLibraryQuery, librarySort) { query, sort -> query to sort }
            .flatMapLatest { (query, sort) -> container.trackRepository.pagedLibrary(query, sort) }
            .cachedIn(viewModelScope)

    val pickerTracks: Flow<PagingData<TrackDescriptor>> = _pickerQuery
        .debounce(180)
        .distinctUntilChanged()
        .flatMapLatest { query -> container.trackRepository.pagedLibrary(query, LibrarySort.TITLE) }
        .cachedIn(viewModelScope)

    private val visibleTrackCount =
        debouncedLibraryQuery.flatMapLatest(container.trackRepository::observeLibraryCount)
    private val totalTrackCount = container.trackRepository.observeLibraryCount("")
    private val library = combine(
        totalTrackCount,
        visibleTrackCount,
        container.trackRepository.temporaryTrackIds,
        container.trackRepository.storageSummary,
        libraryControls,
    ) { totalCount, visibleCount, temporaryTrackIds, storageSummary, controls ->
        LibraryUiData(totalCount, visibleCount, temporaryTrackIds, storageSummary, controls)
    }

    val state: StateFlow<MainUiState> = combine(
        roomStructure,
        library,
        container.playlistRepository.playlists,
        preferences,
        transient,
    ) { room, libraryState, playlists, preferencesState, transientState ->
        MainUiState(
            room = room,
            libraryTotalCount = libraryState.totalCount,
            libraryVisibleCount = libraryState.visibleCount,
            libraryQuery = libraryState.controls.query,
            librarySort = libraryState.controls.sort,
            temporaryTrackIds = libraryState.temporaryTrackIds,
            storageSummary = libraryState.storageSummary,
            playlists = playlists,
            settingsLoaded = true,
            onboardingComplete = preferencesState.first,
            retentionPolicy = preferencesState.second,
            busy = transientState.operation.busy,
            importProgress = transientState.operation.importProgress,
            message = transientState.message,
            pendingM3uResolution = transientState.pendingM3uResolution,
            selectedPlaylist = transientState.selectedPlaylist,
            pendingShare = transientState.pendingShare,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun setLibraryQuery(value: String) {
        libraryQuery.value = value.take(120)
    }

    fun setLibrarySort(value: LibrarySort) {
        librarySort.value = value
    }

    fun setPickerQuery(value: String) {
        _pickerQuery.value = value.take(120)
    }

    fun command(command: AppCommand, feedback: String? = command.feedbackMessage()) {
        UnisonRoomService.start(getApplication())
        if (container.roomCommandBus.trySend(command).isSuccess) {
            feedback?.let { message.value = it }
        } else {
            message.value = "Unison is busy. Try again."
        }
    }

    fun addTracksToRoom(trackIds: List<TrackId>, insertAfterCurrent: Boolean = false) {
        if (trackIds.isEmpty()) {
            message.value = "Select at least one song"
            return
        }
        val queueSize = container.roomStore.state.value.snapshot?.queue?.size
        if (queueSize == null) {
            message.value = "Join or create a room first"
            return
        }
        val availableSlots = (RoomReducer.MAX_QUEUE_ITEMS - queueSize).coerceAtLeast(0)
        val selectedTracks = trackIds.take(availableSlots)
        if (selectedTracks.isEmpty()) {
            message.value = "The room queue is full"
            return
        }
        command(
            AppCommand.AddTracks(selectedTracks, insertAfterCurrent),
            feedback = when {
                insertAfterCurrent -> "Playing next"
                selectedTracks.size < trackIds.size ->
                    "Adding ${selectedTracks.size} songs; the queue holds up to ${RoomReducer.MAX_QUEUE_ITEMS}"

                selectedTracks.size == 1 -> "Added to the queue"
                else -> "Adding ${selectedTracks.size} songs"
            },
        )
    }

    fun loadTrackIds(query: String, onLoaded: (Set<TrackId>) -> Unit) {
        viewModelScope.launch {
            userResult { container.trackRepository.libraryTrackIds(query) }
                .onSuccess(onLoaded)
                .onFailure { message.value = "Could not select this music" }
        }
    }

    fun saveName(name: String) {
        viewModelScope.launch {
            userResult {
                container.settings.saveDisplayName(name)
                val identity = container.settings.ensureIdentity()
                container.roomStore.update { it.copy(localIdentity = identity) }
            }.onFailure { message.value = "Could not save your name" }
        }
    }

    fun importMusic(uris: List<Uri>, toRoom: Boolean) {
        startAudioImport(
            uris = uris,
            retention = if (toRoom) RetentionPolicy.TEMPORARY_24_HOURS else RetentionPolicy.KEEP_IN_LIBRARY,
            addToRoom = toRoom,
            completion = if (toRoom) ImportCompletion.ROOM else ImportCompletion.LIBRARY,
        )
    }

    fun cancelImport() {
        val job = importJob ?: return
        message.value = "Import cancelled"
        job.cancel()
    }

    fun importM3u(uri: Uri, toRoom: Boolean) {
        viewModelScope.launch {
            withBusyOperation { userResult { container.importManager.importM3u(uri) } }
                .onSuccess { result ->
                    if (result.unresolved.isEmpty()) {
                        if (toRoom && result.tracks.isNotEmpty()) {
                            command(AppCommand.AddTracks(result.tracks.map { it.trackId }))
                        }
                        message.value =
                            "Imported ${result.tracks.size} track${if (result.tracks.size == 1) "" else "s"}"
                    } else {
                        pendingM3uResolution.value = PendingM3uResolution(
                            sourceUri = uri,
                            playlistId = result.playlistId,
                            toRoom = toRoom,
                            availableTracks = result.tracks,
                            unresolvedCount = result.unresolved.size,
                        )
                        message.value = "Some playlist tracks need their music folder"
                    }
                }
                .onFailure { message.value = "Unison could not import this playlist" }
        }
    }

    fun resolvePendingM3u(treeUri: Uri) {
        val pending = pendingM3uResolution.value ?: return
        viewModelScope.launch {
            withBusyOperation {
                userResult {
                    runCatching {
                        getApplication<Application>().contentResolver.takePersistableUriPermission(
                            treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    container.importManager.importM3u(
                        uri = pending.sourceUri,
                        musicTreeUri = treeUri,
                        existingPlaylistId = pending.playlistId,
                    )
                }
            }.onSuccess { result ->
                if (result.unresolved.isEmpty()) {
                    pendingM3uResolution.value = null
                    if (pending.toRoom && result.tracks.isNotEmpty()) {
                        command(AppCommand.AddTracks(result.tracks.map { it.trackId }))
                    }
                    message.value = "Imported ${result.tracks.size} track${if (result.tracks.size == 1) "" else "s"}"
                } else {
                    pendingM3uResolution.value = pending.copy(
                        availableTracks = result.tracks,
                        unresolvedCount = result.unresolved.size,
                    )
                    message.value =
                        "${result.unresolved.size} playlist track${if (result.unresolved.size == 1) " is" else "s are"} still unavailable"
                }
            }.onFailure { message.value = "Unison could not read this folder" }
        }
    }

    fun finishPendingM3uWithoutFolder() {
        val pending = pendingM3uResolution.value ?: return
        pendingM3uResolution.value = null
        if (pending.toRoom && pending.availableTracks.isNotEmpty()) {
            command(AppCommand.AddTracks(pending.availableTracks.map { it.trackId }))
        }
        message.value = if (pending.availableTracks.isEmpty()) {
            "No playlist tracks were available"
        } else {
            "Imported ${pending.availableTracks.size}; skipped ${pending.unresolvedCount} unavailable"
        }
    }

    fun setRetentionPolicy(policy: RetentionPolicy) {
        viewModelScope.launch {
            userResult {
                container.settings.setRetentionPolicy(policy)
                if (policy == RetentionPolicy.KEEP_IN_LIBRARY) {
                    val roomTrackIds = container.roomStore.state.value.snapshot
                        ?.queue
                        ?.asSequence()
                        ?.map { it.track.trackId }
                        ?.distinct()
                        ?.toList()
                        .orEmpty()
                    val verifiedTrackIds = buildList {
                        for (trackId in roomTrackIds) {
                            if (container.trackRepository.hasVerifiedSource(trackId)) add(trackId)
                        }
                    }
                    container.trackRepository.keepMany(verifiedTrackIds)
                }
            }.onSuccess {
                message.value = if (policy == RetentionPolicy.KEEP_IN_LIBRARY) {
                    "Received music will be kept"
                } else {
                    "Received music will be removed after 24 hours"
                }
            }.onFailure { message.value = "Could not update music storage" }
        }
    }

    fun keepTrack(trackId: TrackId) {
        viewModelScope.launch {
            userResult { container.trackRepository.keep(trackId) }
                .onSuccess { message.value = "Kept in library" }
                .onFailure { message.value = "Could not keep this song" }
        }
    }

    fun clearTemporaryMusic() {
        val activeTrackIds = container.roomStore.state.value.snapshot
            ?.queue
            ?.mapTo(mutableSetOf()) { it.track.trackId }
            .orEmpty()
        viewModelScope.launch {
            withBusyOperation { userResult { container.trackRepository.clearTemporary(activeTrackIds) } }
                .onSuccess { removed ->
                    message.value = when (removed) {
                        0 -> if (activeTrackIds.isEmpty()) "No temporary music to remove" else "Temporary music in the room was kept"
                        1 -> "Removed 1 temporary song"
                        else -> "Removed $removed temporary songs"
                    }
                }
                .onFailure { message.value = "Could not clear temporary music" }
        }
    }

    fun removeTemporaryTrack(trackId: TrackId) {
        val activeQueueUsesTrack = container.roomStore.state.value.snapshot
            ?.queue
            ?.any { it.track.trackId == trackId } == true
        if (activeQueueUsesTrack) {
            message.value = "Remove this song after leaving the room"
            return
        }
        viewModelScope.launch {
            userResult { container.trackRepository.deleteTemporary(trackId) }
                .onSuccess { message.value = "Temporary copy removed" }
                .onFailure { message.value = "Could not remove this song" }
        }
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (Intent.ACTION_VIEW == intent.action && data?.scheme == "unison") {
            val invitation = parseJoinLink(data)
            if (invitation == null) {
                message.value = "This room invite is invalid or made for a different Unison version"
            } else {
                command(AppCommand.JoinRoom(invitation.first, invitation.second))
            }
            return
        }
        val uris = when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> intent.readSharedUris()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            val isPlaylist = uris.size == 1 && isM3u(intent.type, uris.single())
            if (container.roomStore.state.value.snapshot != null) {
                pendingShare.value = PendingShare(uris, isPlaylist)
            } else if (isPlaylist) {
                importM3u(uris.single(), toRoom = false)
            } else {
                importMusic(uris, toRoom = false)
            }
        }
    }

    fun resolvePendingShare(destination: ShareDestination?) {
        val pending = pendingShare.value ?: return
        pendingShare.value = null
        if (destination == null) return
        when {
            pending.isM3u -> {
                when (destination) {
                    ShareDestination.ROOM -> importM3u(pending.uris.single(), toRoom = true)
                    ShareDestination.LIBRARY -> importM3u(pending.uris.single(), toRoom = false)
                    ShareDestination.BOTH -> importM3u(pending.uris.single(), toRoom = true)
                }
            }

            destination == ShareDestination.ROOM -> importMusic(pending.uris, toRoom = true)
            destination == ShareDestination.LIBRARY -> importMusic(pending.uris, toRoom = false)
            destination == ShareDestination.BOTH -> startAudioImport(
                uris = pending.uris,
                retention = RetentionPolicy.KEEP_IN_LIBRARY,
                addToRoom = true,
                completion = ImportCompletion.BOTH,
            )
        }
    }

    private fun startAudioImport(
        uris: List<Uri>,
        retention: RetentionPolicy,
        addToRoom: Boolean,
        completion: ImportCompletion,
    ) {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return
        if (importJob?.isActive == true) {
            message.value = "Finish or cancel the current import first"
            return
        }
        importJob = viewModelScope.launch {
            activeOperationCount.update { it + 1 }
            importProgress.value = ImportProgress(0, uniqueUris.size)
            try {
                val result = container.importManager.importAudio(uniqueUris, retention) { completed, total ->
                    importProgress.value = ImportProgress(completed, total)
                }
                if (addToRoom && result.tracks.isNotEmpty()) {
                    command(AppCommand.AddTracks(result.tracks.map { it.trackId }))
                }
                message.value = when {
                    result.tracks.isEmpty() -> result.errors.firstOrNull() ?: "Unison could not add this music"
                    result.errors.isNotEmpty() ->
                        "Added ${result.tracks.size}; ${result.errors.size} could not be opened"

                    completion == ImportCompletion.BOTH -> "Added to your library and room"
                    completion == ImportCompletion.ROOM ->
                        "Added ${result.tracks.size} song${if (result.tracks.size == 1) "" else "s"} to the room"

                    else -> "Added ${result.tracks.size} song${if (result.tracks.size == 1) "" else "s"}"
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Unison could not add this music"
            } finally {
                importProgress.value = null
                activeOperationCount.update { (it - 1).coerceAtLeast(0) }
                importJob = null
            }
        }
    }

    fun createPlaylist(name: String, trackIds: List<TrackId>) {
        viewModelScope.launch {
            withBusyOperation { userResult { container.playlistRepository.create(name, trackIds) } }
                .onSuccess { message.value = "Playlist created" }
                .onFailure { message.value = "Could not create playlist" }
        }
    }

    fun openPlaylist(playlistId: String) {
        viewModelScope.launch {
            withBusyOperation { userResult { container.playlistRepository.get(playlistId) } }
                .onSuccess { playlist ->
                    selectedPlaylist.value = playlist
                    if (playlist == null) message.value = "This playlist is no longer available"
                }
                .onFailure { message.value = "Could not open playlist" }
        }
    }

    fun closePlaylist() {
        selectedPlaylist.value = null
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch {
            userResult {
                container.playlistRepository.rename(playlistId, name)
                refreshSelectedPlaylist(playlistId)
            }.onFailure { message.value = "Could not rename playlist" }
        }
    }

    fun updatePlaylistTracks(playlistId: String, trackIds: List<TrackId>) {
        viewModelScope.launch {
            userResult {
                container.playlistRepository.replaceTracks(playlistId, trackIds)
                refreshSelectedPlaylist(playlistId)
            }.onSuccess { message.value = "Playlist updated" }
                .onFailure { message.value = "Could not update playlist" }
        }
    }

    fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            userResult {
                val detail = container.playlistRepository.get(playlistId) ?: error("Playlist not found")
                container.playlistRepository.replaceTracks(playlistId, detail.tracks.map { it.trackId } + trackIds)
                refreshSelectedPlaylist(playlistId)
            }.onSuccess { message.value = "Songs added" }
                .onFailure { message.value = "Could not update playlist" }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            userResult { container.playlistRepository.delete(playlistId) }
                .onSuccess {
                    if (selectedPlaylist.value?.playlistId == playlistId) selectedPlaylist.value = null
                    message.value = "Playlist deleted"
                }
                .onFailure { message.value = "Could not delete playlist" }
        }
    }

    fun addPlaylistToRoom(playlistId: String) {
        viewModelScope.launch {
            withBusyOperation { userResult { container.playlistRepository.get(playlistId) } }
                .onSuccess { detail ->
                    if (detail == null) {
                        message.value = "This playlist is no longer available"
                    } else if (detail.tracks.isEmpty()) {
                        message.value = "This playlist is empty"
                    } else {
                        addTracksToRoom(detail.tracks.map { it.trackId })
                    }
                }
                .onFailure { message.value = "Could not open playlist" }
        }
    }

    fun exportPlaylist(playlistId: String, destination: Uri) {
        viewModelScope.launch {
            withBusyOperation {
                userResult {
                    val detail = container.playlistRepository.get(playlistId) ?: error("Playlist not found")
                    val text = M3uCodec.encode(detail.tracks.map { track ->
                        M3uEntry(
                            reference = container.trackRepository.exportReference(track.trackId)
                                ?: track.originalFileName
                                ?: "unison-${track.trackId.value}.audio",
                            durationSeconds = track.durationMs.takeIf { it > 0 }?.div(1000),
                            displayTitle = listOfNotNull(track.artist, track.displayTitle).joinToString(" - "),
                        )
                    })
                    getApplication<Application>().contentResolver.openOutputStream(destination, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                        ?: error("Could not create export file")
                }
            }.onSuccess { message.value = "Playlist exported" }
                .onFailure { message.value = "Could not export playlist" }
        }
    }

    private suspend fun <T> withBusyOperation(block: suspend () -> T): T {
        activeOperationCount.update { it + 1 }
        return try {
            block()
        } finally {
            activeOperationCount.update { (it - 1).coerceAtLeast(0) }
        }
    }

    /**
     * Kotlin's standard runCatching also captures CancellationException. UI operations must let
     * structured cancellation propagate so Activity/ViewModel teardown never becomes a fake error.
     */
    private suspend fun <T> userResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun showMessage(value: String) {
        message.value = value
    }

    fun clearMessage(expected: String) {
        message.compareAndSet(expected, null)
    }

    fun clearRoomError(expected: String) {
        container.roomStore.update { state ->
            if (state.errorMessage == expected) state.copy(errorMessage = null) else state
        }
    }

    fun joinLink(): String? {
        val room = container.roomStore.state.value
        val snapshot = room.snapshot ?: return null
        val host = room.roomAddress ?: return null
        val port = room.roomPort ?: return null
        val pin = snapshot.roomPin ?: return null
        return Uri.Builder()
            .scheme("unison")
            .authority("join")
            .appendQueryParameter("roomId", snapshot.roomId)
            .appendQueryParameter("name", snapshot.roomName)
            .appendQueryParameter("host", host)
            .appendQueryParameter("port", port.toString())
            .appendQueryParameter("pin", pin)
            .appendQueryParameter("v", PROTOCOL_VERSION.toString())
            .build().toString()
    }

    private suspend fun refreshSelectedPlaylist(playlistId: String) {
        if (selectedPlaylist.value?.playlistId == playlistId) {
            selectedPlaylist.value = container.playlistRepository.get(playlistId)
        }
    }

    private fun parseJoinLink(uri: Uri): Pair<DiscoveredRoom, String>? {
        if (uri.scheme != "unison" || uri.authority != "join") return null
        val roomId = uri.getQueryParameter("roomId")
            ?.takeIf { it.length in 8..128 && ROOM_ID_PATTERN.matches(it) }
            ?: return null
        val host = uri.getQueryParameter("host")
            ?.let { NetworkAddressPolicy.parseAllowedIpv4(it) }
            ?.hostAddress
            ?: return null
        val port = uri.getQueryParameter("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val pin = uri.getQueryParameter("pin")?.takeIf(PIN_PATTERN::matches) ?: return null
        val version = uri.getQueryParameter("v")?.toIntOrNull() ?: return null
        if (version != PROTOCOL_VERSION) return null
        val roomName = uri.getQueryParameter("name")
            ?.filterNot { it.isISOControl() }
            ?.trim()
            ?.take(60)
            ?.ifBlank { null }
            ?: "Unison room"
        return DiscoveredRoom(
            serviceName = "QR",
            roomId = roomId,
            roomName = roomName,
            hostAddress = host,
            port = port,
            protocolVersion = version,
            term = 1,
        ) to pin
    }

    private fun isM3u(mimeType: String?, uri: Uri): Boolean {
        if (mimeType in M3U_MIME_TYPES) return true
        val name = runCatching {
            getApplication<Application>().contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return name?.lowercase()?.let { it.endsWith(".m3u") || it.endsWith(".m3u8") } == true
    }

    private fun Intent.readSharedUris(): List<Uri> {
        val fromExtras = buildList {
            IntentCompat.getParcelableExtra(
                this@readSharedUris,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )?.let(::add)
            addAll(
                IntentCompat.getParcelableArrayListExtra(
                    this@readSharedUris,
                    Intent.EXTRA_STREAM,
                    Uri::class.java,
                ).orEmpty()
            )
        }
        val fromClip = buildList {
            val value = clipData ?: return@buildList
            for (index in 0 until value.itemCount) value.getItemAt(index).uri?.let(::add)
        }
        return (fromExtras + fromClip + listOfNotNull(data)).distinct()
    }

    private companion object {
        val ROOM_ID_PATTERN = Regex("[A-Za-z0-9-]+")
        val PIN_PATTERN = Regex("[0-9]{6}")
        val M3U_MIME_TYPES = setOf(
            "audio/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
        )
    }
}

private fun AppCommand.feedbackMessage(): String? = when (this) {
    AppCommand.Play,
    AppCommand.Pause,
    is AppCommand.Seek,
    AppCommand.SkipNext,
    AppCommand.SkipPrevious,
    is AppCommand.PlayQueueItem,
        -> null

    AppCommand.ShuffleQueue,
    is AppCommand.SetRepeat,
    is AppCommand.RemoveQueueItem,
    is AppCommand.MoveQueueItem,
    AppCommand.ClearPlayed,
    is AppCommand.UpdateRoomOptions,
    AppCommand.LeaveRoom,
        -> null

    is AppCommand.AddTracks -> when {
        trackIds.isEmpty() -> null
        insertAfterCurrent -> "Playing next"
        trackIds.size == 1 -> "Added to the queue"
        else -> "Adding ${trackIds.size} songs"
    }

    else -> null
}
