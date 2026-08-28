package com.darius.unison.ui

import com.darius.unison.model.AppCommand
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomIssue
import com.darius.unison.model.RoomIssueCode
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomRecoveryAction
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.TransportCommandStatus

/** Pure presentation policy for room playback controls and user-facing room status. */
internal object RoomPlaybackUiPolicy {
    data class Controls(
        val displayedPlaying: Boolean,
        val canPlayPause: Boolean,
        val canSeek: Boolean,
        val canNavigate: Boolean,
        val canSelectItem: Boolean,
        val playPausePending: Boolean,
        val seekPending: Boolean,
        val navigationPending: Boolean,
    )

    enum class TransitionKind {
        PREPARING,
        WAITING_FOR_CONTENT,
        RECOVERING,
        FAILED,
    }

    data class TransitionPresentation(
        val kind: TransitionKind,
        val queueItemId: QueueItemId? = null,
        val trackId: TrackId? = null,
        val message: String,
        val progressFraction: Float? = null,
    )

    data class IssuePresentation(
        val title: String,
        val message: String,
    )

    enum class IssueAction {
        RETRY_TRANSPORT,
        CHOOSE_FILES,
        LEAVE_ROOM,
    }

    fun controls(
        hasCurrentItem: Boolean,
        hasSeekableDuration: Boolean,
        localIsPlaying: Boolean,
        status: TransportCommandStatus?,
    ): Controls {
        val active = status?.takeIf { it.active }
        val playPausePending =
            active?.action == TransportAction.PLAY || active?.action == TransportAction.PAUSE
        val seekPending = active?.action == TransportAction.SEEK
        val navigationPending =
            active?.action == TransportAction.NEXT ||
                active?.action == TransportAction.PREVIOUS ||
                active?.action == TransportAction.PLAY_ITEM
        val displayedPlaying =
            when (active?.action) {
                TransportAction.PLAY -> true
                TransportAction.PAUSE -> false
                else -> localIsPlaying
            }
        return Controls(
            displayedPlaying = displayedPlaying,
            canPlayPause = hasCurrentItem,
            canSeek = hasCurrentItem && hasSeekableDuration && !navigationPending,
            // Navigation is intentionally reversible. The deterministic transport-intent processor
            // can supersede a pending target, so UI locking would only make preparation feel stuck.
            canNavigate = hasCurrentItem,
            canSelectItem = true,
            playPausePending = playPausePending,
            seekPending = seekPending,
            navigationPending = navigationPending,
        )
    }

    fun transition(
        snapshot: RoomSnapshot,
        lifecycle: RoomLifecycleState,
        status: TransportCommandStatus?,
        transfers: Map<TrackId, TransferProgress>,
    ): TransitionPresentation? {
        if (lifecycle == RoomLifecycleState.RECONNECTING) {
            return TransitionPresentation(
                kind = TransitionKind.RECOVERING,
                message = "Reconnecting to the room…",
            )
        }

        val command = status ?: return null
        if (command.phase == TransportCommandPhase.REJECTED) {
            return TransitionPresentation(
                kind = TransitionKind.FAILED,
                queueItemId = command.queueItemId,
                message = transportFailureMessage(command.action),
            )
        }
        if (!command.active) return null

        val target = command.queueItemId?.let { id -> snapshot.queue.firstOrNull { it.queueItemId == id } }
        val transfer = target?.track?.trackId?.let(transfers::get)
        val title = target?.track?.displayTitle
        val quotedTitle = title?.let { "“$it”" }
        return when (transfer?.state) {
            MemberTrackState.RECEIVING ->
                TransitionPresentation(
                    kind = TransitionKind.WAITING_FOR_CONTENT,
                    queueItemId = target.queueItemId,
                    trackId = target.track.trackId,
                    message = quotedTitle?.let { "Getting $it ready" } ?: "Getting music ready",
                    progressFraction = transfer.fraction,
                )

            MemberTrackState.VERIFYING,
            MemberTrackState.PREPARING_PLAYER ->
                TransitionPresentation(
                    kind = TransitionKind.WAITING_FOR_CONTENT,
                    queueItemId = target.queueItemId,
                    trackId = target.track.trackId,
                    message = quotedTitle?.let { "Getting $it ready" } ?: "Getting music ready",
                    progressFraction = transfer.fraction.takeIf { it > 0f },
                )

            else ->
                TransitionPresentation(
                    kind = TransitionKind.PREPARING,
                    queueItemId = target?.queueItemId ?: command.queueItemId,
                    trackId = target?.track?.trackId,
                    message =
                        quotedTitle?.let { "Getting $it ready…" }
                            ?: when (command.action) {
                                TransportAction.SEEK -> "Seeking…"
                                TransportAction.PLAY -> "Starting playback…"
                                TransportAction.PAUSE -> "Pausing…"
                                else -> "Getting playback ready…"
                            },
                )
        }
    }

