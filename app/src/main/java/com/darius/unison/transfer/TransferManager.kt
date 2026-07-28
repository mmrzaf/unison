package com.darius.unison.transfer

import com.darius.unison.library.TrackRepository
import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.RetentionPolicy
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import com.darius.unison.network.NetworkAddressPolicy
import com.darius.unison.protocol.ChannelType
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.FileRequest
import com.darius.unison.protocol.FileResponseHeader
import com.darius.unison.protocol.FileResponseStatus
import com.darius.unison.protocol.FileWireCodec
import com.darius.unison.protocol.HandshakeCodec
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.util.DiagnosticLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class TransferManager(
    private val localIdentity: LocalIdentity,
    private val listeningPort: () -> Int,
    private val appVersion: String,
    private val trackRepository: TrackRepository,
    private val fileStore: ManagedFileStore,
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val retentionPolicyProvider: suspend () -> RetentionPolicy,
    private val onProgress: (TransferProgress) -> Unit,
    private val onCompleted: suspend (TrackDescriptor) -> Unit,
    private val onFailed: suspend (TrackId, PeerId?, String) -> Unit,
) {
    private data class Authorization(
        val trackId: TrackId,
        val destinationPeerId: PeerId,
        val expiresAtElapsedMs: Long,
    )

    private val authorizations = ConcurrentHashMap<String, Authorization>()
    private val outgoingSemaphore = Semaphore(1)
    private val incomingSemaphore = Semaphore(1)
    private val activeDownloads = ConcurrentHashMap<TrackId, Job>()

    fun authorize(trackId: TrackId, destinationPeerId: PeerId, token: String, expiresAtElapsedMs: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        authorizations.entries.removeIf { it.value.expiresAtElapsedMs <= now }
        authorizations[token] = Authorization(trackId, destinationPeerId, expiresAtElapsedMs)
    }

    suspend fun handleIncomingFileSocket(socket: Socket, hello: HandshakeMessage.ClientHello) {
        outgoingSemaphore.withPermit {
            val lastProgressMs = AtomicLong(android.os.SystemClock.elapsedRealtime())
            val watchdog = scope.launch(Dispatchers.IO) {
                while (isActive && !socket.isClosed) {
                    delay(UPLOAD_WATCHDOG_INTERVAL_MS)
                    if (android.os.SystemClock.elapsedRealtime() - lastProgressMs.get() > UPLOAD_IDLE_TIMEOUT_MS) {
                        log.w(TAG, "Closing stalled upload peer=${hello.peerId.value.take(8)}")
                        runCatching { socket.close() }
                        break
                    }
                }
            }
            try {
                val request = hello.fileRequest
                if (request == null) {
                    HandshakeCodec.write(socket.getOutputStream(), HandshakeMessage.Rejected("Missing file request"))
                    return@withPermit
                }
                if (request.roomId != hello.roomId) {
                    HandshakeCodec.write(socket.getOutputStream(), HandshakeMessage.Rejected("Room mismatch"))
                    return@withPermit
                }
                val authorization = authorizations[request.authorizationToken]
                if (authorization == null || authorization.trackId != request.trackId || authorization.destinationPeerId != hello.peerId) {
                    HandshakeCodec.write(socket.getOutputStream(), HandshakeMessage.Rejected("Transfer not authorized"))
                    return@withPermit
                }
                if (android.os.SystemClock.elapsedRealtime() > authorization.expiresAtElapsedMs) {
                    authorizations.remove(request.authorizationToken, authorization)
                    HandshakeCodec.write(
                        socket.getOutputStream(),
                        HandshakeMessage.Rejected("Transfer authorization expired")
                    )
                    return@withPermit
                }
                if (!authorizations.remove(request.authorizationToken, authorization)) {
                    HandshakeCodec.write(
                        socket.getOutputStream(),
                        HandshakeMessage.Rejected("Transfer authorization already used")
                    )
                    return@withPermit
                }
                val file = trackRepository.requireReadableFile(request.trackId)
                if (file == null) {
                    HandshakeCodec.write(socket.getOutputStream(), HandshakeMessage.Accepted(Crypto.randomBase64(12)))
                    FileWireCodec.writeHeader(
                        socket.getOutputStream(),
                        FileResponseHeader(
                            request.requestId,
                            FileResponseStatus.NOT_FOUND,
                            request.trackId,
                            0,
                            0,
                            "File unavailable"
                        )
                    )
                    return@withPermit
                }
                if (request.offset !in 0..file.length()) {
                    HandshakeCodec.write(socket.getOutputStream(), HandshakeMessage.Accepted(Crypto.randomBase64(12)))
                    FileWireCodec.writeHeader(
                        socket.getOutputStream(),
                        FileResponseHeader(
                            request.requestId,
                            FileResponseStatus.INVALID_OFFSET,
                            request.trackId,
                            file.length(),
                            0,
                            "Invalid offset"
                        )
                    )
                    return@withPermit
                }
                HandshakeCodec.write(socket.getOutputStream(), HandshakeMessage.Accepted(Crypto.randomBase64(12)))
                FileWireCodec.writeHeader(
                    socket.getOutputStream(),
                    FileResponseHeader(
                        request.requestId,
                        FileResponseStatus.OK,
                        request.trackId,
                        file.length(),
                        request.offset
                    )
                )
                lastProgressMs.set(android.os.SystemClock.elapsedRealtime())
                file.inputStream().buffered(128 * 1024).use { input ->
                    if (request.offset > 0) input.skipFully(request.offset)
                    socket.getOutputStream().buffered(128 * 1024).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        while (currentCoroutineContext().isActive) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read > 0) {
                                output.write(buffer, 0, read)
                                lastProgressMs.set(android.os.SystemClock.elapsedRealtime())
                            }
                        }
                        currentCoroutineContext().ensureActive()
                        output.flush()
                    }
                }
            } finally {
                watchdog.cancel()
                runCatching { socket.close() }
            }
        }
    }

    fun download(
        roomId: String,
        track: TrackDescriptor,
        source: PeerEndpoint,
        authorizationToken: String,
    ) {
        activeDownloads[track.trackId]?.cancel()
        activeDownloads[track.trackId] = scope.launch(Dispatchers.IO) {
            try {
                if (fileStore.hasVerified(track.trackId, track.sizeBytes)) {
                    // A process may have committed the content-addressed file just before crashing,
                    // leaving the database registration incomplete. Repair that state before
                    // announcing readiness, and keep the operation tracked by cancelAll().
                    trackRepository.registerManagedFile(track, retentionPolicyProvider())
                    onProgress(
                        TransferProgress(
                            track.trackId,
                            track.sizeBytes,
                            track.sizeBytes,
                            source.peerId,
                            localIdentity.peerId,
                            MemberTrackState.READY,
                        )
                    )
                    onCompleted(track)
                } else {
                    incomingSemaphore.withPermit {
                        performDownload(roomId, track, source, authorizationToken)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                log.w(TAG, "Transfer failed track=${track.trackId.value.take(8)}", error)
                onProgress(
                    TransferProgress(
                        track.trackId,
                        fileStore.partialFile(track.trackId).length(),
                        track.sizeBytes,
                        source.peerId,
                        localIdentity.peerId,
                        MemberTrackState.FAILED,
                        error.message,
                    )
                )
                onFailed(track.trackId, source.peerId, error.message ?: "Transfer failed")
            }
        }.also { job -> job.invokeOnCompletion { activeDownloads.remove(track.trackId, job) } }
    }

    @Suppress("UsableSpace") // Require space already available; do not evict other apps' caches.
    private suspend fun performDownload(
        roomId: String,
        track: TrackDescriptor,
        source: PeerEndpoint,
        authorizationToken: String,
    ) {
        require(track.sizeBytes in 1..MAX_TRACK_SIZE_BYTES) { "Unsupported track size" }
        val address = java.net.InetAddress.getByName(source.hostAddress)
        NetworkAddressPolicy.requireAllowed(address)
        val partial = fileStore.partialFile(track.trackId)
        partial.parentFile?.mkdirs()
        var offset = partial.takeIf { it.isFile }?.length() ?: 0L
        if (offset > track.sizeBytes) {
            partial.delete()
            offset = 0
        }
        val remaining = track.sizeBytes - offset
        require(partial.parentFile?.usableSpace ?: 0L >= remaining + MIN_FREE_SPACE_BYTES) { "Not enough storage space" }
        val socket = Socket().apply {
            tcpNoDelay = true
            keepAlive = true
            connect(InetSocketAddress(address, source.port), 10_000)
            soTimeout = 20_000
        }
        try {
            val request = FileRequest(UUID.randomUUID().toString(), roomId, track.trackId, offset, authorizationToken)
            HandshakeCodec.write(
                socket.getOutputStream(),
                HandshakeMessage.ClientHello(
                    channel = ChannelType.FILE,
                    peerId = localIdentity.peerId,
                    displayName = localIdentity.displayName,
                    appVersion = appVersion,
                    protocolVersions = listOf(PROTOCOL_VERSION),
                    listeningPort = listeningPort(),
                    roomId = roomId,
                    clientNonce = Crypto.randomBase64(12),
                    fileRequest = request,
                )
            )
            when (val response = HandshakeCodec.read(socket.getInputStream())) {
                is HandshakeMessage.Rejected -> error(response.reason)
                is HandshakeMessage.Accepted -> Unit
                else -> error("Unexpected file handshake")
            }
            val header = FileWireCodec.readHeader(socket.getInputStream())
            check(header.requestId == request.requestId) { "Mismatched transfer response" }
            if (header.status == FileResponseStatus.INVALID_OFFSET && offset > 0) {
                partial.delete()
                error("Resume offset rejected; requesting a fresh transfer")
            }
            check(header.status == FileResponseStatus.OK) { header.message ?: "File source rejected request" }
            check(header.trackId == track.trackId && header.totalSize == track.sizeBytes) { "Track descriptor changed" }
            check(header.acceptedOffset == offset) { "Resume offset rejected" }

            onProgress(
                TransferProgress(
                    track.trackId,
                    offset,
                    track.sizeBytes,
                    source.peerId,
                    localIdentity.peerId,
                    MemberTrackState.RECEIVING
                )
            )
            var lastReport = android.os.SystemClock.elapsedRealtime()
            fileStore.receivePartial(
                trackId = track.trackId,
                offset = offset,
                expectedSize = track.sizeBytes,
                input = socket.getInputStream().buffered(128 * 1024),
            ) { total ->
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastReport >= 250 || total == track.sizeBytes) {
                    onProgress(
                        TransferProgress(
                            track.trackId,
                            total,
                            track.sizeBytes,
                            source.peerId,
                            localIdentity.peerId,
                            MemberTrackState.RECEIVING
                        )
                    )
                    lastReport = now
                }
            }
            onProgress(
                TransferProgress(
                    track.trackId,
                    track.sizeBytes,
                    track.sizeBytes,
                    source.peerId,
                    localIdentity.peerId,
                    MemberTrackState.VERIFYING
                )
            )
            if (!fileStore.verifyPartial(track.trackId, track.sizeBytes)) {
                fileStore.discardPartial(track.trackId)
                error("SHA-256 verification failed")
            }
            trackRepository.registerManagedFile(track, retentionPolicyProvider())
            onProgress(
                TransferProgress(
                    track.trackId,
                    track.sizeBytes,
                    track.sizeBytes,
                    source.peerId,
                    localIdentity.peerId,
                    MemberTrackState.READY
                )
            )
            onCompleted(track)
        } finally {
            runCatching { socket.close() }
        }
    }

    fun cancel(trackId: TrackId) {
        activeDownloads.remove(trackId)?.cancel()
    }

    fun cancelAll() {
        activeDownloads.values.forEach(Job::cancel)
        activeDownloads.clear()
    }

    private fun java.io.InputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        val buffer = ByteArray(32 * 1024)
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) error("Source file ended before resume offset")
                remaining -= read
            }
        }
    }

    companion object {
        private const val TAG = "TransferManager"
        private const val MAX_TRACK_SIZE_BYTES = 1_073_741_824L // 1 GiB
        private const val MIN_FREE_SPACE_BYTES = 32L * 1024L * 1024L
        private const val UPLOAD_WATCHDOG_INTERVAL_MS = 5_000L
        private const val UPLOAD_IDLE_TIMEOUT_MS = 30_000L
    }
}
