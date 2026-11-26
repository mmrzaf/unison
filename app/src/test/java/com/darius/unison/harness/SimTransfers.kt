package com.darius.unison.harness

import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.protocol.TransferFailureBlame
import com.darius.unison.protocol.TransferFailureCode
import com.darius.unison.protocol.TransferFailureStage
import com.darius.unison.room.port.TransferHost
import com.darius.unison.room.port.TransferPort
import com.darius.unison.room.port.TransferPortFactory
import com.darius.unison.transfer.TransferFailure
import java.io.File
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** What happened to one simulated download, for scenario assertions. */
data class SimTransferRecord(
    val trackId: TrackId,
    val sourceAddress: String,
    val outcome: Outcome,
) {
    enum class Outcome {
        STARTED,
        COMPLETED,
        FAILED,
    }
}

/**
 * In-memory data plane for every simulated device. The room runtime's transfer orchestration
 * (assignment, authorization, retry, blocking) runs for real; only the byte movement is simulated,
 * on virtual time. Byte-level protocol behavior has its own socket tests.
 */
class SimTransferHub(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val bytesPerSecond: Long = 2_000_000L,
) {
    private val ports = ConcurrentHashMap<String, SimTransferPort>()
    private val blockedLinks = ConcurrentHashMap.newKeySet<Pair<String, String>>()
    private val injected = CopyOnWriteArrayList<InjectedFailure>()
    val records = CopyOnWriteArrayList<SimTransferRecord>()

    internal data class InjectedFailure(
        val trackId: TrackId,
        val sourceAddress: String?,
        val failure: (PeerId?) -> TransferFailure,
        var remaining: Int,
    )

    fun factory(address: String, tracks: SimTrackStore, directory: File): TransferPortFactory =
        TransferPortFactory { host ->
            SimTransferPort(this, host, address, tracks, directory).also { ports[address] = it }
        }

    /** Blocks the data path between two devices (control traffic is partitioned separately). */
    fun blockLink(a: String, b: String) {
        blockedLinks += a to b
        blockedLinks += b to a
    }

    fun healLinks() = blockedLinks.clear()

    /** Fails the next [times] downloads of [trackId] from [sourceAddress] (any source if null). */
    fun failNext(
        trackId: TrackId,
        sourceAddress: String? = null,
        times: Int = 1,
        code: TransferFailureCode = TransferFailureCode.CONNECT_FAILED,
        blame: TransferFailureBlame = TransferFailureBlame.ROUTE,
        retryable: Boolean = true,
    ) {
        injected +=
            InjectedFailure(
                trackId = trackId,
                sourceAddress = sourceAddress,
                remaining = times,
                failure = { source ->
                    TransferFailure(
                        trackId = trackId,
                        sourcePeerId = source,
                        stage = TransferFailureStage.CONNECT,
                        code = code,
                        blame = blame,
                        retryable = retryable,
                        message = "injected $code",
                    )
                },
            )
    }

    fun attempts(trackId: TrackId): Int = records.count {
        it.trackId == trackId && it.outcome == SimTransferRecord.Outcome.STARTED
    }

    internal fun portAt(address: String): SimTransferPort? = ports[address]

    internal fun isBlocked(from: String, to: String): Boolean = (from to to) in blockedLinks

    internal fun takeInjected(trackId: TrackId, sourceAddress: String): InjectedFailure? =
        synchronized(injected) {
            injected
                .firstOrNull {
                    it.trackId == trackId &&
                        (it.sourceAddress == null || it.sourceAddress == sourceAddress) &&
                        it.remaining > 0
                }
                ?.also { it.remaining-- }
        }

    internal fun launch(block: suspend () -> Unit): Job = scope.launch(dispatcher) { block() }

    internal fun transferMillis(sizeBytes: Long): Long =
        (sizeBytes * 1_000L / bytesPerSecond).coerceAtLeast(1L)

    internal fun failureOf(injected: InjectedFailure, source: PeerId?): TransferFailure =
        injected.failure(source)
}