    fun issuePresentation(issue: RoomIssue): IssuePresentation =
        when (issue.code) {
            RoomIssueCode.PLAYBACK_TRACK_UNAVAILABLE ->
                IssuePresentation(
                    title = "Song unavailable",
                    message = "Unison couldn't get the song needed for playback.",
                )

            RoomIssueCode.PLAYBACK_ACTION_FAILED,
            RoomIssueCode.COMMAND_REJECTED ->
                IssuePresentation(
                    title = "Playback needs attention",
                    message = "Unison couldn't complete that playback action.",
                )

            RoomIssueCode.PLAYBACK_CLOCK_UNAVAILABLE,
            RoomIssueCode.CONNECTION_INTERRUPTED ->
                IssuePresentation(
                    title = "Reconnecting playback",
                    message = "Unison is reconnecting to the room.",
                )

            RoomIssueCode.PLAYBACK_RECOVERED ->
                IssuePresentation(
                    title = "Playback recovered",
                    message = issue.message,
                )

            RoomIssueCode.PLAYBACK_UNSTABLE ->
                IssuePresentation(
                    title = "Playback is unstable",
                    message = "Unison is trying to restore smooth synchronized playback.",
                )

            RoomIssueCode.ROOM_NOT_ACTIVE,
            RoomIssueCode.CONNECTION_FAILED ->
                IssuePresentation(
                    title = "Room unavailable",
                    message = issue.message,
                )

            RoomIssueCode.ROOM_QUEUE_FULL ->
                IssuePresentation(
                    title = "Queue is full",
                    message = "Remove a song before adding more music.",
                )

            RoomIssueCode.TRACK_OPEN_FAILED ->
                IssuePresentation(
                    title = "Couldn't open this song",
                    message = "Choose the file again so Unison can use it.",
                )

            RoomIssueCode.TRACK_PREPARATION_TIMED_OUT ->
                IssuePresentation(
                    title = "Song isn't ready",
                    message = "Unison couldn't get this song ready for playback.",
                )

            RoomIssueCode.PARTIAL_TRACK_IMPORT ->
                IssuePresentation(
                    title = "Some songs need attention",
                    message = issue.message,
                )

            RoomIssueCode.INTERNAL_FAILURE ->
                IssuePresentation(
                    title = "Something went wrong",
                    message = "Unison couldn't complete the room action.",
                )
        }

    fun issueAction(issue: RoomIssue?, status: TransportCommandStatus?): IssueAction? =
        when (issue?.recoveryAction) {
            RoomRecoveryAction.RETRY ->
                status?.retryCommandOrNull()?.let { IssueAction.RETRY_TRANSPORT }
            RoomRecoveryAction.READD_TRACK -> IssueAction.CHOOSE_FILES
            RoomRecoveryAction.LEAVE_ROOM -> IssueAction.LEAVE_ROOM
            RoomRecoveryAction.NONE,
            RoomRecoveryAction.RECONNECT,
            null -> null
        }

    private fun transportFailureMessage(action: TransportAction): String =
        when (action) {
            TransportAction.PLAY -> "Couldn't start playback"
            TransportAction.PAUSE -> "Couldn't pause playback"
            TransportAction.SEEK -> "Couldn't seek playback"
            TransportAction.NEXT,
            TransportAction.PREVIOUS,
            TransportAction.PLAY_ITEM -> "Couldn't change songs"
        }
}

internal fun TransportCommandStatus.retryCommandOrNull(): AppCommand.Transport? =
    when (action) {
        TransportAction.PLAY -> AppCommand.Play()
        TransportAction.PAUSE -> AppCommand.Pause()
        TransportAction.SEEK -> requestedPositionMs?.let { AppCommand.Seek(it) }
        TransportAction.NEXT -> AppCommand.SkipNext()
        TransportAction.PREVIOUS -> AppCommand.SkipPrevious()
        TransportAction.PLAY_ITEM -> queueItemId?.let { AppCommand.PlayQueueItem(it) }
    }
