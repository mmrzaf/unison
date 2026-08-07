package com.darius.unison.model

import java.util.UUID
import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class PeerId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
value class TrackId(val value: String) {
    override fun toString(): String = value
}

@JvmInline
@Serializable
value class QueueItemId(val value: String) {
    override fun toString(): String = value
}

@Serializable
data class LocalIdentity(
    val peerId: PeerId,
    val displayName: String,
)

@Serializable
data class PeerEndpoint(
    val peerId: PeerId,
    val displayName: String,
    val hostAddress: String,
    val port: Int,
    val appVersion: String,
    val lastSeenElapsedMs: Long = 0,
)

@Serializable
data class TrackDescriptor(
    val trackId: TrackId,
    val sizeBytes: Long,
    val mimeType: String? = null,
    val durationMs: Long = 0,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val originalFileName: String? = null,
) {
    val displayTitle: String
        get() =
            title?.takeIf { it.isNotBlank() }
                ?: originalFileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
                ?: "Unknown track"
}

@Serializable
data class QueueItem(
    val queueItemId: QueueItemId,
    val track: TrackDescriptor,
    val addedByPeerId: PeerId,
    val addedAtSequence: Long,
) {
    companion object {
        fun create(track: TrackDescriptor, addedBy: PeerId, sequence: Long = 0): QueueItem =
            QueueItem(
                queueItemId = QueueItemId(UUID.randomUUID().toString()),
                track = track,
                addedByPeerId = addedBy,
                addedAtSequence = sequence,
            )
    }
}

@Serializable
enum class LocalPlaybackParticipation {
    ACTIVE,
    OUTPUT_INHIBITED,
    REJOINING,
}

@Serializable
enum class LocalPlaybackInhibitionReason {
    AUDIO_FOCUS,
    BECOMING_NOISY,
    UNSUITABLE_OUTPUT,
    /** Vendor/OS playback policy changed local intent without a Unison-owned mutation. */
    SYSTEM_POLICY,
}

@Serializable
enum class RetentionPolicy {
    EXTERNAL_REFERENCE,
    TEMPORARY_24_HOURS,
    KEEP_IN_LIBRARY,
}

@Serializable
enum class TrackSourceType {
    PERSISTED_DOCUMENT_URI,
    APP_MANAGED_FILE,
}

@Serializable
enum class MemberTrackState {
    UNKNOWN,
    CHECKING,
    NEEDS_FILE,
    RECEIVING,
    VERIFYING,
    PREPARING_PLAYER,
    READY,
    CANCELLED,
    FAILED,
}

@Serializable
data class MemberSnapshot(
    val peerId: PeerId,
    val displayName: String,
    val endpoint: PeerEndpoint? = null,
    val connected: Boolean = true,
    val currentTrackState: MemberTrackState = MemberTrackState.UNKNOWN,
)

@Serializable
enum class RepeatMode {
    OFF,
    ALL,
    ONE,
}

@Serializable
data class RoomOptions(
    val waitAtTrackBoundary: Boolean = true,
    val preloadCount: Int = 3,
)

@Serializable
data class CanonicalPlaybackState(
    val queueItemId: QueueItemId? = null,
    val positionAtTimestampMs: Long = 0,
    val coordinatorTimestampNs: Long = 0,
    val isPlaying: Boolean = false,
    val playbackSpeed: Float = 1f,
    /**
     * Monotonic identity of the canonical playback intent. Zero represents the initial idle state.
     */
    val revision: Long = 0,
) {
    fun projectedPositionMs(atCoordinatorNs: Long): Long {
        if (!isPlaying || coordinatorTimestampNs <= 0) return positionAtTimestampMs.coerceAtLeast(0)
        val elapsedMs = ((atCoordinatorNs - coordinatorTimestampNs).coerceAtLeast(0) / 1_000_000.0)
        return (positionAtTimestampMs + elapsedMs * playbackSpeed).toLong().coerceAtLeast(0)
    }

    /**
     * Produces a state-sync view without destroying a future scheduled command. Once the command's
     * room timestamp has passed, the position is materialized at [atCoordinatorNs] so receivers can
     * compare it directly with their local player.
     */
    fun forStateSync(atCoordinatorNs: Long): CanonicalPlaybackState {
        if (coordinatorTimestampNs > atCoordinatorNs) return this
        return copy(
            positionAtTimestampMs = projectedPositionMs(atCoordinatorNs),
            coordinatorTimestampNs = atCoordinatorNs,
        )
    }
}

@Serializable
enum class TransportAction {
    PLAY,
    PAUSE,
    SEEK,
    NEXT,
    PREVIOUS,
    PLAY_ITEM,
}

@Serializable
enum class TransportCommandPhase {
    SUBMITTED,
    ACCEPTED,
    SCHEDULED,
    EXECUTING,
    SETTLED,
    SUPERSEDED,
    REJECTED;

