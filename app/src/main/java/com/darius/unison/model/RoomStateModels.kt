package com.darius.unison.model

/**
 * Low-frequency room state. Playback samples and transfer byte counters deliberately live in
 * separate models so a position tick cannot invalidate every room-screen collector.
 */
data class RoomStructureState(
    val lifecycle: RoomLifecycleState = RoomLifecycleState.IDLE,
    val localIdentity: LocalIdentity? = null,
    val snapshot: RoomSnapshot? = null,
    val isCoordinator: Boolean = false,
    val discoveredRooms: List<DiscoveredRoom> = emptyList(),
    val discoveryCompleted: Boolean = false,
    val status: UserFacingStatus = UserFacingStatus.IDLE,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val roomAddress: String? = null,
    val roomPort: Int? = null,
    val localRoomPin: String? = null,
    val hotspot: HotspotInfo? = null,
) {
    val operationActive: Boolean
        get() = lifecycle != RoomLifecycleState.IDLE && lifecycle != RoomLifecycleState.FAILED

    val sessionActive: Boolean
        get() = when (lifecycle) {
            RoomLifecycleState.PREPARING,
            RoomLifecycleState.CONNECTING,
            RoomLifecycleState.JOINING,
            RoomLifecycleState.CONNECTED,
            RoomLifecycleState.RECONNECTING,
            RoomLifecycleState.ENDING,
                -> true

            RoomLifecycleState.IDLE,
            RoomLifecycleState.DISCOVERING,
            RoomLifecycleState.FAILED,
                -> false
        }
}

data class MemberPlaybackTelemetry(
    val positionMs: Long? = null,
    val driftMs: Long? = null,
)

data class RoomPlaybackTelemetry(
    /** Null means the player has not produced a sample yet; zero is a real playback position. */
    val localPositionMs: Long? = null,
    val localQueueItemId: QueueItemId? = null,
    val localIsPlaying: Boolean = false,
    val localSeekRevision: Long = 0,
    val localDriftMs: Long? = null,
    val memberPlayback: Map<PeerId, MemberPlaybackTelemetry> = emptyMap(),
)

data class RoomTransferTelemetry(
    val transfers: Map<TrackId, TransferProgress> = emptyMap(),
)

fun RoomUiState.toStructureState(): RoomStructureState = RoomStructureState(
    lifecycle = lifecycle,
    localIdentity = localIdentity,
    snapshot = snapshot?.copy(
        members = snapshot.members.map { member ->
            member.copy(playbackPositionMs = null, driftMs = null)
        },
    ),
    isCoordinator = isCoordinator,
    discoveredRooms = discoveredRooms,
    discoveryCompleted = discoveryCompleted,
    status = status,
    statusMessage = statusMessage,
    errorMessage = errorMessage,
    roomAddress = roomAddress,
    roomPort = roomPort,
    localRoomPin = localRoomPin,
    hotspot = hotspot,
)

fun RoomUiState.toPlaybackTelemetry(): RoomPlaybackTelemetry = RoomPlaybackTelemetry(
    localPositionMs = localPlaybackPositionMs,
    localQueueItemId = localPlaybackQueueItemId,
    localIsPlaying = localIsPlaying,
    localSeekRevision = localSeekRevision,
    localDriftMs = localDriftMs,
    memberPlayback = snapshot?.members.orEmpty().associate { member ->
        member.peerId to MemberPlaybackTelemetry(member.playbackPositionMs, member.driftMs)
    },
)

fun RoomUiState.toTransferTelemetry(): RoomTransferTelemetry = RoomTransferTelemetry(transfers)

fun RoomStructureState.toUiState(
    playback: RoomPlaybackTelemetry = RoomPlaybackTelemetry(),
    transfer: RoomTransferTelemetry = RoomTransferTelemetry(),
): RoomUiState {
    val enrichedSnapshot = snapshot?.copy(
        members = snapshot.members.map { member ->
            val telemetry = playback.memberPlayback[member.peerId]
            member.copy(
                playbackPositionMs = telemetry?.positionMs,
                driftMs = telemetry?.driftMs,
            )
        },
    )
    return RoomUiState(
        lifecycle = lifecycle,
        localIdentity = localIdentity,
        snapshot = enrichedSnapshot,
        isCoordinator = isCoordinator,
        discoveredRooms = discoveredRooms,
        discoveryCompleted = discoveryCompleted,
        transfers = transfer.transfers,
        status = status,
        statusMessage = statusMessage,
        errorMessage = errorMessage,
        localPlaybackPositionMs = playback.localPositionMs ?: 0L,
        localPlaybackQueueItemId = playback.localQueueItemId,
        localIsPlaying = playback.localIsPlaying,
        localSeekRevision = playback.localSeekRevision,
        localDriftMs = playback.localDriftMs,
        roomAddress = roomAddress,
        roomPort = roomPort,
        localRoomPin = localRoomPin,
        hotspot = hotspot,
    )
}
