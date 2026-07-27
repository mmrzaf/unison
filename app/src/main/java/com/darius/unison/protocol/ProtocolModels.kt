package com.darius.unison.protocol

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.UserCommand
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val PROTOCOL_VERSION = 1
const val MAX_CONTROL_PAYLOAD_BYTES = 256 * 1024

@Serializable
enum class ChannelType { CONTROL, FILE }

@Serializable
data class Envelope(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val roomId: String,
    val term: Long,
    val coordinatorPeerId: PeerId? = null,
    val senderPeerId: PeerId,
    val sequence: Long? = null,
    val messageId: String,
    val sentAtElapsedNs: Long,
    val body: ProtocolBody,
)

@Serializable
sealed interface ProtocolBody {
    @Serializable
    @SerialName("join_accepted")
    data class JoinAccepted(val snapshot: RoomSnapshot, val peerDirectory: List<PeerEndpoint>) : ProtocolBody

    @Serializable
    @SerialName("peer_joined")
    data class PeerJoined(val member: MemberSnapshot) : ProtocolBody

    @Serializable
    @SerialName("peer_updated")
    data class PeerUpdated(val member: MemberSnapshot) : ProtocolBody

    @Serializable
    @SerialName("peer_left")
    data class PeerLeft(val peerId: PeerId) : ProtocolBody

    @Serializable
    @SerialName("peer_directory")
    data class PeerDirectory(val peers: List<PeerEndpoint>) : ProtocolBody

    @Serializable
    @SerialName("endpoint_announcement")
    data class EndpointAnnouncement(val endpoint: PeerEndpoint) : ProtocolBody

    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(val lastAppliedSequence: Long) : ProtocolBody

    @Serializable
    @SerialName("leave_room")
    data class LeaveRoom(val reason: String? = null) : ProtocolBody

    @Serializable
    @SerialName("snapshot_request")
    data class SnapshotRequest(val lastAppliedSequence: Long) : ProtocolBody

    @Serializable
    @SerialName("snapshot")
    data class Snapshot(val snapshot: RoomSnapshot) : ProtocolBody

    @Serializable
    @SerialName("ack_sequence")
    data class AckSequence(val sequence: Long) : ProtocolBody

    @Serializable
    @SerialName("rejoin_request")
    data class RejoinRequest(
        val lastAppliedSequence: Long,
        val cachedTrackIds: List<TrackId>,
        val listeningPort: Int,
    ) : ProtocolBody

    @Serializable
    @SerialName("user_command")
    data class UserCommandRequest(val command: UserCommand) : ProtocolBody

    @Serializable
    @SerialName("command_rejected")
    data class CommandRejected(val commandId: String, val reason: String) : ProtocolBody

    @Serializable
    @SerialName("queue_item_added")
    data class QueueItemAdded(val item: QueueItem) : ProtocolBody

    @Serializable
    @SerialName("queue_item_removed")
    data class QueueItemRemoved(val queueItemId: QueueItemId) : ProtocolBody

    @Serializable
    @SerialName("queue_item_moved")
    data class QueueItemMoved(val queueItemId: QueueItemId, val newIndex: Int) : ProtocolBody

    @Serializable
    @SerialName("queue_item_preparation")
    data class QueueItemPreparation(val queueItemId: QueueItemId, val prepared: Boolean) : ProtocolBody

    @Serializable
    @SerialName("room_options_changed")
    data class RoomOptionsChanged(val options: RoomOptions) : ProtocolBody

    @Serializable
    @SerialName("play_scheduled")
    data class PlayScheduled(
        val queueItemId: QueueItemId,
        val positionMs: Long,
        val executeAtCoordinatorNs: Long,
    ) : ProtocolBody

    @Serializable
    @SerialName("pause_scheduled")
    data class PauseScheduled(
        val positionMs: Long,
        val executeAtCoordinatorNs: Long,
    ) : ProtocolBody

    @Serializable
    @SerialName("seek_scheduled")
    data class SeekScheduled(
        val queueItemId: QueueItemId,
        val positionMs: Long,
        val resumePlayback: Boolean,
        val executeAtCoordinatorNs: Long,
    ) : ProtocolBody

    @Serializable
    @SerialName("current_item_changed")
    data class CurrentItemChanged(
        val queueItemId: QueueItemId?,
        val positionMs: Long,
        val executeAtCoordinatorNs: Long,
        val resumePlayback: Boolean,
    ) : ProtocolBody

