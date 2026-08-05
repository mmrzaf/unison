package com.darius.unison.ui

import com.darius.unison.model.AppCommand
import com.darius.unison.model.RoomIssue
import com.darius.unison.model.RoomRecoveryAction
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandStatus

/** Pure presentation policy for room playback controls and issue actions. */
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
            canNavigate = hasCurrentItem && !navigationPending,
            canSelectItem = !navigationPending,
            playPausePending = playPausePending,
            seekPending = seekPending,
            navigationPending = navigationPending,
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
