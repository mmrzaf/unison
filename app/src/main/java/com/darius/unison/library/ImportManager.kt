package com.darius.unison.library

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.util.ArrayDeque
import java.util.Locale

class ImportManager(
    private val context: Context,
    private val trackRepository: TrackRepository,
    private val playlistRepository: PlaylistRepository,
) {
    suspend fun importAudio(
        uris: List<Uri>,
        retentionPolicy: RetentionPolicy = RetentionPolicy.KEEP_IN_LIBRARY,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): ImportResult = coroutineScope {
        val uniqueUris = uris.distinct()
        if (uniqueUris.isEmpty()) return@coroutineScope ImportResult(emptyList(), emptyList())
        val jobs = Channel<IndexedValue<Uri>>(capacity = IMPORT_BUFFER_SIZE)
        val progressEvents = Channel<Unit>(capacity = IMPORT_BUFFER_SIZE)
        val results = arrayOfNulls<Result<TrackDescriptor>>(uniqueUris.size)
        onProgress(0, uniqueUris.size)
        val progressCollector = launch {
            repeat(uniqueUris.size) { completed ->
                progressEvents.receive()
                onProgress(completed + 1, uniqueUris.size)
            }
        }
        val producer = launch {
            try {
                uniqueUris.forEachIndexed { index, uri -> jobs.send(IndexedValue(index, uri)) }
            } finally {
                jobs.close()
            }
        }
        val workers = List(IMPORT_WORKERS) {
            launch(Dispatchers.IO) {
                for ((index, uri) in jobs) {
                    results[index] = try {
                        Result.success(trackRepository.importUri(uri, retentionPolicy))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        Result.failure(error)
                    }
                    progressEvents.send(Unit)
                }
            }
        }
        producer.join()
        workers.joinAll()
        progressCollector.join()
        progressEvents.close()
        val completedResults = results.filterNotNull()
        ImportResult(
            tracks = completedResults.mapNotNull(Result<TrackDescriptor>::getOrNull),
            errors = completedResults.mapNotNull { it.exceptionOrNull()?.toUserMessage() },
        )
    }

    /**
     * Imports an M3U/M3U8 while preserving source order. Ambiguous metadata matches are returned to
     * the caller rather than silently choosing one. [manualSelections] contains explicit choices
     * made by the user for entry indices from a previous pass.
     */
    suspend fun importM3u(
        uri: Uri,
        musicTreeUri: Uri? = null,
        existingPlaylistId: String? = null,
        manualSelections: Map<Int, TrackId> = emptyMap(),
        onProgress: suspend (LibraryImportProgress) -> Unit = {},
    ): M3uImportResult = withContext(Dispatchers.IO) {
        val startedAtNs = System.nanoTime()
        suspend fun report(
            stage: LibraryImportStage,
            documentsScanned: Int = 0,
            processed: Int = 0,
            total: Int = 0,
            resolved: Int = 0,
            unresolved: Int = 0,
            ambiguous: Int = 0,
            currentDirectory: String? = null,
        ) {
            onProgress(
                LibraryImportProgress(
                    stage = stage,
                    documentsScanned = documentsScanned,
                    playlistEntriesProcessed = processed,
                    totalPlaylistEntries = total,
                    tracksResolved = resolved,
                    unresolvedEntries = unresolved,
                    ambiguousEntries = ambiguous,
                    currentDirectory = currentDirectory,
                    elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000L,
                )
            )
        }

        report(LibraryImportStage.READING_PLAYLIST)
        val parsed = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var totalBytes = 0
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                totalBytes += read
                require(totalBytes <= M3uCodec.MAX_FILE_BYTES) { "M3U playlist is too large" }
                output.write(buffer, 0, read)
            }
            M3uCodec.parse(StringReader(output.toString(Charsets.UTF_8.name())))
        } ?: error("Unable to open M3U playlist")

        val baseDirectory = uri.takeIf { it.scheme == "file" }?.path?.let(::File)?.parentFile
        var indexedDocuments = 0
        val treeIndex = musicTreeUri?.let { treeUri ->
            buildTreeIndex(treeUri) { scanned, directory ->
                indexedDocuments = scanned
                report(
                    stage = LibraryImportStage.INDEXING_FOLDER,
                    documentsScanned = scanned,
                    total = parsed.entries.size,
                    currentDirectory = directory,
                )
            }
        }

        val resolved = mutableListOf<M3uResolvedEntry>()
        val unresolved = mutableListOf<M3uUnresolvedEntry>()
        val ambiguous = mutableListOf<M3uAmbiguousEntry>()
        report(
            stage = LibraryImportStage.RESOLVING_ENTRIES,
            documentsScanned = indexedDocuments,
            total = parsed.entries.size,
        )
        parsed.entries.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            when (val resolution = resolveEntry(entry, baseDirectory, treeIndex, manualSelections[index])) {
                is EntryResolution.Resolved -> resolved += M3uResolvedEntry(index, entry, resolution.track)
                is EntryResolution.Ambiguous -> ambiguous += M3uAmbiguousEntry(index, entry, resolution.candidates)
                is EntryResolution.Unresolved -> unresolved += M3uUnresolvedEntry(index, entry, resolution.reason)
            }
            report(
                stage = LibraryImportStage.RESOLVING_ENTRIES,
                documentsScanned = indexedDocuments,
                processed = index + 1,
                total = parsed.entries.size,
                resolved = resolved.size,
                unresolved = unresolved.size,
                ambiguous = ambiguous.size,
            )
            if (index % CANCELLATION_YIELD_INTERVAL == 0) yield()
        }

        report(
            stage = LibraryImportStage.FINALIZING,
            documentsScanned = indexedDocuments,
            processed = parsed.entries.size,
            total = parsed.entries.size,
            resolved = resolved.size,
            unresolved = unresolved.size,
            ambiguous = ambiguous.size,
        )
        val orderedTracks = resolved.sortedBy(M3uResolvedEntry::entryIndex).map { it.track.trackId }
        val playlistName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "Imported playlist"
        val playlistId = if (existingPlaylistId == null) {
            playlistRepository.create(playlistName, orderedTracks)
        } else {
            playlistRepository.replaceTracks(existingPlaylistId, orderedTracks)
            existingPlaylistId
        }
        M3uImportResult(
            playlistId = playlistId,
            resolvedEntries = resolved.sortedBy(M3uResolvedEntry::entryIndex),
            unresolved = unresolved.sortedBy(M3uUnresolvedEntry::entryIndex),
            ambiguous = ambiguous.sortedBy(M3uAmbiguousEntry::entryIndex),
        )
    }

    private suspend fun resolveEntry(
        entry: M3uEntry,
        baseDirectory: File?,
        treeIndex: TreeIndex?,
        manualSelection: TrackId?,
    ): EntryResolution {
        manualSelection?.let { selected ->
            trackRepository.get(selected)?.let { return EntryResolution.Resolved(it) }
        }

        val reference = entry.reference.trim()
        if (reference.isEmpty()) return EntryResolution.Unresolved("Empty playlist reference")

        val parsed = runCatching { reference.toUri() }.getOrNull()
        when (parsed?.scheme?.lowercase(Locale.US)) {
            "content" -> suspendResult { trackRepository.importUri(parsed) }.getOrNull()
                ?.let { return EntryResolution.Resolved(it) }

            "file" -> safeFileFromReference(parsed.path.orEmpty(), baseDirectory)?.let { file ->
                suspendResult { trackRepository.importUri(Uri.fromFile(file)) }.getOrNull()
                    ?.let { return EntryResolution.Resolved(it) }
            }
        }

        safeFileFromReference(reference, baseDirectory)?.let { file ->
            suspendResult { trackRepository.importUri(Uri.fromFile(file)) }.getOrNull()
                ?.let { return EntryResolution.Resolved(it) }
        }

        when (val treeResolution = treeIndex?.resolve(reference)) {
            is TreeResolution.Found -> suspendResult {
                trackRepository.importUri(treeResolution.document.uri)
            }.getOrNull()?.let { return EntryResolution.Resolved(it) }

            is TreeResolution.Ambiguous -> return EntryResolution.Unresolved(
                "Multiple files in the selected folder match this path"
            )

            is TreeResolution.Rejected -> return EntryResolution.Unresolved(treeResolution.reason)
            null, TreeResolution.Missing -> Unit
        }

        val pathDecision = PlaylistPathPolicy.evaluate(reference)
        val fileName = when (pathDecision) {
            is PlaylistPathPolicy.Decision.Valid -> pathDecision.fileName
            is PlaylistPathPolicy.Decision.Rejected -> reference.replace('\\', '/').substringAfterLast('/').trim()
        }
        val title = entry.displayTitle?.trim()?.ifBlank { null } ?: fileName.substringBeforeLast('.')
        val candidates = trackRepository.findReferenceCandidates(fileName, title)
        val decision = PlaylistMatchPolicy.decide(
            fileName = fileName,
            displayTitle = entry.displayTitle ?: title,
            durationSeconds = entry.durationSeconds,
            candidates = candidates.map { descriptor ->
                PlaylistMatchPolicy.Candidate(
                    trackId = descriptor.trackId.value,
                    fileName = descriptor.originalFileName,
                    title = descriptor.title,
                    durationMs = descriptor.durationMs,
                    sizeBytes = descriptor.sizeBytes,
                )
            },
        )
        return when (decision) {
            is PlaylistMatchPolicy.Decision.Unique -> candidates
                .firstOrNull { it.trackId.value == decision.candidate.trackId }
                ?.let(EntryResolution::Resolved)
                ?: EntryResolution.Unresolved("Playlist track is unavailable")

            is PlaylistMatchPolicy.Decision.Ambiguous -> {
                val ids = decision.candidates.mapTo(hashSetOf()) { it.trackId }
                EntryResolution.Ambiguous(candidates.filter { it.trackId.value in ids })
            }

            PlaylistMatchPolicy.Decision.Missing -> EntryResolution.Unresolved("Playlist track is unavailable")
        }
    }

    private fun safeFileFromReference(reference: String, baseDirectory: File?): File? {
        val base = baseDirectory?.canonicalFile ?: return null
        val decision = PlaylistPathPolicy.evaluate(reference)
        val relative = (decision as? PlaylistPathPolicy.Decision.Valid)?.relativePath ?: return null
        val candidate = File(base, relative).canonicalFile
        val basePrefix = base.path.trimEnd(File.separatorChar) + File.separator
        return candidate.takeIf { it.path.startsWith(basePrefix) && it.isFile && it.canRead() }
    }

    private suspend fun buildTreeIndex(
        treeUri: Uri,
        onProgress: suspend (documentsScanned: Int, currentDirectory: String?) -> Unit,
    ): TreeIndex {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Unable to read selected music folder")
        check(root.isDirectory && root.canRead()) { "Selected folder cannot be read" }

        val byPath = LinkedHashMap<String, MutableList<DocumentFile>>()
        val byName = LinkedHashMap<String, MutableList<DocumentFile>>()
        val queue = ArrayDeque<IndexedDirectory>()
        queue.add(IndexedDirectory(root, "", 0))
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_INDEXED_DOCUMENTS) {
            currentCoroutineContext().ensureActive()
            val current = queue.removeFirst()
            if (current.depth > MAX_TREE_DEPTH) continue
            onProgress(visited, current.relativePath.ifEmpty { root.name })
            val children = runCatching { current.document.listFiles() }.getOrDefault(emptyArray())
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (++visited > MAX_INDEXED_DOCUMENTS) break
                val name = child.name?.trim()?.takeIf(String::isNotEmpty) ?: continue
                val relativePath = if (current.relativePath.isEmpty()) name else "${current.relativePath}/$name"
                if (child.isDirectory) {
                    queue.add(IndexedDirectory(child, relativePath, current.depth + 1))
                } else if (child.isFile && child.canRead()) {
                    val normalized = (PlaylistPathPolicy.evaluate(relativePath) as? PlaylistPathPolicy.Decision.Valid)
                        ?.normalizedPath ?: continue
                    byPath.getOrPut(normalized) { mutableListOf() }.add(child)
                    byName.getOrPut(name.lowercase(Locale.US)) { mutableListOf() }.add(child)
                }
                if (visited % CANCELLATION_YIELD_INTERVAL == 0) {
                    onProgress(visited, current.relativePath)
                    yield()
                }
            }
        }
        onProgress(visited, null)
        return TreeIndex(byPath, byName)
    }

    private data class IndexedDirectory(
        val document: DocumentFile,
        val relativePath: String,
        val depth: Int,
    )

    private class TreeIndex(
        private val byPath: Map<String, List<DocumentFile>>,
        private val byName: Map<String, List<DocumentFile>>,
    ) {
        fun resolve(rawReference: String): TreeResolution {
            val decision = PlaylistPathPolicy.evaluate(rawReference)
            val valid = decision as? PlaylistPathPolicy.Decision.Valid
                ?: return TreeResolution.Rejected("Unsafe or unsupported playlist path")
            when (val exact = PlaylistTreeMatchPolicy.decide(
                byPath[valid.normalizedPath].orEmpty(),
                identity = { it.uri },
            )) {
                is PlaylistTreeMatchPolicy.Decision.Found -> return TreeResolution.Found(exact.value)
                is PlaylistTreeMatchPolicy.Decision.Ambiguous -> return TreeResolution.Ambiguous(exact.count)
                PlaylistTreeMatchPolicy.Decision.Missing -> Unit
            }

            val suffix = "/${valid.normalizedPath}"
            val suffixMatches = byPath.asSequence()
                .filter { (path, _) -> path.endsWith(suffix) }
                .flatMap { it.value.asSequence() }
                .toList()
            when (val suffixMatch = PlaylistTreeMatchPolicy.decide(suffixMatches, identity = { it.uri })) {
                is PlaylistTreeMatchPolicy.Decision.Found -> return TreeResolution.Found(suffixMatch.value)
                is PlaylistTreeMatchPolicy.Decision.Ambiguous -> return TreeResolution.Ambiguous(suffixMatch.count)
                PlaylistTreeMatchPolicy.Decision.Missing -> Unit
            }

            return when (val nameMatch = PlaylistTreeMatchPolicy.decide(
                byName[valid.fileName].orEmpty(),
                identity = { it.uri },
            )) {
                is PlaylistTreeMatchPolicy.Decision.Found -> TreeResolution.Found(nameMatch.value)
                is PlaylistTreeMatchPolicy.Decision.Ambiguous -> TreeResolution.Ambiguous(nameMatch.count)
                PlaylistTreeMatchPolicy.Decision.Missing -> TreeResolution.Missing
            }
        }
    }

    private sealed interface TreeResolution {
        data class Found(val document: DocumentFile) : TreeResolution
        data class Ambiguous(val count: Int) : TreeResolution
        data class Rejected(val reason: String) : TreeResolution
        data object Missing : TreeResolution
    }

    private sealed interface EntryResolution {
        data class Resolved(val track: TrackDescriptor) : EntryResolution
        data class Ambiguous(val candidates: List<TrackDescriptor>) : EntryResolution
        data class Unresolved(val reason: String) : EntryResolution
    }

    private suspend fun <T> suspendResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    private fun Throwable.toUserMessage(): String = when {
        this is SecurityException -> "Unison could not read this file"
        message?.contains("larger than", ignoreCase = true) == true -> "This audio file is too large"
        message?.contains("storage", ignoreCase = true) == true ||
            message?.contains("space", ignoreCase = true) == true -> "Not enough storage space"

        message?.contains("not recognized as audio", ignoreCase = true) == true -> "This file is not supported audio"
        message?.contains("open", ignoreCase = true) == true -> "Unison could not open this file"
        else -> "Unison could not add this music"
    }

    private companion object {
        const val MAX_INDEXED_DOCUMENTS = 20_000
        const val MAX_TREE_DEPTH = 24
        const val IMPORT_WORKERS = 2
        const val IMPORT_BUFFER_SIZE = 8
        const val CANCELLATION_YIELD_INTERVAL = 32
    }
}

data class ImportResult(
    val tracks: List<TrackDescriptor>,
    val errors: List<String>,
)