class SimTransferPort(
    private val hub: SimTransferHub,
    private val host: TransferHost,
    private val address: String,
    private val tracks: SimTrackStore,
    private val directory: File,
) : TransferPort {
    private data class Authorization(
        val roomId: String,
        val trackId: TrackId,
        val destination: PeerId,
    )

    private val authorizations = ConcurrentHashMap<String, Authorization>()
    private val jobs = ConcurrentHashMap.newKeySet<Job>()

    override val activeTransferCount: Int
        get() = jobs.count { it.isActive }

    override val pendingAuthorizationCount: Int
        get() = authorizations.size

    override fun authorize(
        roomId: String,
        trackId: TrackId,
        destinationPeerId: PeerId,
        token: String,
        expiresAtElapsedMs: Long,
    ) {
        authorizations[token] = Authorization(roomId, trackId, destinationPeerId)
    }

    override fun download(
        roomId: String,
        track: TrackDescriptor,
        source: PeerEndpoint,
        authorizationToken: String,
    ) {
        val destination = host.localIdentity.peerId
        lateinit var job: Job
        job = hub.launch {
            record(track.trackId, source.hostAddress, SimTransferRecord.Outcome.STARTED)
            try {
                val failure = attempt(roomId, track, source, authorizationToken, destination)
                if (failure != null) {
                    record(track.trackId, source.hostAddress, SimTransferRecord.Outcome.FAILED)
                    host.onFailed(failure)
                }
            } finally {
                jobs -= job
            }
        }
        jobs += job
    }

    private suspend fun attempt(
        roomId: String,
        track: TrackDescriptor,
        source: PeerEndpoint,
        token: String,
        destination: PeerId,
    ): TransferFailure? {
        fun failure(
            stage: TransferFailureStage,
            code: TransferFailureCode,
            blame: TransferFailureBlame,
            retryable: Boolean,
        ) =
            TransferFailure(
                trackId = track.trackId,
                sourcePeerId = source.peerId,
                stage = stage,
                code = code,
                blame = blame,
                retryable = retryable,
                message = "simulated $code",
            )

        val sourcePort = hub.portAt(source.hostAddress)
        if (sourcePort == null || hub.isBlocked(address, source.hostAddress)) {
            return failure(
                TransferFailureStage.CONNECT,
                TransferFailureCode.CONNECT_FAILED,
                TransferFailureBlame.ROUTE,
                retryable = true,
            )
        }
        delay(LINK_LATENCY_MS)
        if (sourcePort.authorizations[token] != Authorization(roomId, track.trackId, destination)) {
            return failure(
                TransferFailureStage.HANDSHAKE,
                TransferFailureCode.AUTHENTICATION,
                TransferFailureBlame.SOURCE,
                retryable = false,
            )
        }
        hub.takeInjected(track.trackId, source.hostAddress)?.let { injected ->
            return hub.failureOf(injected, source.peerId)
        }
        val sourceFile =
            sourcePort.tracks.fileOf(track.trackId)
                ?: return failure(
                    TransferFailureStage.VALIDATE,
                    TransferFailureCode.SOURCE_UNAVAILABLE,
                    TransferFailureBlame.SOURCE,
                    retryable = true,
                )

        val steps = PROGRESS_STEPS
        for (step in 1..steps) {
            delay(hub.transferMillis(track.sizeBytes) / steps)
            host.onProgress(
                TransferProgress(
                    trackId = track.trackId,
                    bytesTransferred = track.sizeBytes * step / steps,
                    totalBytes = track.sizeBytes,
                    sourcePeerId = source.peerId,
                    destinationPeerId = destination,
                    state = MemberTrackState.RECEIVING,
                )
            )
            if (hub.isBlocked(address, source.hostAddress)) {
                return failure(
                    TransferFailureStage.BODY,
                    TransferFailureCode.IO,
                    TransferFailureBlame.ROUTE,
                    retryable = true,
                )
            }
        }
        val file = File(directory, "tracks/${track.trackId.value}").apply { parentFile?.mkdirs() }
        sourceFile.copyTo(file, overwrite = true)
        tracks.publish(track, file)
        record(track.trackId, source.hostAddress, SimTransferRecord.Outcome.COMPLETED)
        host.onProgress(
            TransferProgress(
                trackId = track.trackId,
                bytesTransferred = track.sizeBytes,
                totalBytes = track.sizeBytes,
                sourcePeerId = source.peerId,
                destinationPeerId = destination,
                state = MemberTrackState.READY,
            )
        )
        host.onCompleted(track, file)
        return null
    }

    private fun record(
        trackId: TrackId,
        sourceAddress: String,
        outcome: SimTransferRecord.Outcome,
    ) {
        hub.records += SimTransferRecord(trackId, sourceAddress, outcome)
    }

    override suspend fun handleIncomingFileSocket(
        socket: Socket,
        hello: HandshakeMessage.FileClientHello,
    ) {
        runCatching { socket.close() }
    }

    override fun cancelAll() {
        jobs.forEach { it.cancel() }
        authorizations.clear()
    }

    override suspend fun cancelAllAndJoin(timeoutMs: Long): Boolean {
        cancelAll()
        jobs.toList().joinAll()
        return true
    }

    private companion object {
        const val LINK_LATENCY_MS = 5L
        const val PROGRESS_STEPS = 4
    }
}
