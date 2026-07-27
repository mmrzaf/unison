package com.darius.unison.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.darius.unison.app.unisonContainer
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
import com.darius.unison.playback.UnisonRoomService
import com.darius.unison.storage.PlaylistEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PendingM3uResolution(
    val sourceUri: Uri,
    val playlistId: String,
    val toRoom: Boolean,
    val availableTracks: List<TrackDescriptor>,
    val unresolvedCount: Int,
)

private data class LibraryUiData(
    val tracks: List<TrackDescriptor>,
    val temporaryTrackIds: Set<TrackId>,
    val storageSummary: StorageSummary,
)

private data class TransientUiState(
    val busy: Boolean,
    val message: String?,
    val pendingM3uResolution: PendingM3uResolution?,
    val selectedPlaylist: PlaylistDetail?,
)

data class MainUiState(
    val room: RoomUiState = RoomUiState(),
    val tracks: List<TrackDescriptor> = emptyList(),
    val temporaryTrackIds: Set<TrackId> = emptySet(),
    val storageSummary: StorageSummary = StorageSummary(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val settingsLoaded: Boolean = false,
    val onboardingComplete: Boolean = false,
    val retentionPolicy: RetentionPolicy = RetentionPolicy.TEMPORARY_24_HOURS,
    val busy: Boolean = false,
    val message: String? = null,
    val pendingM3uResolution: PendingM3uResolution? = null,
    val selectedPlaylist: PlaylistDetail? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = application.unisonContainer
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val pendingM3uResolution = MutableStateFlow<PendingM3uResolution?>(null)
    private val selectedPlaylist = MutableStateFlow<PlaylistDetail?>(null)

    private val transient =
        combine(busy, message, pendingM3uResolution, selectedPlaylist) { isBusy, notice, pending, playlist ->
            TransientUiState(isBusy, notice, pending, playlist)
        }
    private val preferences = combine(
        container.settings.onboardingComplete,
        container.settings.retentionPolicy,
    ) { onboarded, retention -> onboarded to retention }
    private val library = combine(
        container.trackRepository.tracks,
        container.trackRepository.temporaryTrackIds,
        container.trackRepository.storageSummary,
    ) { tracks, temporaryTrackIds, storageSummary ->
        LibraryUiData(tracks, temporaryTrackIds, storageSummary)
    }

    val state: StateFlow<MainUiState> = combine(
        container.roomStore.state,
        library,
        container.playlistRepository.playlists,
        preferences,
        transient,
    ) { room, libraryState, playlists, preferencesState, transientState ->
        MainUiState(
            room = room,
            tracks = libraryState.tracks,
            temporaryTrackIds = libraryState.temporaryTrackIds,
            storageSummary = libraryState.storageSummary,
            playlists = playlists,
            settingsLoaded = true,
            onboardingComplete = preferencesState.first,
            retentionPolicy = preferencesState.second,
            busy = transientState.busy,
            message = transientState.message,
            pendingM3uResolution = transientState.pendingM3uResolution,
            selectedPlaylist = transientState.selectedPlaylist,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun command(command: AppCommand) {
        UnisonRoomService.start(getApplication())
        container.roomCommandBus.trySend(command)
    }

    fun saveName(name: String) {
        viewModelScope.launch {
            container.settings.saveDisplayName(name)
            val identity = container.settings.ensureIdentity()
            container.roomStore.update { it.copy(localIdentity = identity) }
        }
    }

    fun importMusic(uris: List<Uri>, toRoom: Boolean) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy.value = true
            val retention = if (toRoom) RetentionPolicy.TEMPORARY_24_HOURS else RetentionPolicy.KEEP_IN_LIBRARY
            runCatching { container.importManager.importAudio(uris, retention) }
                .onSuccess { result ->
                    if (toRoom && result.tracks.isNotEmpty()) {
                        command(AppCommand.AddTracks(result.tracks.map { it.trackId }))
                    }
                    message.value = when {
                        result.tracks.isEmpty() -> result.errors.firstOrNull() ?: "Unison could not add this music"
                        result.errors.isNotEmpty() -> "Added ${result.tracks.size}; ${result.errors.size} could not be opened"
                        toRoom -> "Added ${result.tracks.size} song${if (result.tracks.size == 1) "" else "s"} to the room"
                        else -> "Added ${result.tracks.size} song${if (result.tracks.size == 1) "" else "s"}"
                    }
                }
                .onFailure { message.value = "Unison could not add this music" }
            busy.value = false
        }
    }

    fun importM3u(uri: Uri, toRoom: Boolean) {
        viewModelScope.launch {
            busy.value = true
            runCatching { container.importManager.importM3u(uri) }
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
            busy.value = false
        }
    }

    fun resolvePendingM3u(treeUri: Uri) {
        val pending = pendingM3uResolution.value ?: return
        viewModelScope.launch {
            busy.value = true
            runCatching {
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
            busy.value = false
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
            container.settings.setRetentionPolicy(policy)
            if (policy == RetentionPolicy.KEEP_IN_LIBRARY) {
                container.roomStore.state.value.snapshot
                    ?.queue
                    ?.asSequence()
                    ?.map { it.track.trackId }
                    ?.distinct()
                    ?.forEach { trackId ->
                        if (container.trackRepository.hasVerifiedSource(trackId)) {
                            container.trackRepository.keep(trackId)
                        }
                    }
            }
            message.value = if (policy == RetentionPolicy.KEEP_IN_LIBRARY) {
                "Received music will be kept"
            } else {
                "Received music will be removed after 24 hours"
            }
        }
    }

    fun keepTrack(trackId: TrackId) {
        viewModelScope.launch {
            runCatching { container.trackRepository.keep(trackId) }
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
            busy.value = true
            runCatching { container.trackRepository.clearTemporary(activeTrackIds) }
                .onSuccess { removed ->
                    message.value = when (removed) {
                        0 -> if (activeTrackIds.isEmpty()) "No temporary music to remove" else "Temporary music in the room was kept"
                        1 -> "Removed 1 temporary song"
                        else -> "Removed $removed temporary songs"
                    }
                }
                .onFailure { message.value = "Could not clear temporary music" }
            busy.value = false
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
            runCatching { container.trackRepository.deleteTemporary(trackId) }
                .onSuccess { message.value = "Temporary copy removed" }
                .onFailure { message.value = "Could not remove this song" }
        }
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val data = intent.data
        if (Intent.ACTION_VIEW == intent.action && data?.scheme == "unison") {
            parseJoinLink(data)?.let { (room, pin) -> command(AppCommand.JoinRoom(room, pin)) }
            return
        }
        val uris = when (intent.action) {
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> intent.readSharedUris()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) {
            val active = container.roomStore.state.value.snapshot != null
            if (uris.size == 1 && isM3u(intent.type, uris.single())) {
                importM3u(uris.single(), toRoom = active)
            } else {
                importMusic(uris, toRoom = active)
            }
        }
    }

    fun createPlaylist(name: String, trackIds: List<TrackId>) {
        if (trackIds.isEmpty()) {
            message.value = "Select at least one track"
            return
        }
        viewModelScope.launch {
            busy.value = true
            runCatching { container.playlistRepository.create(name, trackIds) }
                .onSuccess { message.value = "Playlist created" }
                .onFailure { message.value = "Could not create playlist" }
            busy.value = false
        }
    }

    fun openPlaylist(playlistId: String) {
        viewModelScope.launch {
            busy.value = true
            selectedPlaylist.value = container.playlistRepository.get(playlistId)
            busy.value = false
        }
    }

    fun closePlaylist() {
        selectedPlaylist.value = null
    }

    fun renamePlaylist(playlistId: String, name: String) {
        viewModelScope.launch {
            runCatching {
                container.playlistRepository.rename(playlistId, name)
                refreshSelectedPlaylist(playlistId)
            }.onFailure { message.value = "Could not rename playlist" }
        }
    }

    fun updatePlaylistTracks(playlistId: String, trackIds: List<TrackId>) {
        viewModelScope.launch {
            runCatching {
                container.playlistRepository.replaceTracks(playlistId, trackIds)
                refreshSelectedPlaylist(playlistId)
            }.onSuccess { message.value = "Playlist updated" }
                .onFailure { message.value = "Could not update playlist" }
        }
    }

    fun addTracksToPlaylist(playlistId: String, trackIds: List<TrackId>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                val detail = container.playlistRepository.get(playlistId) ?: error("Playlist not found")
                container.playlistRepository.replaceTracks(playlistId, detail.tracks.map { it.trackId } + trackIds)
                refreshSelectedPlaylist(playlistId)
            }.onSuccess { message.value = "Songs added" }
                .onFailure { message.value = "Could not update playlist" }
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            runCatching { container.playlistRepository.delete(playlistId) }
                .onSuccess {
                    if (selectedPlaylist.value?.playlistId == playlistId) selectedPlaylist.value = null
                    message.value = "Playlist deleted"
                }
                .onFailure { message.value = "Could not delete playlist" }
        }
    }

    fun addPlaylistToRoom(playlistId: String) {
        viewModelScope.launch {
            busy.value = true
            runCatching { container.playlistRepository.get(playlistId) }
                .onSuccess { detail ->
                    if (detail != null) command(AppCommand.AddTracks(detail.tracks.map { it.trackId }))
                }
                .onFailure { message.value = "Could not open playlist" }
            busy.value = false
        }
    }

    fun exportPlaylist(playlistId: String, destination: Uri) {
        viewModelScope.launch {
            busy.value = true
            runCatching {
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
            }.onSuccess { message.value = "Playlist exported" }
                .onFailure { message.value = "Could not export playlist" }
            busy.value = false
        }
    }

    fun showMessage(value: String) {
        message.value = value
    }

    fun clearNotice() {
        message.value = null
        container.roomStore.update { it.copy(errorMessage = null, statusMessage = null) }
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
            .appendQueryParameter("v", "1")
            .build().toString()
    }

    private suspend fun refreshSelectedPlaylist(playlistId: String) {
        if (selectedPlaylist.value?.playlistId == playlistId) {
            selectedPlaylist.value = container.playlistRepository.get(playlistId)
        }
    }

    private fun parseJoinLink(uri: Uri): Pair<DiscoveredRoom, String>? {
        val roomId = uri.getQueryParameter("roomId") ?: return null
        val host = uri.getQueryParameter("host") ?: return null
        val port = uri.getQueryParameter("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val pin = uri.getQueryParameter("pin")?.takeIf { it.length == 6 } ?: return null
        return DiscoveredRoom(
            serviceName = "QR",
            roomId = roomId,
            roomName = uri.getQueryParameter("name") ?: "Unison room",
            hostAddress = host,
            port = port,
            protocolVersion = uri.getQueryParameter("v")?.toIntOrNull() ?: 1,
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

    @Suppress("DEPRECATION")
    private fun Intent.readSharedUris(): List<Uri> {
        val fromExtras = if (android.os.Build.VERSION.SDK_INT >= 33) {
            buildList {
                getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)?.let(::add)
                addAll(getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty())
            }
        } else {
            buildList {
                getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let(::add)
                addAll(getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty())
            }
        }
        val fromClip = buildList {
            val value = clipData ?: return@buildList
            for (index in 0 until value.itemCount) value.getItemAt(index).uri?.let(::add)
        }
        return (fromExtras + fromClip + listOfNotNull(data)).distinct()
    }

    private companion object {
        val M3U_MIME_TYPES = setOf(
            "audio/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "application/x-mpegurl",
        )
    }
}
