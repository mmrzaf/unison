package com.darius.unison.ui

import android.app.Application
import com.darius.unison.app.AppContainer
import com.darius.unison.model.AppCommand
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.TransportCommandStatus
import com.darius.unison.model.transportAction
import com.darius.unison.playback.UnisonRoomService
import com.darius.unison.room.RoomReducer
import com.darius.unison.util.DiagnosticCategory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Room-session intents kept out of the presentation-state coordinator. */
internal class RoomSessionActions(
    private val application: Application,
    private val container: AppContainer,
    private val scope: CoroutineScope,
    private val message: MutableStateFlow<String?>,
) {
    private val log = container.diagnostics.scoped("RoomSessionActions", DiagnosticCategory.APP)

    fun command(command: AppCommand, feedback: String? = command.feedbackMessage()) {
        val transport = command as? AppCommand.Transport
        val commandAttributes =
            mapOf(
                "command.type" to command::class.simpleName,
                "command.id" to transport?.commandId?.take(12),
                "transport.action" to transport?.transportAction()?.name,
                "queue.item_id" to (command as? AppCommand.PlayQueueItem)?.queueItemId?.value?.take(12),
            )
        log.debug("app.command.submitted", attributes = commandAttributes)
        if (!UnisonRoomService.ensureStarted(application)) {
            log.error("app.command.service_start_failed", attributes = commandAttributes)
            message.value = "Unison could not start playback"
            return
        }
        if (command is AppCommand.Transport) {
            container.roomStore.updateStructure { state ->
                state.copy(
                    transportStatus =
                        TransportCommandStatus(
                            commandId = command.commandId,
                            action = command.transportAction(),
                            phase = TransportCommandPhase.SUBMITTED,
                            queueItemId = (command as? AppCommand.PlayQueueItem)?.queueItemId,
                            requestedPositionMs = (command as? AppCommand.Seek)?.positionMs,
                        ),
                    errorMessage = null,
                    issue = null,
                )
            }
        }
        if (container.roomCommandBus.trySend(command).isSuccess) {
            log.debug("app.command.enqueued", attributes = commandAttributes)
            feedback?.let { message.value = it }
        } else {
            log.warn("app.command.rejected", attributes = commandAttributes + ("reason" to "mailbox_full"))
            if (command is AppCommand.Transport) {
                container.roomStore.updateStructure { state ->
                    val status = state.transportStatus
                    if (status?.commandId == command.commandId) {
                        state.copy(
                            transportStatus =
                                status.copy(
                                    phase = TransportCommandPhase.REJECTED,
                                    message = "Unison is busy",
                                )
                        )
                    } else state
                }
            }
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
            feedback =
                when {
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

    fun loadRoomTrackIds(query: String, onLoaded: (Set<TrackId>) -> Unit) {
        val queueSize = container.roomStore.structure.value.snapshot?.queue?.size
        if (queueSize == null) {
            message.value = "Join or create a room first"
            return
        }
        val availableSlots = (RoomReducer.MAX_QUEUE_ITEMS - queueSize).coerceAtLeast(0)
        if (availableSlots == 0) {
            message.value = "The room queue is full"
            return
        }
        scope.launch {
            userResult { container.trackRepository.libraryTrackIds(query, availableSlots) }
                .onSuccess(onLoaded)
                .onFailure { message.value = "Could not load music for the room" }
        }
    }

    fun retryRoomIssue() {
        val status = container.roomStore.structure.value.transportStatus
        val retryCommand = status?.retryCommandOrNull()
        if (retryCommand == null) {
            message.value = "Repeat the action that failed"
            return
        }
        command(retryCommand, feedback = null)
    }

    fun clearRoomError(expected: String) {
        container.roomStore.updateStructure { state ->
            if (state.errorMessage == expected) state.copy(errorMessage = null, issue = null)
            else state
        }
    }

    private suspend fun <T> userResult(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
}

internal fun AppCommand.feedbackMessage(): String? =
    when (this) {
        is AppCommand.Play,
        is AppCommand.Pause,
        is AppCommand.Seek,
        is AppCommand.SkipNext,
        is AppCommand.SkipPrevious,
        is AppCommand.PlayQueueItem -> null

        AppCommand.ShuffleQueue,
        is AppCommand.SetRepeat,
        is AppCommand.RemoveQueueItem,
        is AppCommand.MoveQueueItem,
        is AppCommand.MoveQueueItemNext,
        AppCommand.ClearPlayed,
        is AppCommand.UpdateRoomOptions,
        AppCommand.LeaveRoom -> null

        is AppCommand.AddTracks ->
            when {
                trackIds.isEmpty() -> null
                insertAfterCurrent -> "Playing next"
                trackIds.size == 1 -> "Added to the queue"
                else -> "Adding ${trackIds.size} songs"
            }

        else -> null
    }
