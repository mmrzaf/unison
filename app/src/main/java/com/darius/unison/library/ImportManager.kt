package com.darius.unison.library

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
     * Imports an M3U/M3U8 as an interoperability format. URI/file entries are copied into Unison.
     * When [musicTreeUri] is supplied, relative references are resolved against that SAF tree.
     * Passing [existingPlaylistId] replaces the first-pass playlist while preserving source order.
     */
    suspend fun importM3u(
        uri: Uri,
        musicTreeUri: Uri? = null,
        existingPlaylistId: String? = null,
    ): M3uImportResult = withContext(Dispatchers.IO) {
        val parsed = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                total += read
                require(total <= M3uCodec.MAX_FILE_BYTES) { "M3U playlist is too large" }
                output.write(buffer, 0, read)
            }
            M3uCodec.parse(StringReader(output.toString(Charsets.UTF_8.name())))
        } ?: error("Unable to open M3U playlist")

        val baseDirectory = uri.takeIf { it.scheme == "file" }?.path?.let(::File)?.parentFile
        val treeIndex = musicTreeUri?.let(::buildTreeIndex)
        val resolved = mutableListOf<TrackDescriptor>()
        val unresolved = mutableListOf<M3uEntry>()

        parsed.entries.forEach { entry ->
            val descriptor = resolveEntry(entry, baseDirectory, treeIndex)
            if (descriptor != null) resolved += descriptor else unresolved += entry
        }

        val playlistName = uri.lastPathSegment
            ?.substringAfterLast('/')
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: "Imported playlist"
        val playlistId = if (existingPlaylistId == null) {
            playlistRepository.create(playlistName, resolved.map { it.trackId })
        } else {
            playlistRepository.replaceTracks(existingPlaylistId, resolved.map { it.trackId })
            existingPlaylistId
        }
        M3uImportResult(playlistId, resolved, unresolved)
    }

    private suspend fun resolveEntry(
        entry: M3uEntry,
        baseDirectory: File?,
        treeIndex: TreeIndex?,
    ): TrackDescriptor? {
        val reference = entry.reference.trim()
        if (reference.isEmpty()) return null

        val parsed = runCatching { reference.toUri() }.getOrNull()
        if (parsed != null && parsed.scheme in SUPPORTED_URI_SCHEMES) {
            return suspendResult { trackRepository.importUri(parsed) }.getOrNull()
        }

        val file = File(reference).let { candidate ->
            when {
                candidate.isAbsolute -> candidate
                baseDirectory != null -> File(baseDirectory, reference).canonicalFile
                else -> candidate
            }
        }
        if (file.isFile && file.canRead()) {
            return suspendResult { trackRepository.importUri(Uri.fromFile(file)) }.getOrNull()
        }

        val treeDocument = treeIndex?.resolve(reference)
        if (treeDocument != null) {
            return suspendResult { trackRepository.importUri(treeDocument.uri) }.getOrNull()
        }

        val filename = reference.replace('\\', '/').substringAfterLast('/').trim()
        return trackRepository.findByReference(
            fileName = filename,
            title = filename.substringBeforeLast('.'),
        )
    }

    private fun buildTreeIndex(treeUri: Uri): TreeIndex {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: error("Unable to read selected music folder")
        check(root.isDirectory && root.canRead()) { "Selected folder cannot be read" }

        val byPath = LinkedHashMap<String, DocumentFile>()
        val byName = LinkedHashMap<String, MutableList<DocumentFile>>()
        val queue = ArrayDeque<IndexedDirectory>()
        queue.add(IndexedDirectory(root, "", 0))
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_INDEXED_DOCUMENTS) {
            val current = queue.removeFirst()
            if (current.depth > MAX_TREE_DEPTH) continue
            val children = runCatching { current.document.listFiles() }.getOrDefault(emptyArray())
            for (child in children) {
                if (++visited > MAX_INDEXED_DOCUMENTS) break
                val name = child.name?.trim()?.takeIf(String::isNotEmpty) ?: continue
                val relativePath = if (current.relativePath.isEmpty()) name else "${current.relativePath}/$name"
                if (child.isDirectory) {
                    queue.add(IndexedDirectory(child, relativePath, current.depth + 1))
                } else if (child.isFile && child.canRead()) {
                    byPath[normalizePath(relativePath)] = child
                    byName.getOrPut(name.lowercase(Locale.US)) { mutableListOf() }.add(child)
                }
            }
        }
        return TreeIndex(byPath, byName)
    }

    private data class IndexedDirectory(
        val document: DocumentFile,
        val relativePath: String,
        val depth: Int,
    )

    private class TreeIndex(
        private val byPath: Map<String, DocumentFile>,
        private val byName: Map<String, List<DocumentFile>>,
    ) {
        fun resolve(rawReference: String): DocumentFile? {
            val normalized = normalizePath(rawReference)
            if (normalized.isEmpty() || normalized.startsWith("../") || "/../" in normalized) return null
            byPath[normalized]?.let { return it }

            val segments = normalized.split('/').filter(String::isNotBlank)
            for (start in 1 until segments.size) {
                byPath[segments.drop(start).joinToString("/")]?.let { return it }
            }
            return byName[segments.lastOrNull().orEmpty()]?.singleOrNull()
        }
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
        val SUPPORTED_URI_SCHEMES = setOf("content", "file")
        const val MAX_INDEXED_DOCUMENTS = 20_000
        const val MAX_TREE_DEPTH = 24
        const val IMPORT_WORKERS = 2
        const val IMPORT_BUFFER_SIZE = 8

        fun normalizePath(value: String): String = value
            .trim()
            .replace('\\', '/')
            .removePrefix("./")
            .trimStart('/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
            .joinToString("/")
            .lowercase(Locale.US)
    }
}

data class ImportResult(
    val tracks: List<TrackDescriptor>,
    val errors: List<String>,
)

data class M3uImportResult(
    val playlistId: String,
    val tracks: List<TrackDescriptor>,
    val unresolved: List<M3uEntry>,
)
