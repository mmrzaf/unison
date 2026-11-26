package com.darius.unison.room.port

import com.darius.unison.model.LocalIdentity
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import com.darius.unison.protocol.HandshakeMessage
import com.darius.unison.transfer.TransferCapacityPolicy
import com.darius.unison.transfer.TransferFailure
import java.io.File
import java.net.Socket

/**
 * Moves track bytes between phones for one room session. The room decides who sends what to whom;
 * this port only authorizes, uploads and downloads, and reports outcomes through [TransferHost].
 */
interface TransferPort {
    val activeTransferCount: Int

    val pendingAuthorizationCount: Int

    fun authorize(
        roomId: String,
        trackId: TrackId,
        destinationPeerId: PeerId,
        token: String,
        expiresAtElapsedMs: Long,
    )

    fun download(
        roomId: String,
        track: TrackDescriptor,
        source: PeerEndpoint,
        authorizationToken: String,
    )

    suspend fun handleIncomingFileSocket(socket: Socket, hello: HandshakeMessage.FileClientHello)

    fun cancelAll()

    suspend fun cancelAllAndJoin(timeoutMs: Long): Boolean
}

/** What a transfer implementation needs from, and reports to, the room that owns it. */
class TransferHost(
    val localIdentity: LocalIdentity,
    val localDisplayName: () -> String,
    val listeningPort: () -> Int,
    val capacityPolicy: TransferCapacityPolicy,
    val onProgress: (TransferProgress) -> Unit,
    val onCompleted: suspend (TrackDescriptor, File) -> Unit,
    val onFailed: suspend (TransferFailure) -> Unit,
)

fun interface TransferPortFactory {
    fun create(host: TransferHost): TransferPort
}
