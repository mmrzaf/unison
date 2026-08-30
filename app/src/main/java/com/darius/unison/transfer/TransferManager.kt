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
import com.darius.unison.network.LocalNetworkSocketProvider
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
import com.darius.unison.protocol.TransferFailureBlame
import com.darius.unison.protocol.TransferFailureCode
import com.darius.unison.protocol.TransferFailureStage
import com.darius.unison.storage.ManagedFileLeaseReason
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.util.DiagnosticCategory
import com.darius.unison.util.DiagnosticLog
import java.io.FileInputStream
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

data class TransferFailure(
    val trackId: TrackId,
    val sourcePeerId: PeerId?,
    val stage: TransferFailureStage,
    val code: TransferFailureCode,
    val blame: TransferFailureBlame,
    val retryable: Boolean,
    val message: String,
)

class TransferManager(
    private val localIdentity: LocalIdentity,
    private val listeningPort: () -> Int,
    private val appVersion: String,
    private val trackRepository: TrackRepository,
    private val fileStore: ManagedFileStore,
    private val scope: CoroutineScope,
    private val log: DiagnosticLog,
    private val socketProvider: LocalNetworkSocketProvider,
    private val retentionPolicyProvider: suspend () -> RetentionPolicy,
    private val onProgress: (TransferProgress) -> Unit,
    private val onCompleted: suspend (TrackDescriptor) -> Unit,
    private val onFailed: suspend (TransferFailure) -> Unit,
    private val capacityPolicy: TransferCapacityPolicy = TransferCapacityPolicy.DEFAULT,
    private val onActiveTransferCountChanged: (Int) -> Unit = {},
) {
    private val authorizations =
        TransferAuthorizationRegistry(
            maxEntries = MAX_TRACKED_AUTHORIZATIONS,
            nowElapsedMs = { android.os.SystemClock.elapsedRealtime() },
            onCapacityEviction = {
                log.warn(
                    TAG,
                    DiagnosticCategory.TRANSFER,
                    "transfer.authorization.evicted",
                )
            },
        )
    private val uploadGate =
        TransferUploadGate(
            maxConcurrentUploads = capacityPolicy.maxOutboundPerSource,
            maxConcurrentPerDestination = capacityPolicy.maxPerSourceDestinationPair,
        )
    private val incomingSemaphore = Semaphore(capacityPolicy.maxInboundPerDestination)
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
        // Coordinator admission should already guarantee capacity. Keep a non-blocking transport
        // guard so stale/duplicate sockets fail fast instead of waiting until the client handshake
        // timeout and masquerading as network instability.
        val admitted = uploadGate.tryWithPermit(hello.peerId) {
            val request = hello.request
            val uploadOperationId = request.requestId
            activeUploadSockets.put(uploadOperationId, socket)?.let { previous ->
                runCatching { previous.close() }
            }
            notifyActiveTransferCount()
            log.debug(
                TAG,
                DiagnosticCategory.TRANSFER,
                "transfer.upload.started",
                attributes = mapOf(
                    "transfer.operation_id" to uploadOperationId,
                    "transfer.assignment_id" to request.authorizationId.take(16),
                    "track.id" to request.trackId.value.take(12),
                    "peer.id" to hello.peerId.value.take(12),
                    "transfer.offset" to request.offset,
                ),
            )
            val lastProgressMs = AtomicLong(0L)
            var watchdog: kotlinx.coroutines.Job? = null
            try {
                if (request.roomId != hello.roomId) {
                    HandshakeCodec.write(
                        socket.getOutputStream(),
                        HandshakeMessage.Rejected(
                            "Room mismatch",
                            HandshakeRejectionCode.WRONG_ROOM,
                        ),
                    )
                    return@tryWithPermit
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
                    return@tryWithPermit
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
                    return@tryWithPermit
                }

                // The proof establishes an authenticated transfer session. From this point on,
                // represent file-level rejection with the typed encrypted response header rather
                // than English handshake text. This keeps retry/blame decisions independent of
                // user-facing wording while remaining inside Protocol 2.
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
                    val leasedFile =
                        trackRepository.requireReadableFileWithLease(
                            request.trackId,
                            ManagedFileLeaseReason.TRANSFER_UPLOAD,
                        )
                    if (leasedFile == null) {
                        FileWireCodec.writeEncryptedHeader(
                            socket.getOutputStream(),
                            FileResponseHeader(
                                requestId = request.requestId,
                                status = FileResponseStatus.NOT_FOUND,
                                trackId = request.trackId,
                                totalSize = 0L,
                                acceptedOffset = 0L,
                                message = "File unavailable",
                            ),
                            sessionKey,
                            baseNonce,
                            associatedData,
                        )
                        return@tryWithPermit
                    }
                    val file = leasedFile.file
                    try {
                        if (request.offset !in 0..file.length()) {
                            FileWireCodec.writeEncryptedHeader(
                                socket.getOutputStream(),
                                FileResponseHeader(
                                    requestId = request.requestId,
                                    status = FileResponseStatus.INVALID_OFFSET,
                                    trackId = request.trackId,
                                    totalSize = file.length(),
                                    acceptedOffset = 0L,
                                    message = "Resume offset rejected",
                                ),
                                sessionKey,
                                baseNonce,
                                associatedData,
                            )
                            return@tryWithPermit
                        }
                        if (!authorizations.consume(request.authorizationId, authorization)) {
                            FileWireCodec.writeEncryptedHeader(
                                socket.getOutputStream(),
                                FileResponseHeader(
                                    requestId = request.requestId,
                                    status = FileResponseStatus.UNAUTHORIZED,
                                    trackId = request.trackId,
                                    totalSize = file.length(),
                                    acceptedOffset = request.offset,
                                    message = "Transfer authorization already used",
                                ),
                                sessionKey,
                                baseNonce,
                                associatedData,
                            )
                            return@tryWithPermit
                        }

                        FileWireCodec.writeEncryptedHeader(
                            socket.getOutputStream(),
                            FileResponseHeader(
                                requestId = request.requestId,
                                status = FileResponseStatus.OK,
                                trackId = request.trackId,
                                totalSize = file.length(),
                                acceptedOffset = request.offset,
                            ),
                            sessionKey,
                            baseNonce,
                            associatedData,
                        )
                        lastProgressMs.set(android.os.SystemClock.elapsedRealtime())
                        watchdog =
                            scope.launch(Dispatchers.IO) {
                                while (isActive && !socket.isClosed) {
                                    delay(UPLOAD_WATCHDOG_INTERVAL_MS)
                                    if (
                                        android.os.SystemClock.elapsedRealtime() - lastProgressMs.get() >
                                            UPLOAD_IDLE_TIMEOUT_MS
                                    ) {
                                        log.warn(
                                            TAG,
                                            DiagnosticCategory.TRANSFER,
                                            "transfer.upload.stalled",
                                            attributes = mapOf(
                                                "transfer.operation_id" to uploadOperationId,
                                                "transfer.assignment_id" to request.authorizationId.take(16),
                                                "track.id" to request.trackId.value.take(12),
                                                "peer.id" to hello.peerId.value.take(12),
                                            ),
                                        )
                                        runCatching { socket.close() }
                                        break
                                    }
                                }
                            }
                        FileInputStream(file).use { fileInput ->
                            fileInput.channel.position(request.offset)
                            fileInput
                                .buffered(AuthenticatedFileStreamCodec.MAX_CHUNK_BYTES)
                                .use { input ->
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
                        }
                        log.info(
                            TAG,
                            DiagnosticCategory.TRANSFER,
                            "transfer.upload.completed",
                            attributes = mapOf(
                                "transfer.operation_id" to uploadOperationId,
                                "transfer.assignment_id" to request.authorizationId.take(16),
                                "track.id" to request.trackId.value.take(12),
                                "peer.id" to hello.peerId.value.take(12),
                                "transfer.offset" to request.offset,
                                "transfer.bytes" to (file.length() - request.offset),
                            ),
                        )
                    } finally {
                        leasedFile.lease.close()
                    }
                } finally {
                    sessionKey.fill(0)
                    associatedData.fill(0)
                    baseNonce.fill(0)
                }
            } finally {
                watchdog?.cancelAndJoin()
                activeUploadSockets.remove(uploadOperationId, socket)
                notifyActiveTransferCount()
                runCatching { socket.close() }
            }
        }
        if (!admitted) {
            runCatching {
                HandshakeCodec.write(
                    socket.getOutputStream(),
                    HandshakeMessage.Rejected(
                        "Transfer source busy; retry",
                        HandshakeRejectionCode.RATE_LIMITED,
                    ),
                )
            }
            runCatching { socket.close() }
            log.debug(
                TAG,
                DiagnosticCategory.TRANSFER,
                "transfer.upload.capacity_rejected",
                attributes = mapOf(
                    "transfer.operation_id" to hello.request.requestId,
                    "transfer.assignment_id" to hello.request.authorizationId.take(16),
                    "track.id" to hello.request.trackId.value.take(12),
                    "peer.id" to hello.peerId.value.take(12),
                ),
            )
        }
    }

    fun download(
        roomId: String,
        track: TrackDescriptor,
        source: PeerEndpoint,
        authorizationToken: String,
    ) {
        val operationId = UUID.randomUUID().toString()
        val assignmentId = Crypto.fileTransferAuthorizationId(authorizationToken).take(16)
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
                                    performDownload(
                                        roomId,
                                        track,
                                        source,
                                        authorizationToken,
                                        operationId,
                                        assignmentId,
                                    )
                                }
                            }
                        } finally {
                            lease.close()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    val reason = cancelled.message ?: "Transfer cancelled"
                    log.debug(
                        TAG,
                        DiagnosticCategory.TRANSFER,
                        "transfer.download.cancelled",
                        attributes = mapOf(
                            "transfer.operation_id" to operationId,
                            "transfer.assignment_id" to assignmentId,
                            "track.id" to track.trackId.value.take(12),
                            "peer.id" to source.peerId.value.take(12),
                            "transfer.reason" to reason,
                        ),
                    )
                    onProgress(
                        TransferProgress(
                            track.trackId,
                            fileStore.partialFile(track.trackId).length(),
                            track.sizeBytes,
                            source.peerId,
                            localIdentity.peerId,
                            MemberTrackState.CANCELLED,
                            reason,
                        )
                    )
                    throw cancelled
                } catch (error: Exception) {
                    // Closing a socket is how cancellation wakes blocking I/O. Re-check coroutine
                    // cancellation before interpreting the resulting IOException as route failure.
                    currentCoroutineContext().ensureActive()
                    val staged = error as? TransferStageException
                    val userMessage = staged?.cause?.message ?: error.message ?: "Transfer failed"
                    log.warn(
                        TAG,
                        DiagnosticCategory.TRANSFER,
                        "transfer.track.failed",
                        attributes = mapOf(
                            "transfer.operation_id" to operationId,
                            "transfer.assignment_id" to assignmentId,
                            "track.id" to track.trackId.value.take(12),
                            "peer.id" to source.peerId.value.take(12),
                            "transfer.phase" to staged?.stage,
                        ),
                        throwable = staged?.cause ?: error,
                    )
                    onProgress(
                        TransferProgress(
                            track.trackId,
                            fileStore.partialFile(track.trackId).length(),
                            track.sizeBytes,
                            source.peerId,
                            localIdentity.peerId,
                            MemberTrackState.FAILED,
                            userMessage,
                        )
                    )
                    onFailed(classifyFailure(track.trackId, source.peerId, staged, error, userMessage))
                }
            }
        if (!cancellationRegistry.tryRegisterJob(track.trackId, job)) {
            job.cancel(CancellationException("Duplicate transfer assignment ignored"))
            log.debug(
                TAG,
                DiagnosticCategory.TRANSFER,
                "transfer.download.duplicate_ignored",
                attributes = mapOf(
                    "transfer.operation_id" to operationId,
                    "transfer.assignment_id" to assignmentId,
                    "track.id" to track.trackId.value.take(12),
                    "peer.id" to source.peerId.value.take(12),
                ),
            )
            return
        }
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
        operationId: String,
        assignmentId: String,
    ) {
        var phase = "VALIDATE"
        var routeAttributes: Map<String, Any?> =
            mapOf(
                "transfer.operation_id" to operationId,
                "transfer.assignment_id" to assignmentId,
            )
        var socket: Socket? = null
        try {
            require(track.sizeBytes in 1..MAX_TRACK_SIZE_BYTES) { "Unsupported track size" }
            val address =
                NetworkAddressPolicy.parseAllowedAddress(source.hostAddress)
                    ?: throw IllegalArgumentException("Invalid local transfer endpoint")
            val partial = fileStore.partialFile(track.trackId)
            partial.parentFile?.mkdirs()
            var offset = partial.takeIf { it.isFile }?.length() ?: 0L
            if (offset > track.sizeBytes) {
                partial.delete()
                offset = 0
            }
            val remaining = track.sizeBytes - offset
            if ((partial.parentFile?.usableSpace ?: 0L) < remaining + MIN_FREE_SPACE_BYTES) {
                throw TransferProblemException(
                    code = TransferFailureCode.DESTINATION_STORAGE,
                    blame = TransferFailureBlame.DESTINATION,
                    retryable = false,
                    message = "Not enough storage space",
                )
            }

            phase = "CONNECT"
            val route = socketProvider.createSocket(address, purpose = "transfer")
            routeAttributes = route.diagnosticAttributes()
            socket = route.socket
            cancellationRegistry.attachSocket(track.trackId, socket)
            currentCoroutineContext().ensureActive()
            socket.tcpNoDelay = true
            socket.keepAlive = true
            log.debug(
                TAG,
                DiagnosticCategory.TRANSFER,
                "transfer.download.connecting",
                attributes = routeAttributes + mapOf(
                    "track.id" to track.trackId.value.take(12),
                    "peer.id" to source.peerId.value.take(12),
                    "transfer.offset" to offset,
                    "transfer.expected_bytes" to track.sizeBytes,
                    "network.remote_port" to source.port,
                ),
            )
            socket.connect(InetSocketAddress(address, source.port), 10_000)
            socket.soTimeout = 20_000
            socketProvider.onConnected(route, socket)
            log.debug(
                TAG,
                DiagnosticCategory.TRANSFER,
                "transfer.download.connected",
                attributes = routeAttributes + mapOf(
                    "track.id" to track.trackId.value.take(12),
                    "peer.id" to source.peerId.value.take(12),
                    "transfer.offset" to offset,
                ),
            )
            currentCoroutineContext().ensureActive()

            phase = "HANDSHAKE"
            val clientNonce = Crypto.randomBase64(18)
            val request =
                FileRequest(
                    requestId = operationId,
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
                    is HandshakeMessage.Rejected -> throw transferProblemForHandshakeRejection(response)
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
                    is HandshakeMessage.Rejected ->
                        throw transferProblemForHandshakeRejection(response)
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
                if (header.status != FileResponseStatus.OK) {
                    if (header.status == FileResponseStatus.INVALID_OFFSET && offset > 0) {
                        partial.delete()
                    }
                    throw transferProblemForFileResponse(header)
                }
                check(header.trackId == track.trackId && header.totalSize == track.sizeBytes) {
                    "Track descriptor changed"
                }
                check(header.acceptedOffset == offset) { "Resume offset rejected" }

                phase = "BODY"
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

                phase = "VERIFY"
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
                    throw TransferProblemException(
                        code = TransferFailureCode.INTEGRITY,
                        blame = TransferFailureBlame.SOURCE,
                        retryable = true,
                        message = "SHA-256 verification failed",
                    )
                }

                phase = "REGISTER"
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
                log.info(
                    TAG,
                    DiagnosticCategory.TRANSFER,
                    "transfer.download.completed",
                    attributes = routeAttributes + mapOf(
                        "track.id" to track.trackId.value.take(12),
                        "peer.id" to source.peerId.value.take(12),
                        "transfer.bytes" to track.sizeBytes,
                    ),
                )
                onCompleted(track)
            } finally {
                sessionKey.fill(0)
                associatedData.fill(0)
                baseNonce.fill(0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.debug(
                TAG,
                DiagnosticCategory.TRANSFER,
                "transfer.download.failure_detail",
                attributes = routeAttributes + mapOf(
                    "track.id" to track.trackId.value.take(12),
                    "peer.id" to source.peerId.value.take(12),
                    "transfer.phase" to phase,
                ),
                throwable = error,
            )
            throw TransferStageException(phase, error)
        } finally {
            socket?.let { connected ->
                cancellationRegistry.detachSocket(track.trackId, connected)
                runCatching { connected.close() }
            }
        }
    }

    private class TransferProblemException(
        val code: TransferFailureCode,
        val blame: TransferFailureBlame,
        val retryable: Boolean,
        message: String,
    ) : Exception(message)

    private fun transferProblemForHandshakeRejection(
        rejection: HandshakeMessage.Rejected,
    ): TransferProblemException =
        when (rejection.code) {
            HandshakeRejectionCode.AUTHENTICATION_FAILED ->
                TransferProblemException(
                    TransferFailureCode.AUTHENTICATION,
                    TransferFailureBlame.ROUTE,
                    true,
                    rejection.reason,
                )
            HandshakeRejectionCode.RATE_LIMITED ->
                TransferProblemException(
                    TransferFailureCode.IO,
                    TransferFailureBlame.UNKNOWN,
                    true,
                    rejection.reason,
                )
            else ->
                TransferProblemException(
                    TransferFailureCode.PROTOCOL,
                    TransferFailureBlame.ROUTE,
                    true,
                    rejection.reason,
                )
        }

    private fun transferProblemForFileResponse(
        header: FileResponseHeader,
    ): TransferProblemException =
        when (header.status) {
            FileResponseStatus.NOT_FOUND ->
                TransferProblemException(
                    TransferFailureCode.SOURCE_UNAVAILABLE,
                    TransferFailureBlame.SOURCE,
                    true,
                    header.message ?: "File unavailable",
                )
            FileResponseStatus.UNAUTHORIZED ->
                TransferProblemException(
                    TransferFailureCode.AUTHENTICATION,
                    TransferFailureBlame.ROUTE,
                    true,
                    header.message ?: "Transfer authorization rejected",
                )
            FileResponseStatus.INVALID_OFFSET ->
                TransferProblemException(
                    TransferFailureCode.PROTOCOL,
                    TransferFailureBlame.UNKNOWN,
                    true,
                    header.message ?: "Resume offset rejected",
                )
            FileResponseStatus.BUSY ->
                TransferProblemException(
                    TransferFailureCode.IO,
                    TransferFailureBlame.UNKNOWN,
                    true,
                    header.message ?: "Transfer source busy",
                )
            FileResponseStatus.ERROR ->
                TransferProblemException(
                    TransferFailureCode.UNKNOWN,
                    TransferFailureBlame.UNKNOWN,
                    true,
                    header.message ?: "File source rejected request",
                )
            FileResponseStatus.OK -> error("OK is not a transfer problem")
        }

    private fun classifyFailure(
        trackId: TrackId,
        sourcePeerId: PeerId?,
        staged: TransferStageException?,
        error: Exception,
        message: String,
    ): TransferFailure {
        val stage =
            when (staged?.stage) {
                "VALIDATE" -> TransferFailureStage.VALIDATE
                "CONNECT" -> TransferFailureStage.CONNECT
                "HANDSHAKE" -> TransferFailureStage.HANDSHAKE
                "BODY" -> TransferFailureStage.BODY
                "VERIFY" -> TransferFailureStage.VERIFY
                "REGISTER" -> TransferFailureStage.REGISTER
                else -> TransferFailureStage.UNKNOWN
            }
        val problem = (staged?.cause as? TransferProblemException) ?: (error as? TransferProblemException)
        val (code, blame, retryable) =
            when {
                problem != null -> Triple(problem.code, problem.blame, problem.retryable)
                stage == TransferFailureStage.CONNECT ->
                    Triple(TransferFailureCode.CONNECT_FAILED, TransferFailureBlame.ROUTE, true)
                stage == TransferFailureStage.HANDSHAKE ->
                    Triple(TransferFailureCode.PROTOCOL, TransferFailureBlame.ROUTE, true)
                (staged?.cause ?: error) is java.io.IOException ->
                    Triple(TransferFailureCode.IO, TransferFailureBlame.ROUTE, true)
                else -> Triple(TransferFailureCode.UNKNOWN, TransferFailureBlame.UNKNOWN, true)
            }
        return TransferFailure(
            trackId = trackId,
            sourcePeerId = sourcePeerId,
            stage = stage,
            code = code,
            blame = blame,
            retryable = retryable,
            message = message,
        )
    }

    fun cancel(trackId: TrackId, reason: String = "Transfer cancelled") {
        cancellationRegistry.cancel(trackId, reason)
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

    private class TransferStageException(
        val stage: String,
        cause: Throwable,
    ) : Exception(cause.message, cause)

    companion object {
        private const val TAG = "TransferManager"
        private const val MAX_TRACK_SIZE_BYTES = 1_073_741_824L // 1 GiB
        private const val MAX_TRACKED_AUTHORIZATIONS = 512
        private const val MIN_FREE_SPACE_BYTES = 32L * 1024L * 1024L
        private const val UPLOAD_WATCHDOG_INTERVAL_MS = 5_000L
        private const val UPLOAD_IDLE_TIMEOUT_MS = 30_000L
    }
}
