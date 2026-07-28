package com.darius.unison.ui

import android.app.Application
import android.net.Uri
import com.darius.unison.app.AppContainer
import com.darius.unison.model.AppCommand
import com.darius.unison.model.DiscoveredRoom
import com.darius.unison.model.TrackId
import com.darius.unison.network.NetworkAddressPolicy
import com.darius.unison.playback.UnisonRoomService
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.room.RoomReducer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Room-session intents and join-link encoding, kept out of the presentation-state coordinator. */
internal class RoomSessionActions(
    private val application: Application,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val message: MutableStateFlow<String?>,
) {
    fun command(command: AppCommand, feedback: String? = command.feedbackMessage()) {
        UnisonRoomService.start(application)
        if (container.roomCommandBus.trySend(command).isSuccess) {
            feedback?.let { message.value = it }
        } else {
            message.value = "Unison is busy. Try again."
        }
    }

    fun addTracksToRoom(trackIds: List<TrackId>, insertAfterCurrent: Boolean = false) {
        if (trackIds.isEmpty()) {
            message.value = "Select at least one song"
            return
        }
        val queueSize = container.roomStore.structure.value.snapshot?.queue?.size
        if (queueSize == null) {
            message.value = "Join or create a room first"
            return
        }
        val availableSlots = (RoomReducer.MAX_QUEUE_ITEMS - queueSize).coerceAtLeast(0)
        val selectedTracks = trackIds.take(availableSlots)
        if (selectedTracks.isEmpty()) {
            message.value = "The room queue is full"
            return
        }
        command(
            AppCommand.AddTracks(selectedTracks, insertAfterCurrent),
            feedback = when {
                insertAfterCurrent -> "Playing next"
                selectedTracks.size < trackIds.size ->
                    "Adding ${selectedTracks.size} songs; the queue holds up to ${RoomReducer.MAX_QUEUE_ITEMS}"

                selectedTracks.size == 1 -> "Added to the queue"
                else -> "Adding ${selectedTracks.size} songs"
            },
        )
    }

    fun loadTrackIds(query: String, onLoaded: (Set<TrackId>) -> Unit) {
        scope.launch {
            userResult { container.trackRepository.libraryTrackIds(query) }
                .onSuccess(onLoaded)
                .onFailure { message.value = "Could not select this music" }
        }
    }

    fun clearRoomError(expected: String) {
        container.roomStore.updateStructure { state ->
            if (state.errorMessage == expected) state.copy(errorMessage = null) else state
        }
    }

    fun joinLink(): String? = RoomJoinLinkCodec.encode(container)

    private suspend fun <T> userResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}

internal object RoomJoinLinkCodec {
    fun encode(container: AppContainer): String? {
        val room = container.roomStore.structure.value
        val snapshot = room.snapshot ?: return null
        val host = room.roomAddress ?: return null
        val port = room.roomPort ?: return null
        val pin = room.localRoomPin ?: return null
        return Uri.Builder()
            .scheme("unison")
            .authority("join")
            .appendQueryParameter("roomId", snapshot.roomId)
            .appendQueryParameter("name", snapshot.roomName)
            .appendQueryParameter("host", host)
            .appendQueryParameter("port", port.toString())
            .appendQueryParameter("pin", pin)
            .appendQueryParameter("v", PROTOCOL_VERSION.toString())
            .build().toString()
    }

    fun decode(uri: Uri): Pair<DiscoveredRoom, String>? {
        if (uri.scheme != "unison" || uri.authority != "join") return null
        val roomId = uri.getQueryParameter("roomId")
            ?.takeIf { it.length in 8..128 && ROOM_ID_PATTERN.matches(it) }
            ?: return null
        val host = uri.getQueryParameter("host")
            ?.let { NetworkAddressPolicy.parseAllowedIpv4(it) }
            ?.hostAddress
            ?: return null
        val port = uri.getQueryParameter("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val pin = uri.getQueryParameter("pin")?.takeIf(PIN_PATTERN::matches) ?: return null
        val version = uri.getQueryParameter("v")?.toIntOrNull() ?: return null
        if (version != PROTOCOL_VERSION) return null
        val roomName = uri.getQueryParameter("name")
            ?.filterNot { it.isISOControl() }
            ?.trim()
            ?.take(60)
            ?.ifBlank { null }
            ?: "Unison room"
        return DiscoveredRoom(
            serviceName = "QR",
            roomId = roomId,
            roomName = roomName,
            hostAddress = host,
            port = port,
            protocolVersion = version,
            term = 1,
        ) to pin
    }

    private val ROOM_ID_PATTERN = Regex("[A-Za-z0-9-]+")
    private val PIN_PATTERN = Regex("[0-9]{6}")
}

internal fun AppCommand.feedbackMessage(): String? = when (this) {
    AppCommand.Play,
    AppCommand.Pause,
    is AppCommand.Seek,
    AppCommand.SkipNext,
    AppCommand.SkipPrevious,
    is AppCommand.PlayQueueItem,
        -> null

    AppCommand.ShuffleQueue,
    is AppCommand.SetRepeat,
    is AppCommand.RemoveQueueItem,
    is AppCommand.MoveQueueItem,
    AppCommand.ClearPlayed,
    is AppCommand.UpdateRoomOptions,
    AppCommand.LeaveRoom,
        -> null

    is AppCommand.AddTracks -> when {
        trackIds.isEmpty() -> null
        insertAfterCurrent -> "Playing next"
        trackIds.size == 1 -> "Added to the queue"
        else -> "Adding ${trackIds.size} songs"
    }

    else -> null
}
