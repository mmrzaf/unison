package com.darius.unison.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.IntentCompat
import com.darius.unison.app.AppContainer
import com.darius.unison.library.LibraryImportProgress
import com.darius.unison.library.LibraryImportStage
import com.darius.unison.library.M3uResolutionPolicy
import com.darius.unison.library.M3uUnresolvedEntry
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackId
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Owns import jobs, progress, shared-file routing, and unresolved M3U decisions. */
internal class LibraryImportCoordinator(
    private val application: Application,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val activeOperationCount: MutableStateFlow<Int>,
    private val message: MutableStateFlow<String?>,
    private val roomActions: RoomSessionActions,
) {
    private val _importProgress = MutableStateFlow<ImportProgress?>(null)
    val importProgress = _importProgress.asStateFlow()
    private val _pendingM3uResolution = MutableStateFlow<PendingM3uResolution?>(null)
    val pendingM3uResolution = _pendingM3uResolution.asStateFlow()
    private val _pendingMusicImport = MutableStateFlow<PendingMusicImport?>(null)
    val pendingMusicImport = _pendingMusicImport.asStateFlow()
    private var importJob: Job? = null

    fun importMusic(uris: List<Uri>, toRoom: Boolean) {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return
        _pendingMusicImport.value =
            PendingMusicImport(
                uris = uniqueUris,
                isM3u = false,
                defaultSaveToLibrary = !toRoom,
                defaultAddToRoom = toRoom,
                sharedFromAnotherApp = false,
            )
    }

    fun cancel() {
        val job = importJob ?: return
        _importProgress.update { current ->
            current?.copy(headline = "Cancelling…", detail = "Stopping folder and metadata work")
        }
        message.value = "Import cancelled"
        job.cancel()
    }

    fun importM3u(uri: Uri, toRoom: Boolean) {
        _pendingMusicImport.value =
            PendingMusicImport(
                uris = listOf(uri),
                isM3u = true,
                defaultSaveToLibrary = true,
                defaultAddToRoom = toRoom,
                sharedFromAnotherApp = false,
            )
    }

    fun resolvePendingM3u(treeUri: Uri) {
        val pending = _pendingM3uResolution.value ?: return
        startM3uImport(
            pending.sourceUri,
            pending.toRoom,
            treeUri,
            pending.playlistId,
            pending.manualSelections,
        )
    }

    fun choosePendingM3uCandidate(entryIndex: Int, trackId: TrackId) {
        val pending = _pendingM3uResolution.value ?: return
        val ambiguity = pending.ambiguous.firstOrNull { it.entryIndex == entryIndex } ?: return
        val selected = ambiguity.candidates.firstOrNull { it.trackId == trackId } ?: return
        scope.launch {
            withBusyOperation {
                    userResult {
                        val resolved =
                            checkNotNull(
                                M3uResolutionPolicy.choose(
                                    pending.resolvedEntries,
                                    ambiguity,
                                    selected.trackId,
                                )
                            )
                        container.playlistRepository.replaceTracks(
                            pending.playlistId,
                            M3uResolutionPolicy.orderedTrackIds(resolved),
                        )
                        pending.copy(
                            resolvedEntries = resolved,
                            ambiguous = pending.ambiguous.filterNot { it.entryIndex == entryIndex },
                            manualSelections = pending.manualSelections + (entryIndex to trackId),
                        )
                    }
                }
                .onSuccess(::updatePendingM3u)
                .onFailure { message.value = "Could not update this playlist match" }
        }
    }

    fun skipPendingM3uAmbiguity(entryIndex: Int) {
        val pending = _pendingM3uResolution.value ?: return
        val ambiguity = pending.ambiguous.firstOrNull { it.entryIndex == entryIndex } ?: return
        updatePendingM3u(
            pending.copy(
                ambiguous = pending.ambiguous.filterNot { it.entryIndex == entryIndex },
                unresolved =
                    (pending.unresolved +
                            M3uUnresolvedEntry(
                                entryIndex = entryIndex,
                                entry = ambiguity.entry,
                                reason = "Ambiguous match skipped",
                            ))
                        .sortedBy(M3uUnresolvedEntry::entryIndex),
            )
        )
    }

    fun finishPendingM3uWithoutFolder() {
        val pending = _pendingM3uResolution.value ?: return
        _pendingM3uResolution.value = null
        if (pending.toRoom && pending.availableTracks.isNotEmpty()) {
            roomActions.addTracksToRoom(pending.availableTracks.map { it.trackId })
        }
        val skipped = pending.unresolved.size + pending.ambiguous.size
        message.value =
            when {
                pending.availableTracks.isEmpty() -> "No playlist tracks were available"
                skipped > 0 ->
                    "Imported ${pending.availableTracks.size}; skipped $skipped unavailable"
                else ->
                    "Imported ${pending.availableTracks.size} track${if (pending.availableTracks.size == 1) "" else "s"}"
            }
    }

    fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uris =
            when (intent.action) {
                Intent.ACTION_SEND,
                Intent.ACTION_SEND_MULTIPLE -> intent.readSharedUris()
                else -> emptyList()
            }
        if (uris.isEmpty()) return
        val isPlaylist = uris.size == 1 && isM3u(intent.type, uris.single())
        _pendingMusicImport.value =
            PendingMusicImport(
                uris = uris,
                isM3u = isPlaylist,
                defaultSaveToLibrary = true,
                defaultAddToRoom = false,
                sharedFromAnotherApp = true,
            )
    }

    fun resolvePendingImport(destination: MusicDestination?) {
        val pending = _pendingMusicImport.value ?: return
        _pendingMusicImport.value = null
        if (destination == null || !destination.hasDestination) return
        if (pending.isM3u) {
            startM3uImport(
                sourceUri = pending.uris.single(),
                toRoom = destination.addToRoom,
                treeUri = null,
                existingPlaylistId = null,
                manualSelections = emptyMap(),
            )
            return
        }
        startAudioImport(
            uris = pending.uris,
            destination = destination,
        )
    }

    private fun startM3uImport(
        sourceUri: Uri,
        toRoom: Boolean,
        treeUri: Uri?,
        existingPlaylistId: String?,
        manualSelections: Map<Int, TrackId>,
    ) {
        if (importJob?.isActive == true) {
            message.value = "Finish or cancel the current import first"
            return
        }
        importJob = scope.launch {
            activeOperationCount.update { it + 1 }
            _importProgress.value = ImportProgress(0, 0, "Reading playlist")
            try {
                val block: suspend () -> com.darius.unison.library.M3uImportResult = {
                    container.importManager.importM3u(
                        uri = sourceUri,
                        musicTreeUri = treeUri,
                        existingPlaylistId = existingPlaylistId,
                        manualSelections = manualSelections,
                        onProgress = { progress ->
                            _importProgress.value = progress.toUiProgress()
                        },
                    )
                }
                val result =
                    if (treeUri == null) block()
                    else {
                        container.persistedUriPermissions.withTemporaryReadPermission(
                            treeUri,
                            block,
                        )
                    }
                updatePendingM3u(
                    PendingM3uResolution(
                        sourceUri = sourceUri,
                        playlistId = result.playlistId,
                        toRoom = toRoom,
                        resolvedEntries = result.resolvedEntries,
                        unresolved = result.unresolved,
                        ambiguous = result.ambiguous,
                        manualSelections = manualSelections,
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value =
                    if (treeUri == null) {
                        "Unison could not import this playlist"
                    } else {
                        "Unison could not read this folder"
                    }
            } finally {
                _importProgress.value = null
                activeOperationCount.update { (it - 1).coerceAtLeast(0) }
                importJob = null
            }
        }
    }

    private fun updatePendingM3u(pending: PendingM3uResolution) {
        if (pending.ambiguous.isEmpty() && pending.unresolved.isEmpty()) {
            _pendingM3uResolution.value = null
            if (pending.toRoom && pending.availableTracks.isNotEmpty()) {
                roomActions.addTracksToRoom(pending.availableTracks.map { it.trackId })
            }
            message.value =
                "Imported ${pending.availableTracks.size} track${if (pending.availableTracks.size == 1) "" else "s"}"
        } else {
            _pendingM3uResolution.value = pending
            message.value =
                when {
                    pending.ambiguous.isNotEmpty() ->
                        "${pending.ambiguous.size} playlist match${if (pending.ambiguous.size == 1) " needs" else "es need"} review"
                    else ->
                        "${pending.unresolved.size} playlist track${if (pending.unresolved.size == 1) " needs" else "s need"} their music folder"
                }
        }
    }

    private fun startAudioImport(
        uris: List<Uri>,
        destination: MusicDestination,
    ) {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return
        if (importJob?.isActive == true) {
            message.value = "Finish or cancel the current import first"
            return
        }
        importJob = scope.launch {
            activeOperationCount.update { it + 1 }
            _importProgress.value = ImportProgress(0, uniqueUris.size)
            try {
                val retention =
                    if (destination.keepsInLibrary) {
                        RetentionPolicy.KEEP_IN_LIBRARY
                    } else {
                        RetentionPolicy.TEMPORARY_24_HOURS
                    }
                val result =
                    container.importManager.importAudio(uniqueUris, retention) { completed, total ->
                        _importProgress.value = ImportProgress(completed, total)
                    }
                val trackIds = result.tracks.map { it.trackId }
                if (destination.addToRoom && trackIds.isNotEmpty()) {
                    roomActions.addTracksToRoom(trackIds)
                }

                var playlistFailures = 0
                if (trackIds.isNotEmpty()) {
                    destination.playlistIds.distinct().forEach { playlistId ->
                        try {
                            container.playlistRepository.appendTracks(playlistId, trackIds)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            playlistFailures += 1
                        }
                    }
                    destination.newPlaylistName?.trim()?.takeIf(String::isNotEmpty)?.let { name ->
                        try {
                            container.playlistRepository.create(name, trackIds)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            playlistFailures += 1
                        }
                    }
                }

                message.value =
                    importCompletionMessage(
                        importedCount = trackIds.size,
                        importErrorCount = result.errors.size,
                        playlistFailures = playlistFailures,
                        destination = destination,
                        firstImportError = result.errors.firstOrNull(),
                    )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                message.value = "Unison could not add this music"
            } finally {
                _importProgress.value = null
                activeOperationCount.update { (it - 1).coerceAtLeast(0) }
                importJob = null
            }
        }
    }

    private fun importCompletionMessage(
        importedCount: Int,
        importErrorCount: Int,
        playlistFailures: Int,
        destination: MusicDestination,
        firstImportError: String?,
    ): String {
        if (importedCount == 0) return firstImportError ?: "Unison could not add this music"
        if (playlistFailures > 0) return "Added $importedCount; some playlists could not be updated"
        if (importErrorCount > 0)
            return "Added $importedCount; $importErrorCount could not be opened"

        val places = buildList {
            if (destination.keepsInLibrary) add("your library")
            val playlistCount =
                destination.playlistIds.size +
                    if (destination.newPlaylistName.isNullOrBlank()) 0 else 1
            if (playlistCount == 1) add("a playlist")
            else if (playlistCount > 1) add("$playlistCount playlists")
            if (destination.addToRoom) add("the room")
        }
        val suffix =
            when (places.size) {
                0 -> ""
                1 -> places.single()
                2 -> places.joinToString(" and ")
                else -> places.dropLast(1).joinToString(", ") + ", and " + places.last()
            }
        return if (suffix.isBlank()) {
            "Added $importedCount ${if (importedCount == 1) "song" else "songs"}"
        } else {
            "Added $importedCount ${if (importedCount == 1) "song" else "songs"} to $suffix"
        }
    }

    private fun LibraryImportProgress.toUiProgress(): ImportProgress {
        val headline =
            when (stage) {
                LibraryImportStage.READING_PLAYLIST -> "Reading playlist"
                LibraryImportStage.INDEXING_FOLDER -> "Scanning music folder"
                LibraryImportStage.RESOLVING_ENTRIES -> "Matching playlist music"
                LibraryImportStage.IMPORTING_AUDIO -> "Importing playlist music"
                LibraryImportStage.FINALIZING -> "Saving playlist"
            }
        val detail =
            when (stage) {
                LibraryImportStage.INDEXING_FOLDER ->
                    buildString {
                        append("$documentsScanned documents scanned")
                        currentDirectory?.takeIf(String::isNotBlank)?.let {
                            append(" • ").append(it.takeLast(80))
                        }
                        append(" • ").append(elapsedMs / 1_000L).append('s')
                    }
                LibraryImportStage.RESOLVING_ENTRIES,
                LibraryImportStage.IMPORTING_AUDIO,
                LibraryImportStage.FINALIZING ->
                    "$tracksResolved matched • $unresolvedEntries missing • $ambiguousEntries ambiguous • ${elapsedMs / 1_000L}s"
                LibraryImportStage.READING_PLAYLIST -> null
            }
        return ImportProgress(
            completed = playlistEntriesProcessed,
            total = totalPlaylistEntries,
            headline = headline,
            detail = detail,
        )
    }

    private fun isM3u(mimeType: String?, uri: Uri): Boolean {
        if (mimeType in M3U_MIME_TYPES) return true
        val name =
            runCatching {
                    application.contentResolver
                        .query(
                            uri,
                            arrayOf(OpenableColumns.DISPLAY_NAME),
                            null,
                            null,
                            null,
                        )
                        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                }
                .getOrNull()
        return name?.lowercase(Locale.ROOT)?.let { it.endsWith(".m3u") || it.endsWith(".m3u8") } ==
            true
    }

    private fun Intent.readSharedUris(): List<Uri> {
        val fromExtras =
            when (action) {
                Intent.ACTION_SEND,
                Intent.ACTION_SEND_MULTIPLE ->
                    // Some senders incorrectly label a single Uri as ACTION_SEND_MULTIPLE.
                    // Use type-safe compatibility accessors for both valid representations.
                    listOfNotNull(
                            IntentCompat.getParcelableExtra(
                                this,
                                Intent.EXTRA_STREAM,
                                Uri::class.java,
                            )
                        )
                        .ifEmpty {
                            IntentCompat.getParcelableArrayListExtra(
                                    this,
                                    Intent.EXTRA_STREAM,
                                    Uri::class.java,
                                )
                                .orEmpty()
                        }

                else -> emptyList()
            }
        val fromClip = buildList {
            val value = clipData ?: return@buildList
            for (index in 0 until value.itemCount) value.getItemAt(index).uri?.let(::add)
        }
        return (fromExtras + fromClip + listOfNotNull(data)).distinct()
    }

    private suspend fun <T> withBusyOperation(block: suspend () -> T): T {
        activeOperationCount.update { it + 1 }
        return try {
            block()
        } finally {
            activeOperationCount.update { (it - 1).coerceAtLeast(0) }
        }
    }

    private suspend fun <T> userResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }

    private companion object {
        val M3U_MIME_TYPES =
            setOf(
                "audio/x-mpegurl",
                "application/vnd.apple.mpegurl",
                "application/x-mpegurl",
            )
    }
}
