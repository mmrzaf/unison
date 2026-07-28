package com.darius.unison.ui

import android.app.Application
import android.net.Uri
import com.darius.unison.app.AppContainer
import com.darius.unison.library.M3uCodec
import com.darius.unison.library.M3uEntry
import com.darius.unison.library.PlaylistDetail
import com.darius.unison.model.TrackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Playlist CRUD/export state holder. MainViewModel only wires its flow into the screen state. */
internal class PlaylistActions(
    private val application: Application,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val activeOperationCount: MutableStateFlow<Int>,
    private val message: MutableStateFlow<String?>,
    private val addTracksToRoom: (List<TrackId>) -> Unit,
) {
    private val _selectedPlaylist = MutableStateFlow<PlaylistDetail?>(null)
    val selectedPlaylist = _selectedPlaylist.asStateFlow()

    fun create(name: String, trackIds: List<TrackId>) {
        scope.launch {
            withBusyOperation { userResult { container.playlistRepository.create(name, trackIds) } }
                .onSuccess { message.value = "Playlist created" }
                .onFailure { message.value = "Could not create playlist" }
        }
    }

    fun open(playlistId: String) {
        scope.launch {
            withBusyOperation { userResult { container.playlistRepository.get(playlistId) } }
                .onSuccess { playlist ->
                    _selectedPlaylist.value = playlist
                    if (playlist == null) message.value = "This playlist is no longer available"
                }
                .onFailure { message.value = "Could not open playlist" }
        }
    }

    fun close() {
        _selectedPlaylist.value = null
    }

    fun rename(playlistId: String, name: String) {
        scope.launch {
            userResult {
                container.playlistRepository.rename(playlistId, name)
                refreshSelected(playlistId)
            }.onFailure { message.value = "Could not rename playlist" }
        }
    }

    fun replaceTracks(playlistId: String, trackIds: List<TrackId>) {
        scope.launch {
            userResult {
                container.playlistRepository.replaceTracks(playlistId, trackIds)
                refreshSelected(playlistId)
            }.onSuccess { message.value = "Playlist updated" }
                .onFailure { message.value = "Could not update playlist" }
        }
    }

    fun addTracks(playlistId: String, trackIds: List<TrackId>) {
        if (trackIds.isEmpty()) return
        scope.launch {
            userResult {
                val detail = container.playlistRepository.get(playlistId) ?: error("Playlist not found")
                container.playlistRepository.replaceTracks(playlistId, detail.tracks.map { it.trackId } + trackIds)
                refreshSelected(playlistId)
            }.onSuccess { message.value = "Songs added" }
                .onFailure { message.value = "Could not update playlist" }
        }
    }

    fun delete(playlistId: String) {
        scope.launch {
            userResult { container.playlistRepository.delete(playlistId) }
                .onSuccess {
                    if (_selectedPlaylist.value?.playlistId == playlistId) _selectedPlaylist.value = null
                    message.value = "Playlist deleted"
                }
                .onFailure { message.value = "Could not delete playlist" }
        }
    }

    fun addToRoom(playlistId: String) {
        scope.launch {
            withBusyOperation { userResult { container.playlistRepository.get(playlistId) } }
                .onSuccess { detail ->
                    when {
                        detail == null -> message.value = "This playlist is no longer available"
                        detail.tracks.isEmpty() -> message.value = "This playlist is empty"
                        else -> addTracksToRoom(detail.tracks.map { it.trackId })
                    }
                }
                .onFailure { message.value = "Could not open playlist" }
        }
    }

    fun export(playlistId: String, destination: Uri) {
        scope.launch {
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
                    application.contentResolver.openOutputStream(destination, "wt")
                        ?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                        ?: error("Could not create export file")
                }
            }.onSuccess { message.value = "Playlist exported" }
                .onFailure { message.value = "Could not export playlist" }
        }
    }

    private suspend fun refreshSelected(playlistId: String) {
        if (_selectedPlaylist.value?.playlistId == playlistId) {
            _selectedPlaylist.value = container.playlistRepository.get(playlistId)
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

    private suspend fun <T> userResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
