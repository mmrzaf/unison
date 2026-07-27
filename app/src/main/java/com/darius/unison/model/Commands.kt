package com.darius.unison.model

import kotlinx.serialization.Serializable
import java.util.UUID

sealed interface AppCommand {
    data class CreateRoom(val roomName: String? = null) : AppCommand
    data class JoinRoom(val room: DiscoveredRoom, val pin: String) : AppCommand
    data object StartDiscovery : AppCommand
    data object StopDiscovery : AppCommand
    data object LeaveRoom : AppCommand
    data object CreateOfflineNetwork : AppCommand
    data object StopOfflineNetwork : AppCommand
    data class AddTracks(val trackIds: List<TrackId>) : AppCommand
    data class SaveDisplayName(val name: String) : AppCommand
    data class KeepTrack(val trackId: TrackId) : AppCommand
    data class RemoveTemporaryTrack(val trackId: TrackId) : AppCommand
    data object Play : AppCommand
    data object Pause : AppCommand
    data class Seek(val positionMs: Long) : AppCommand
    data object SkipNext : AppCommand
    data object SkipPrevious : AppCommand
    data class RemoveQueueItem(val queueItemId: QueueItemId) : AppCommand
    data class MoveQueueItem(val queueItemId: QueueItemId, val newIndex: Int) : AppCommand
    data class UpdateRoomOptions(val options: RoomOptions) : AppCommand
}

@Serializable
sealed interface UserCommand {
    val commandId: String
    val requestedBy: PeerId

    @Serializable
    data class Play(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
    ) : UserCommand

    @Serializable
    data class Pause(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
    ) : UserCommand

    @Serializable
    data class Seek(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val positionMs: Long,
    ) : UserCommand

    @Serializable
    data class SkipNext(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
    ) : UserCommand

    @Serializable
    data class SkipPrevious(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
    ) : UserCommand

    @Serializable
    data class QueueAdd(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val tracks: List<TrackDescriptor>,
    ) : UserCommand

    @Serializable
    data class QueueRemove(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val queueItemId: QueueItemId,
    ) : UserCommand

    @Serializable
    data class QueueMove(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val queueItemId: QueueItemId,
        val newIndex: Int,
    ) : UserCommand

    @Serializable
    data class OptionsChange(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val options: RoomOptions,
    ) : UserCommand
}