    val isTerminal: Boolean
        get() = this == SETTLED || this == SUPERSEDED || this == REJECTED
}

data class TransportCommandStatus(
    val commandId: String,
    val action: TransportAction,
    val phase: TransportCommandPhase,
    val queueItemId: QueueItemId? = null,
    val requestedPositionMs: Long? = null,
    val message: String? = null,
) {
    val active: Boolean
        get() =
            phase == TransportCommandPhase.SUBMITTED ||
                phase == TransportCommandPhase.ACCEPTED ||
                phase == TransportCommandPhase.SCHEDULED ||
                phase == TransportCommandPhase.EXECUTING
}

@Serializable
data class CoordinatorTerm(
    val number: Long,
    val coordinatorPeerId: PeerId,
)

@Serializable
data class RoomSnapshot(
    val roomId: String,
    val roomName: String,
    val term: CoordinatorTerm,
    val sequence: Long,
    val options: RoomOptions = RoomOptions(),
    val members: List<MemberSnapshot> = emptyList(),
    val queue: List<QueueItem> = emptyList(),
    val preparedQueueItemIds: Set<QueueItemId> = emptySet(),
    val playback: CanonicalPlaybackState = CanonicalPlaybackState(),
    val repeatMode: RepeatMode = RepeatMode.OFF,
    /** Sequence of the most recent mutation that changed queue membership or ordering. */
    val queueRevision: Long = 0,
)

@Serializable
data class DiscoveredRoom(
    val serviceName: String,
    val roomId: String,
    val roomName: String,
    val hostAddress: String,
    val port: Int,
    val protocolVersion: Int,
    val term: Long,
)

enum class RoomLifecycleState {
    IDLE,
    PREPARING,
    DISCOVERING,
    CONNECTING,
    JOINING,
    CONNECTED,
    RECONNECTING,
    ENDING,
    FAILED,
}

enum class UserFacingStatus {
    IDLE,
    PREPARING,
    RECEIVING,
    READY,
    SYNCING,
    RECONNECTING,
    UNAVAILABLE,
}

data class TransferProgress(
    val trackId: TrackId,
    val bytesTransferred: Long,
    val totalBytes: Long,
    val sourcePeerId: PeerId?,
    val destinationPeerId: PeerId?,
    val state: MemberTrackState,
    val error: String? = null,
) {
    val fraction: Float
        get() =
            if (totalBytes <= 0) 0f
            else (bytesTransferred.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

data class RoomUiState(
    val lifecycle: RoomLifecycleState = RoomLifecycleState.IDLE,
    val localIdentity: LocalIdentity? = null,
    val snapshot: RoomSnapshot? = null,
    val isCoordinator: Boolean = false,
    val discoveredRooms: List<DiscoveredRoom> = emptyList(),
    /**
     * True after a user-triggered discovery window finishes. Cleared by the next scan or when
     * leaving the lobby so the UI can distinguish "not searched" from "no rooms found".
     */
    val discoveryCompleted: Boolean = false,
    val transfers: Map<TrackId, TransferProgress> = emptyMap(),
    val status: UserFacingStatus = UserFacingStatus.IDLE,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val issue: RoomIssue? = null,
    val localPlaybackPositionMs: Long = 0,
    val localPlaybackQueueItemId: QueueItemId? = null,
    val localIsPlaying: Boolean = false,
    val localPlaybackParticipation: LocalPlaybackParticipation = LocalPlaybackParticipation.ACTIVE,
    val localPlaybackInhibitionReason: LocalPlaybackInhibitionReason? = null,
    val localSeekRevision: Long = 0,
    val localDriftMs: Long? = null,
    val transportStatus: TransportCommandStatus? = null,
    val roomAddress: String? = null,
    val roomPort: Int? = null,
    /**
     * Coordinator-local manual PIN. Never serialized into canonical room state or sent to peers.
     */
    val localRoomPin: String? = null,
    val hotspot: HotspotInfo? = null,
) {
    /** Any finite room operation that needs the service to remain alive. */
    val operationActive: Boolean
        get() = lifecycle != RoomLifecycleState.IDLE && lifecycle != RoomLifecycleState.FAILED

    /**
     * A joined/creating room session that needs foreground networking and media controls. Manual
     * discovery is intentionally excluded so an eight-second scan does not behave like a room.
     */
    val sessionActive: Boolean
        get() =
            when (lifecycle) {
                RoomLifecycleState.PREPARING,
                RoomLifecycleState.CONNECTING,
                RoomLifecycleState.JOINING,
                RoomLifecycleState.CONNECTED,
                RoomLifecycleState.RECONNECTING,
                RoomLifecycleState.ENDING -> true

                RoomLifecycleState.IDLE,
                RoomLifecycleState.DISCOVERING,
                RoomLifecycleState.FAILED -> false
            }
}

@Serializable
data class HotspotInfo(
    val ssid: String,
    val passphrase: String?,
    val securityType: Int,
)
