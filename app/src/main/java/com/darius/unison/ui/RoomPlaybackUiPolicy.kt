package com.darius.unison.ui

import com.darius.unison.model.AppCommand
import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomIssue
import com.darius.unison.model.RoomIssueCode
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.RoomRecoveryAction
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.TransportCommandStatus

/** Pure presentation policy for room playback controls and user-facing room status. */
internal object RoomPlaybackUiPolicy {
    enum class PrimaryControl {
        NONE,
        PLAY,
        PAUSE,
        PREPARE,
        PREPARING,
        WAITING_FOR_NEXT,
        REJOIN,
        RECOVERING,
    }

    data class Controls(
        val displayedPlaying: Boolean,
        val primaryControl: PrimaryControl,
        val primaryActionEnabled: Boolean,
        val canSeek: Boolean,
        val canNavigate: Boolean,
        val canSelectItem: Boolean,
        val playPausePending: Boolean,
        val seekPending: Boolean,
        val navigationPending: Boolean,
    )

    enum class TransitionKind {
        TRANSITIONING,
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
        RETRY_PREPARATION,
        CHOOSE_FILES,
        LEAVE_ROOM,
    }

    fun controls(
        hasCurrentItem: Boolean,
        hasSeekableDuration: Boolean,
        canonicalIsPlaying: Boolean,
        localParticipation: LocalPlaybackParticipation = LocalPlaybackParticipation.ACTIVE,
        localInhibitionReason: LocalPlaybackInhibitionReason? = null,
        currentQueueItemId: QueueItemId? = null,
        currentReadiness: RoomMediaReadiness = RoomMediaReadiness.READY,
        currentTransfer: TransferProgress? = null,
        pendingSuccessorQueueItemId: QueueItemId? = null,
        pendingSuccessorTransfer: TransferProgress? = null,
        issue: RoomIssue? = null,
        status: TransportCommandStatus? = null,
    ): Controls {
        val active = status?.takeIf { it.active }
        val playPausePending =
            active?.action == TransportAction.PLAY || active?.action == TransportAction.PAUSE
        val seekPending = active?.action == TransportAction.SEEK
        val navigationPending =
            active?.action == TransportAction.NEXT ||
                active?.action == TransportAction.PREVIOUS ||
                active?.action == TransportAction.PLAY_ITEM
        val outputInhibited = localParticipation == LocalPlaybackParticipation.OUTPUT_INHIBITED
        val displayedPlaying =
            if (outputInhibited) {
                false
            } else {
                when (active?.action) {
                    TransportAction.PLAY -> true
                    TransportAction.PAUSE -> false
                    else -> canonicalIsPlaying
                }
            }

        val transferPreparing =
            currentTransfer?.state == MemberTrackState.RECEIVING ||
                currentTransfer?.state == MemberTrackState.VERIFYING ||
                currentTransfer?.state == MemberTrackState.PREPARING_PLAYER
        val transferFailed = currentTransfer?.state == MemberTrackState.FAILED
        val currentPreparationBlocked = issue.blocksPreparation(currentQueueItemId)
        val pendingPreparationBlocked =
            issue.blocksPreparation(pendingSuccessorQueueItemId) ||
                pendingSuccessorTransfer?.state == MemberTrackState.FAILED
        val waitingForSuccessor = pendingSuccessorQueueItemId != null && !pendingPreparationBlocked
        val manualRejoinPending = outputInhibited && active?.action == TransportAction.PLAY
        val primaryControl =
            when {
                !hasCurrentItem -> PrimaryControl.NONE
                manualRejoinPending -> PrimaryControl.RECOVERING
                outputInhibited &&
                    localInhibitionReason == LocalPlaybackInhibitionReason.AUDIO_FOCUS ->
                    PrimaryControl.RECOVERING
                outputInhibited -> PrimaryControl.REJOIN
                currentPreparationBlocked || transferFailed -> PrimaryControl.PREPARE
                waitingForSuccessor -> PrimaryControl.WAITING_FOR_NEXT
                transferPreparing || currentReadiness == RoomMediaReadiness.PREPARING ->
                    PrimaryControl.PREPARING
                currentReadiness == RoomMediaReadiness.NEEDS_PREPARATION -> PrimaryControl.PREPARE
                displayedPlaying -> PrimaryControl.PAUSE
                else -> PrimaryControl.PLAY
            }
        val primaryActionEnabled =
            primaryControl == PrimaryControl.PLAY ||
                primaryControl == PrimaryControl.PAUSE ||
                primaryControl == PrimaryControl.PREPARE ||
                primaryControl == PrimaryControl.REJOIN
        val currentReady = currentReadiness == RoomMediaReadiness.READY

        return Controls(
            displayedPlaying = displayedPlaying,
            primaryControl = primaryControl,
            primaryActionEnabled = primaryActionEnabled,
            canSeek =
                hasCurrentItem &&
                    currentReady &&
                    !waitingForSuccessor &&
                    hasSeekableDuration &&
                    !navigationPending,
            // Next is intentionally allowed even when the successor is unready: Phase 1 turns that
            // intent into prepare -> wait -> advance instead of rejecting an impossible command.
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
        pendingSuccessorQueueItemId: QueueItemId? = null,
        issue: RoomIssue? = null,
        localParticipation: LocalPlaybackParticipation = LocalPlaybackParticipation.ACTIVE,
        localInhibitionReason: LocalPlaybackInhibitionReason? = null,
    ): TransitionPresentation? {
        if (lifecycle == RoomLifecycleState.RECONNECTING) {
            return TransitionPresentation(
                kind = TransitionKind.RECOVERING,
                message = "Reconnecting to the room…",
            )
        }

        val command = status
        val pendingTarget = pendingSuccessorQueueItemId?.let { queueItemId ->
            snapshot.queue.firstOrNull { it.queueItemId == queueItemId }
        }
        val pendingTransfer = pendingTarget?.track?.trackId?.let(transfers::get)
        val pendingRejected =
            pendingSuccessorQueueItemId != null &&
                command?.phase == TransportCommandPhase.REJECTED &&
                command.queueItemId == pendingSuccessorQueueItemId
        val pendingBlocked =
            issue.blocksPreparation(pendingSuccessorQueueItemId) ||
                pendingTransfer?.state == MemberTrackState.FAILED ||
                pendingRejected
        if (pendingSuccessorQueueItemId != null && pendingBlocked) {
            return failedPreparationPresentation(
                queueItemId = pendingSuccessorQueueItemId,
                target = pendingTarget,
            )
        }

        pendingSuccessorQueueItemId?.let { queueItemId ->
            return TransitionPresentation(
                kind = TransitionKind.WAITING_FOR_CONTENT,
                queueItemId = queueItemId,
                trackId = pendingTarget?.track?.trackId,
                message =
                    pendingTarget?.track?.displayTitle?.let { "Preparing “$it”…" }
                        ?: "Preparing next song…",
                progressFraction =
                    pendingTransfer?.fraction?.takeIf {
                        pendingTransfer.state == MemberTrackState.RECEIVING ||
                            pendingTransfer.state == MemberTrackState.VERIFYING ||
                            pendingTransfer.state == MemberTrackState.PREPARING_PLAYER
                    },
            )
        }

        val relevantBlockedQueueItemId =
            issue
                ?.takeIf { it.code == RoomIssueCode.TRANSFER_BLOCKED }
                ?.queueItemId
                ?.takeIf { queueItemId ->
                    queueItemId == snapshot.playback.queueItemId ||
                        queueItemId == command?.queueItemId
                }
        relevantBlockedQueueItemId?.let { queueItemId ->
            return failedPreparationPresentation(
                queueItemId = queueItemId,
                target = snapshot.queue.firstOrNull { it.queueItemId == queueItemId },
            )
        }

        val outputInhibited = localParticipation == LocalPlaybackParticipation.OUTPUT_INHIBITED
        if (outputInhibited && snapshot.playback.isPlaying) {
            if (command?.active == true && command.action == TransportAction.PLAY) {
                return TransitionPresentation(
                    kind = TransitionKind.RECOVERING,
                    message = "Rejoining playback…",
                )
            }
            if (localInhibitionReason == LocalPlaybackInhibitionReason.AUDIO_FOCUS) {
                return TransitionPresentation(
                    kind = TransitionKind.RECOVERING,
                    message = "Recovering your audio…",
                )
            }
        }

        command ?: return null
        if (command.phase == TransportCommandPhase.REJECTED) {
            return TransitionPresentation(
                kind = TransitionKind.FAILED,
                queueItemId = command.queueItemId,
                message = transportFailureMessage(command.action),
            )
        }
        if (!command.active) return null

        val target =
            command.queueItemId?.let { id -> snapshot.queue.firstOrNull { it.queueItemId == id } }
        val transfer = target?.track?.trackId?.let(transfers::get)
        val title = target?.track?.displayTitle
        val quotedTitle = title?.let { "“$it”" }
        return when (transfer?.state) {
            MemberTrackState.FAILED ->
                failedPreparationPresentation(
                    queueItemId = target.queueItemId,
                    target = target,
                )

            MemberTrackState.RECEIVING ->
                TransitionPresentation(
                    kind = TransitionKind.WAITING_FOR_CONTENT,
                    queueItemId = target.queueItemId,
                    trackId = target.track.trackId,
                    message = quotedTitle?.let { "Preparing $it" } ?: "Preparing music",
                    progressFraction = transfer.fraction,
                )

            MemberTrackState.VERIFYING,
            MemberTrackState.PREPARING_PLAYER ->
                TransitionPresentation(
                    kind = TransitionKind.WAITING_FOR_CONTENT,
                    queueItemId = target.queueItemId,
                    trackId = target.track.trackId,
                    message = quotedTitle?.let { "Preparing $it" } ?: "Preparing music",
                    progressFraction = transfer.fraction.takeIf { it > 0f },
                )

            else ->
                TransitionPresentation(
                    kind = TransitionKind.TRANSITIONING,
                    queueItemId = target?.queueItemId ?: command.queueItemId,
                    trackId = target?.track?.trackId,
                    message =
                        when (command.action) {
                            TransportAction.PLAY_ITEM ->
                                quotedTitle?.let { "Switching to $it…" } ?: "Switching songs…"
                            TransportAction.NEXT -> "Skipping to next song…"
                            TransportAction.PREVIOUS -> "Going to previous song…"
                            TransportAction.SEEK -> "Seeking…"
                            TransportAction.PLAY -> "Starting playback…"
                            TransportAction.PAUSE -> "Pausing…"
                        },
                )
        }
    }

    private fun RoomIssue?.blocksPreparation(queueItemId: QueueItemId?): Boolean =
        this?.code == RoomIssueCode.TRANSFER_BLOCKED &&
            queueItemId != null &&
            this.queueItemId == queueItemId

    private fun failedPreparationPresentation(
        queueItemId: QueueItemId,
        target: com.darius.unison.model.QueueItem?,
    ): TransitionPresentation =
        TransitionPresentation(
            kind = TransitionKind.FAILED,
            queueItemId = queueItemId,
            trackId = target?.track?.trackId,
            message =
                target?.track?.displayTitle?.let { "Couldn't prepare “$it”" }
                    ?: "Couldn't prepare this song",
        )

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

            RoomIssueCode.LOCAL_NETWORK_UNAVAILABLE ->
                IssuePresentation(
                    title = "No local network",
                    message = issue.message,
                )

            RoomIssueCode.ROOM_NOT_ACTIVE,
            RoomIssueCode.CONNECTION_FAILED ->
                IssuePresentation(
                    title = "Room unavailable",
                    message = issue.message,
                )

            RoomIssueCode.ROOM_ENDED ->
                IssuePresentation(
                    title = "Room ended",
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

            RoomIssueCode.TRANSFER_BLOCKED ->
                IssuePresentation(
                    title = "Music transfer blocked",
                    message = issue.message,
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
            RoomRecoveryAction.RETRY_PREPARATION ->
                issue.queueItemId?.let { IssueAction.RETRY_PREPARATION }
            RoomRecoveryAction.READD_TRACK -> IssueAction.CHOOSE_FILES
            RoomRecoveryAction.LEAVE_ROOM -> IssueAction.LEAVE_ROOM
            RoomRecoveryAction.NONE,
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