    @Serializable
    @SerialName("clock_ping")
    data class ClockPing(val pingId: String, val guestSendNs: Long) : ProtocolBody

    @Serializable
    @SerialName("clock_pong")
    data class ClockPong(
        val pingId: String,
        val guestSendNs: Long,
        val coordinatorReceiveNs: Long,
        val coordinatorSendNs: Long,
    ) : ProtocolBody

    @Serializable
    @SerialName("clock_ready")
    data class ClockReady(val synchronized: Boolean = true) : ProtocolBody

    @Serializable
    @SerialName("playback_state_sync")
    data class PlaybackStateSync(val playback: CanonicalPlaybackState) : ProtocolBody

    @Serializable
    @SerialName("playback_status_report")
    data class PlaybackStatusReport(
        val queueItemId: QueueItemId?,
        val positionMs: Long,
        val isPlaying: Boolean,
        val driftMs: Long,
    ) : ProtocolBody

    /** Ephemeral UI telemetry. It is deliberately not part of canonical room sequencing. */
    @Serializable
    @SerialName("member_playback_status")
    data class MemberPlaybackStatus(
        val peerId: PeerId,
        val queueItemId: QueueItemId?,
        val positionMs: Long,
        val isPlaying: Boolean,
        val driftMs: Long,
    ) : ProtocolBody

    @Serializable
    @SerialName("track_descriptor")
    data class TrackDescriptorMessage(val descriptor: TrackDescriptor) : ProtocolBody

    @Serializable
    @SerialName("track_have")
    data class TrackHave(val trackId: TrackId) : ProtocolBody

    @Serializable
    @SerialName("track_need")
    data class TrackNeed(val trackId: TrackId) : ProtocolBody

    @Serializable
    @SerialName("track_source_assigned")
    data class TrackSourceAssigned(
        val track: TrackDescriptor,
        val source: PeerEndpoint,
        val destinationPeerId: PeerId,
        val authorizationToken: String,
    ) : ProtocolBody

    @Serializable
    @SerialName("track_source_authorized")
    data class TrackSourceAuthorized(
        val trackId: TrackId,
        val destinationPeerId: PeerId,
        val authorizationToken: String,
    ) : ProtocolBody

    @Serializable
    @SerialName("track_ready")
    data class TrackReady(val trackId: TrackId) : ProtocolBody

    @Serializable
    @SerialName("track_failed")
    data class TrackFailed(
        val trackId: TrackId,
        val reason: String,
        val sourcePeerId: PeerId? = null,
    ) : ProtocolBody

    @Serializable
    @SerialName("transfer_cancelled")
    data class TransferCancelled(val trackId: TrackId, val reason: String? = null) : ProtocolBody

}

@Serializable
sealed interface HandshakeMessage {
    @Serializable
    @SerialName("client_hello")
    data class ClientHello(
        val channel: ChannelType,
        val peerId: PeerId,
        val displayName: String,
        val appVersion: String,
        val protocolVersions: List<Int>,
        val listeningPort: Int,
        val roomId: String,
        val clientNonce: String,
        val pinProof: String? = null,
        val fileRequest: FileRequest? = null,
    ) : HandshakeMessage

    @Serializable
    @SerialName("coordinator_hello")
    data class CoordinatorHello(
        val acceptedVersion: Int,
        val term: Long,
        val coordinatorPeerId: PeerId,
        val serverNonce: String,
        val encryptedRoomSecretBase64: String,
        val roomSecretIvBase64: String,
        val snapshotSequence: Long,
    ) : HandshakeMessage

    @Serializable
    @SerialName("accepted")
    data class Accepted(val serverNonce: String) : HandshakeMessage

    @Serializable
    @SerialName("rejected")
    data class Rejected(val reason: String) : HandshakeMessage
}

@Serializable
data class FileRequest(
    val requestId: String,
    val roomId: String,
    val trackId: TrackId,
    val offset: Long,
    val authorizationToken: String,
)

@Serializable
enum class FileResponseStatus { OK, NOT_FOUND, UNAUTHORIZED, INVALID_OFFSET, BUSY, ERROR }

@Serializable
data class FileResponseHeader(
    val requestId: String,
    val status: FileResponseStatus,
    val trackId: TrackId,
    val totalSize: Long,
    val acceptedOffset: Long,
    val message: String? = null,
)
