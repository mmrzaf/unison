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
import com.darius.unison.library.LibraryImportProgress
import com.darius.unison.library.LibraryImportStage
import com.darius.unison.library.LibrarySort
import com.darius.unison.library.M3uAmbiguousEntry
import com.darius.unison.library.M3uResolvedEntry
import com.darius.unison.library.M3uResolutionPolicy
import com.darius.unison.library.M3uUnresolvedEntry
import com.darius.unison.library.M3uCodec
import com.darius.unison.library.M3uEntry
import com.darius.unison.library.PlaylistDetail
import com.darius.unison.library.StorageSummary
import com.darius.unison.model.AppCommand
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.RoomUiState
import com.darius.unison.model.RoomPlaybackTelemetry
import com.darius.unison.model.toUiState
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

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.unisonContainer
    private val activeOperationCount = MutableStateFlow(0)
    private val busy = activeOperationCount.map { it > 0 }.distinctUntilChanged()
    private val message = MutableStateFlow<String?>(null)
    private val roomActions = RoomSessionActions(getApplication(), container, viewModelScope, message)
    private val importCoordinator = LibraryImportCoordinator(
        application = getApplication(),
        container = container,
        scope = viewModelScope,
        activeOperationCount = activeOperationCount,
        message = message,
        roomActions = roomActions,
    )
    private val importProgress = importCoordinator.importProgress
    private val playlistActions = PlaylistActions(
        application = getApplication(),
        container = container,
        scope = viewModelScope,
        activeOperationCount = activeOperationCount,
        message = message,
        addTracksToRoom = roomActions::addTracksToRoom,
    )
    private val pendingM3uResolution = importCoordinator.pendingM3uResolution
    private val selectedPlaylist = playlistActions.selectedPlaylist
    private val pendingShare = importCoordinator.pendingShare
    private val libraryQuery = MutableStateFlow("")
    private val librarySort = MutableStateFlow(LibrarySort.RECENT)
    private val _pickerQuery = MutableStateFlow("")
    val pickerQuery: StateFlow<String> = _pickerQuery.asStateFlow()

    /**
     * Player position changes many times per second. Keep those ticks out of [MainUiState] so the
     * library, queue, playlists, and navigation do not all recompose while a song is playing.
     */
    private val roomPresentation = combine(
        container.roomStore.structure,
        container.roomStore.playback.map { playback ->
            playback.copy(
                localPositionMs = null,
                localDriftMs = null,
                memberPlayback = emptyMap(),
            )
        }.distinctUntilChanged(),
        container.roomStore.transfers,
    ) { structure, playback, transfers ->
        structure.toUiState(playback, transfers)
    }

    val playbackPositionMs: StateFlow<Long> = container.roomStore.playback
        .map { it.localPositionMs ?: 0L }
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
        roomPresentation,
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

    fun command(command: AppCommand, feedback: String? = command.feedbackMessage()) =
        roomActions.command(command, feedback)

    fun addTracksToRoom(trackIds: List<TrackId>, insertAfterCurrent: Boolean = false) =
        roomActions.addTracksToRoom(trackIds, insertAfterCurrent)

    fun loadTrackIds(query: String, onLoaded: (Set<TrackId>) -> Unit) =
        roomActions.loadTrackIds(query, onLoaded)

    fun saveName(name: String) {
        viewModelScope.launch {
            userResult {
                container.settings.saveDisplayName(name)
                val identity = container.settings.ensureIdentity()
                container.roomStore.update { it.copy(localIdentity = identity) }
            }.onFailure { message.value = "Could not save your name" }
        }
    }

    fun importMusic(uris: List<Uri>, toRoom: Boolean) = importCoordinator.importMusic(uris, toRoom)

    fun cancelImport() = importCoordinator.cancel()

    fun importM3u(uri: Uri, toRoom: Boolean) = importCoordinator.importM3u(uri, toRoom)

    fun resolvePendingM3u(treeUri: Uri) = importCoordinator.resolvePendingM3u(treeUri)

    fun choosePendingM3uCandidate(entryIndex: Int, trackId: TrackId) =
        importCoordinator.choosePendingM3uCandidate(entryIndex, trackId)

    fun skipPendingM3uAmbiguity(entryIndex: Int) =
        importCoordinator.skipPendingM3uAmbiguity(entryIndex)

    fun finishPendingM3uWithoutFolder() = importCoordinator.finishPendingM3uWithoutFolder()

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

    fun handleIntent(intent: Intent?) = importCoordinator.handleIntent(intent)

    fun resolvePendingShare(destination: ShareDestination?) =
        importCoordinator.resolvePendingShare(destination)

    fun createPlaylist(name: String, trackIds: List<TrackId>) = playlistActions.create(name, trackIds)

    fun openPlaylist(playlistId: String) = playlistActions.open(playlistId)

    fun closePlaylist() = playlistActions.close()

    fun renamePlaylist(playlistId: String, name: String) = playlistActions.rename(playlistId, name)

    fun updatePlaylistTracks(playlistId: String, trackIds: List<TrackId>) =
        playlistActions.replaceTracks(playlistId, trackIds)

    fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) =
        playlistActions.addTracks(playlistId, trackIds)

    fun deletePlaylist(playlistId: String) = playlistActions.delete(playlistId)

    fun addPlaylistToRoom(playlistId: String) = playlistActions.addToRoom(playlistId)

    fun exportPlaylist(playlistId: String, destination: Uri) = playlistActions.export(playlistId, destination)

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

    fun clearRoomError(expected: String) = roomActions.clearRoomError(expected)

    fun joinLink(): String? = roomActions.joinLink()


}
