package com.darius.unison.model

import java.util.UUID
import kotlinx.serialization.Serializable

sealed interface RoomJoinCredential {
    data class Pin(val value: String) : RoomJoinCredential
}

sealed interface AppCommand {
    sealed interface Transport : AppCommand {
        val commandId: String
    }

    data class CreateRoom(val roomName: String? = null) : AppCommand

    data class JoinRoom(val room: DiscoveredRoom, val credential: RoomJoinCredential) : AppCommand

    data object StartDiscovery : AppCommand

    data object StopDiscovery : AppCommand

    data object LeaveRoom : AppCommand

    data object CreateOfflineNetwork : AppCommand

    data object StopOfflineNetwork : AppCommand

    data class AddTracks(val trackIds: List<TrackId>, val insertAfterCurrent: Boolean = false) :
        AppCommand

    data class SaveDisplayName(val name: String) : AppCommand

    data class KeepTrack(val trackId: TrackId) : AppCommand

    data class RemoveTemporaryTrack(val trackId: TrackId) : AppCommand

    data class Play(override val commandId: String = UUID.randomUUID().toString()) : Transport

    data class Pause(override val commandId: String = UUID.randomUUID().toString()) : Transport

    data class Seek(
        val positionMs: Long,
        override val commandId: String = UUID.randomUUID().toString(),
    ) : Transport

    data class SkipNext(override val commandId: String = UUID.randomUUID().toString()) : Transport

    data class SkipPrevious(override val commandId: String = UUID.randomUUID().toString()) :
        Transport

    data class PlayQueueItem(
        val queueItemId: QueueItemId,
        override val commandId: String = UUID.randomUUID().toString(),
    ) : Transport

    /** Explicitly asks the room to make this queue item playable; it never changes playback. */
    data class PrepareQueueItem(
        val queueItemId: QueueItemId,
        val requestId: String = UUID.randomUUID().toString(),
        val retryPeerId: PeerId? = null,
    ) : AppCommand

    data object ShuffleQueue : AppCommand

    data class SetRepeat(val mode: RepeatMode) : AppCommand

    data class RemoveQueueItem(val queueItemId: QueueItemId) : AppCommand

    data class MoveQueueItem(val queueItemId: QueueItemId, val newIndex: Int) : AppCommand

    data class MoveQueueItemNext(val queueItemId: QueueItemId) : AppCommand

    data object ClearPlayed : AppCommand

    data object ClearQueue : AppCommand

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
    data class PlayQueueItem(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val queueItemId: QueueItemId,
        val resumePlayback: Boolean = true,
    ) : UserCommand

    @Serializable
    data class QueueAdd(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val tracks: List<TrackDescriptor>,
        val insertAfterCurrent: Boolean = false,
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
    data class QueueMoveAfterCurrent(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val queueItemId: QueueItemId,
    ) : UserCommand

    @Serializable
    data class QueueClearPlayed(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
    ) : UserCommand

    @Serializable
    data class QueueClear(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
    ) : UserCommand

    @Serializable
    data class QueueShuffle(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val shuffleSeed: Long,
        val preserveNextQueueItemId: QueueItemId? = null,
    ) : UserCommand

    @Serializable
    data class RepeatModeChange(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val repeatMode: RepeatMode,
    ) : UserCommand

    @Serializable
    data class OptionsChange(
        override val commandId: String = UUID.randomUUID().toString(),
        override val requestedBy: PeerId,
        val options: RoomOptions,
    ) : UserCommand
}

fun AppCommand.Transport.transportAction(): TransportAction =
    when (this) {
        is AppCommand.Play -> TransportAction.PLAY
        is AppCommand.Pause -> TransportAction.PAUSE
        is AppCommand.Seek -> TransportAction.SEEK
        is AppCommand.SkipNext -> TransportAction.NEXT
        is AppCommand.SkipPrevious -> TransportAction.PREVIOUS
        is AppCommand.PlayQueueItem -> TransportAction.PLAY_ITEM
    }

fun UserCommand.transportActionOrNull(): TransportAction? =
    when (this) {
        is UserCommand.Play -> TransportAction.PLAY
        is UserCommand.Pause -> TransportAction.PAUSE
        is UserCommand.Seek -> TransportAction.SEEK
        is UserCommand.SkipNext -> TransportAction.NEXT
        is UserCommand.SkipPrevious -> TransportAction.PREVIOUS
        is UserCommand.PlayQueueItem -> TransportAction.PLAY_ITEM
        else -> null
    }
