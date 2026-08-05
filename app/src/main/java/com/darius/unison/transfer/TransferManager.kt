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
import com.darius.unison.protocol.AuthenticatedFileStreamCodec
import com.darius.unison.protocol.Crypto
import com.darius.unison.protocol.FileRequest
import com.darius.unison.protocol.FileResponseHeader
import com.darius.unison.protocol.FileResponseStatus
import com.darius.unison.protocol.FileWireCodec
import com.darius.unison.protocol.HandshakeCodec
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.HandshakeRejectionCode
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.storage.ManagedFileLeaseReason
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.util.DiagnosticLog
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

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
    private val onActiveTransferCountChanged: (Int) -> Unit = {},
) {
    private val authorizations =
        TransferAuthorizationRegistry(
            maxEntries = MAX_TRACKED_AUTHORIZATIONS,
            nowElapsedMs = { android.os.SystemClock.elapsedRealtime() },
            onCapacityEviction = {
                log.w(TAG, "Evicted oldest transfer authorization at capacity")
            },
        )
    private val uploadGate = TransferUploadGate(MAX_CONCURRENT_UPLOADS)
    private val incomingSemaphore = Semaphore(MAX_CONCURRENT_DOWNLOADS)
    private val cancellationRegistry = TransferCancellationRegistry()
    private val activeUploadSockets = ConcurrentHashMap<String, Socket>()

    val activeTransferCount: Int
        get() = cancellationRegistry.activeCount + activeUploadSockets.size

    val pendingAuthorizationCount: Int
        get() = authorizations.size

    fun authorize(
        roomId: String,
        trackId: TrackId,
        destinationPeerId: PeerId,
        token: String,
        expiresAtElapsedMs: Long,
    ) {
        authorizations.authorize(
            roomId = roomId,
            trackId = trackId,
            destinationPeerId = destinationPeerId,
            token = token,
            expiresAtElapsedMs = expiresAtElapsedMs,
        )
    }

    suspend fun handleIncomingFileSocket(socket: Socket, hello: HandshakeMessage.FileClientHello) {
        // Independent listeners can download concurrently, while duplicate requests from one
        // destination are serialized and cannot occupy every global upload slot.
        uploadGate.withPermit(hello.peerId) {
            val request = hello.request
            val uploadOperationId = request.requestId
            activeUploadSockets.put(uploadOperationId, socket)?.let { previous ->
                runCatching { previous.close() }
            }
            notifyActiveTransferCount()
            val lastProgressMs = AtomicLong(android.os.SystemClock.elapsedRealtime())
            val watchdog =
                scope.launch(Dispatchers.IO) {
                    while (isActive && !socket.isClosed) {
                        delay(UPLOAD_WATCHDOG_INTERVAL_MS)
                        if (
                            android.os.SystemClock.elapsedRealtime() - lastProgressMs.get() >
                                UPLOAD_IDLE_TIMEOUT_MS
                        ) {
                            log.w(TAG, "Closing stalled upload peer=${hello.peerId.value.take(8)}")
                            runCatching { socket.close() }
                            break
                        }
                    }
                }
            try {
                if (request.roomId != hello.roomId) {
                    HandshakeCodec.write(
                        socket.getOutputStream(),
                        HandshakeMessage.Rejected(
                            "Room mismatch",
                            HandshakeRejectionCode.WRONG_ROOM,
                        ),
                    )
                    return@withPermit
                }
                val authorization =
                    authorizations.findMatching(
                        authorizationId = request.authorizationId,
                        roomId = request.roomId,
                        trackId = request.trackId,
                        destinationPeerId = hello.peerId,
                    )
                if (authorization == null) {
                    HandshakeCodec.write(
                        socket.getOutputStream(),
                        HandshakeMessage.Rejected(
                            "Transfer not authorized",
                            HandshakeRejectionCode.AUTHENTICATION_FAILED,
                        ),
                    )
                    return@withPermit
                }

                val serverNonce = Crypto.randomBase64(18)
                HandshakeCodec.write(
                    socket.getOutputStream(),
                    HandshakeMessage.FileChallenge(request.requestId, serverNonce),
                )
                val proof =
                    HandshakeCodec.read(socket.getInputStream()) as? HandshakeMessage.FileProof
                val expectedProof =
                    Crypto.fileTransferProof(
                        authorizationToken = authorization.token,
                        roomId = request.roomId,
                        trackId = request.trackId.value,
                        requestId = request.requestId,
                        sourcePeerId = localIdentity.peerId.value,
                        destinationPeerId = hello.peerId.value,
                        offset = request.offset,
                        clientNonce = hello.clientNonce,
                        serverNonce = serverNonce,
                    )
                if (
                    proof == null ||
                        proof.requestId != request.requestId ||
                        !constantTimeStringEquals(expectedProof, proof.proofBase64)
                ) {
                    HandshakeCodec.write(
                        socket.getOutputStream(),
                        HandshakeMessage.Rejected(
                            "Transfer proof rejected",
                            HandshakeRejectionCode.AUTHENTICATION_FAILED,
                        ),
                    )
                    return@withPermit
                }

                // Validate the source before consuming the one-use authorization. Temporary file
                // unavailability and invalid resume offsets must remain retryable.
                val file = trackRepository.requireReadableFile(request.trackId)
                if (file == null) {
                    HandshakeCodec.write(
                        socket.getOutputStream(),
                        HandshakeMessage.Rejected(
                            "File unavailable",
                            HandshakeRejectionCode.INVALID_REQUEST,
                        ),
                    )
                    return@withPermit
                }
                val uploadLease =
                    fileStore.acquireLease(
                        request.trackId,
                        ManagedFileLeaseReason.TRANSFER_UPLOAD,
                    )
                try {
                    if (request.offset !in 0..file.length()) {
                        HandshakeCodec.write(
                            socket.getOutputStream(),
                            HandshakeMessage.Rejected(
                                "Invalid transfer offset",
                                HandshakeRejectionCode.INVALID_REQUEST,
                            ),
                        )
                        return@withPermit
                    }
                    if (!authorizations.consume(request.authorizationId, authorization)) {
                        HandshakeCodec.write(
                            socket.getOutputStream(),
                            HandshakeMessage.Rejected(
                                "Transfer authorization already used",
                                HandshakeRejectionCode.AUTHENTICATION_FAILED,
                            ),
                        )
                        return@withPermit
                    }

                    val sessionKey =
                        Crypto.deriveFileTransferSessionKey(
                            authorization.token,
                            request.roomId,
                            request.trackId.value,
                            hello.clientNonce,
                            serverNonce,
                        )
                    val associatedData =
                        Crypto.fileTransferAssociatedData(
                            request.roomId,
                            request.trackId.value,
                            request.requestId,
                            localIdentity.peerId.value,
                            hello.peerId.value,
                            request.offset,
                            hello.clientNonce,
                            serverNonce,
                        )
                    val baseNonce = Crypto.randomBytes(12)
                    try {
                        HandshakeCodec.write(
                            socket.getOutputStream(),
                            HandshakeMessage.FileReady(
                                request.requestId,
                                Base64.getUrlEncoder().withoutPadding().encodeToString(baseNonce),
                            ),
                        )
                        FileWireCodec.writeEncryptedHeader(
                            socket.getOutputStream(),
                            FileResponseHeader(
                                request.requestId,
                                FileResponseStatus.OK,
                                request.trackId,
                                file.length(),
                                request.offset,
                            ),
                            sessionKey,
                            baseNonce,
                            associatedData,
                        )
                        lastProgressMs.set(android.os.SystemClock.elapsedRealtime())
                        file
                            .inputStream()
                            .buffered(AuthenticatedFileStreamCodec.MAX_CHUNK_BYTES)
                            .use { input ->
                                if (request.offset > 0) input.skipFully(request.offset)
                                FileWireCodec.writeEncryptedBody(
                                    input = input,
                                    output = socket.getOutputStream(),
                                    byteCount = file.length() - request.offset,
                                    key = sessionKey,
                                    baseNonce = baseNonce,
                                    associatedData = associatedData,
                                ) {
                                    lastProgressMs.set(android.os.SystemClock.elapsedRealtime())
                                }
                            }
                    } finally {
                        sessionKey.fill(0)
                        associatedData.fill(0)
                        baseNonce.fill(0)
                    }
                } finally {
                    uploadLease.close()
                }
            } finally {
                watchdog.cancelAndJoin()
                activeUploadSockets.remove(uploadOperationId, socket)
                notifyActiveTransferCount()
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
        cancel(track.trackId)
        val job =
            scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                try {
                    cancellationRegistry.withTrackOperation(track.trackId) {
                        currentCoroutineContext().ensureActive()
                        val lease =
                            fileStore.acquireLease(
                                track.trackId,
                                ManagedFileLeaseReason.TRANSFER_DOWNLOAD,
                            )
                        try {
                            if (fileStore.hasVerified(track.trackId, track.sizeBytes)) {
                                // A process may have committed the content-addressed file just
                                // before crashing,
                                // leaving the database registration incomplete. Repair that state
                                // before
                                // announcing readiness, and keep the operation tracked by
                                // cancelAll().
                                trackRepository.registerManagedFile(
                                    track,
                                    retentionPolicyProvider(),
                                )
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
                        } finally {
                            lease.close()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    onProgress(
                        TransferProgress(
                            track.trackId,
                            fileStore.partialFile(track.trackId).length(),
                            track.sizeBytes,
                            source.peerId,
                            localIdentity.peerId,
                            MemberTrackState.CANCELLED,
                            "Cancelled",
                        )
                    )
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
            }
        cancellationRegistry.registerJob(track.trackId, job)
        job.invokeOnCompletion { notifyActiveTransferCount() }
        notifyActiveTransferCount()
        job.start()
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
        require(partial.parentFile?.usableSpace ?: 0L >= remaining + MIN_FREE_SPACE_BYTES) {
            "Not enough storage space"
        }
        val socket = Socket()
        // Attach before the blocking connect so cancellation can close the socket immediately.
        cancellationRegistry.attachSocket(track.trackId, socket)
        try {
            currentCoroutineContext().ensureActive()
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.connect(InetSocketAddress(address, source.port), 10_000)
            socket.soTimeout = 20_000
            currentCoroutineContext().ensureActive()
            val clientNonce = Crypto.randomBase64(18)
            val request =
                FileRequest(
                    requestId = UUID.randomUUID().toString(),
                    roomId = roomId,
                    trackId = track.trackId,
                    offset = offset,
                    authorizationId = Crypto.fileTransferAuthorizationId(authorizationToken),
                )
            HandshakeCodec.write(
                socket.getOutputStream(),
                HandshakeMessage.FileClientHello(
                    peerId = localIdentity.peerId,
                    displayName = localIdentity.displayName,
                    appVersion = appVersion,
                    protocolVersion = PROTOCOL_VERSION,
                    listeningPort = listeningPort(),
                    roomId = roomId,
                    clientNonce = clientNonce,
                    request = request,
                ),
            )
            val challenge =
                when (val response = HandshakeCodec.read(socket.getInputStream())) {
                    is HandshakeMessage.Rejected -> error(response.reason)
                    is HandshakeMessage.FileChallenge -> response
                    else -> error("Unexpected file handshake challenge")
                }
            check(challenge.requestId == request.requestId) { "Mismatched transfer challenge" }
            val proof =
                Crypto.fileTransferProof(
                    authorizationToken = authorizationToken,
                    roomId = roomId,
                    trackId = track.trackId.value,
                    requestId = request.requestId,
                    sourcePeerId = source.peerId.value,
                    destinationPeerId = localIdentity.peerId.value,
                    offset = offset,
                    clientNonce = clientNonce,
                    serverNonce = challenge.serverNonce,
                )
            HandshakeCodec.write(
                socket.getOutputStream(),
                HandshakeMessage.FileProof(request.requestId, proof),
            )
            val ready =
                when (val response = HandshakeCodec.read(socket.getInputStream())) {
                    is HandshakeMessage.Rejected -> {
                        if (offset > 0 && response.reason.contains("offset", ignoreCase = true)) {
                            partial.delete()
                        }
                        error(response.reason)
                    }
                    is HandshakeMessage.FileReady -> response
                    else -> error("Unexpected file handshake completion")
                }
            check(ready.requestId == request.requestId) { "Mismatched transfer session" }
            val baseNonce = Base64.getUrlDecoder().decode(ready.baseNonceBase64)
            check(baseNonce.size == 12) { "Invalid transfer nonce" }
            val sessionKey =
                Crypto.deriveFileTransferSessionKey(
                    authorizationToken,
                    roomId,
                    track.trackId.value,
                    clientNonce,
                    challenge.serverNonce,
                )
            val associatedData =
                Crypto.fileTransferAssociatedData(
                    roomId,
                    track.trackId.value,
                    request.requestId,
                    source.peerId.value,
                    localIdentity.peerId.value,
                    offset,
                    clientNonce,
                    challenge.serverNonce,
                )
            try {
                val header =
                    FileWireCodec.readEncryptedHeader(
                        socket.getInputStream(),
                        sessionKey,
                        baseNonce,
                        associatedData,
                    )
                check(header.requestId == request.requestId) { "Mismatched transfer response" }
                if (header.status == FileResponseStatus.INVALID_OFFSET && offset > 0) {
                    partial.delete()
                    error("Resume offset rejected; retry the transfer from the beginning")
                }
                check(header.status == FileResponseStatus.OK) {
                    header.message ?: "File source rejected request"
                }
                check(header.trackId == track.trackId && header.totalSize == track.sizeBytes) {
                    "Track descriptor changed"
                }
                check(header.acceptedOffset == offset) { "Resume offset rejected" }

                onProgress(
                    TransferProgress(
                        track.trackId,
                        offset,
                        track.sizeBytes,
                        source.peerId,
                        localIdentity.peerId,
                        MemberTrackState.RECEIVING,
                    )
                )
                var lastReport = android.os.SystemClock.elapsedRealtime()
                val receiveResult =
                    FileWireCodec.encryptedBodyInputStream(
                            input = socket.getInputStream(),
                            expectedBytes = track.sizeBytes - offset,
                            key = sessionKey,
                            baseNonce = baseNonce,
                            associatedData = associatedData,
                        )
                        .use { authenticatedInput ->
                            fileStore.receivePartialAndHash(
                                trackId = track.trackId,
                                offset = offset,
                                expectedSize = track.sizeBytes,
                                input = authenticatedInput,
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
                                            MemberTrackState.RECEIVING,
                                        )
                                    )
                                    lastReport = now
                                }
                            }
                        }
                onProgress(
                    TransferProgress(
                        track.trackId,
                        track.sizeBytes,
                        track.sizeBytes,
                        source.peerId,
                        localIdentity.peerId,
                        MemberTrackState.VERIFYING,
                    )
                )
                if (
                    !fileStore.commitPartialWithDigest(
                        track.trackId,
                        track.sizeBytes,
                        receiveResult.sha256Hex,
                    )
                ) {
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
                        MemberTrackState.READY,
                    )
                )
                onCompleted(track)
            } finally {
                sessionKey.fill(0)
                associatedData.fill(0)
                baseNonce.fill(0)
            }
        } finally {
            cancellationRegistry.detachSocket(track.trackId, socket)
            runCatching { socket.close() }
        }
    }

    fun cancel(trackId: TrackId) {
        cancellationRegistry.cancel(trackId)
        notifyActiveTransferCount()
    }

    fun cancelAll() {
        activeUploadSockets.values.forEach { socket -> runCatching { socket.close() } }
        authorizations.clear()
        cancellationRegistry.cancelAll()
        notifyActiveTransferCount()
    }

    suspend fun cancelAllAndJoin(timeoutMs: Long): Boolean {
        cancelAll()
        val downloadsClosed = cancellationRegistry.cancelAllAndJoin(timeoutMs = timeoutMs)
        val uploadsClosed =
            withTimeoutOrNull(timeoutMs) {
                while (activeUploadSockets.isNotEmpty()) delay(10)
                true
            } ?: false
        return downloadsClosed && uploadsClosed
    }

    private fun constantTimeStringEquals(expected: String, actual: String): Boolean {
        val expectedBytes = expected.encodeToByteArray()
        val actualBytes = actual.encodeToByteArray()
        return try {
            Crypto.constantTimeEquals(expectedBytes, actualBytes)
        } finally {
            expectedBytes.fill(0)
            actualBytes.fill(0)
        }
    }

    private fun notifyActiveTransferCount() {
        onActiveTransferCountChanged(activeTransferCount)
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
        private const val MAX_CONCURRENT_DOWNLOADS = 2
        private const val MAX_CONCURRENT_UPLOADS = 3
        private const val MAX_TRACK_SIZE_BYTES = 1_073_741_824L // 1 GiB
        private const val MAX_TRACKED_AUTHORIZATIONS = 512
        private const val MIN_FREE_SPACE_BYTES = 32L * 1024L * 1024L
        private const val UPLOAD_WATCHDOG_INTERVAL_MS = 5_000L
        private const val UPLOAD_IDLE_TIMEOUT_MS = 30_000L
    }
}
