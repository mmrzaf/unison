package com.darius.unison.ui

import com.darius.unison.model.AppCommand
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomIssue
import com.darius.unison.model.RoomIssueCode
import com.darius.unison.model.RoomRecoveryAction
import com.darius.unison.model.TransportAction
import com.darius.unison.model.TransportCommandPhase
import com.darius.unison.model.TransportCommandStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomPlaybackUiPolicyTest {
    @Test
    fun navigationIsBlockedOnlyWhileNavigationCommandIsActive() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                localIsPlaying = true,
                status = status(TransportAction.NEXT, TransportCommandPhase.SCHEDULED),
            )

        assertTrue(controls.navigationPending)
        assertFalse(controls.canNavigate)
        assertFalse(controls.canSeek)
        assertTrue(controls.canPlayPause)
    }

    @Test
    fun playPauseRemainsReversibleWhilePending() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                localIsPlaying = false,
                status = status(TransportAction.PLAY, TransportCommandPhase.ACCEPTED),
            )

        assertTrue(controls.playPausePending)
        assertTrue(controls.displayedPlaying)
        assertTrue(controls.canPlayPause)
        assertTrue(controls.canNavigate)
    }

    @Test
    fun terminalNavigationStatusDoesNotBlockControls() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                localIsPlaying = false,
                status = status(TransportAction.NEXT, TransportCommandPhase.SETTLED),
            )

        assertFalse(controls.navigationPending)
        assertTrue(controls.canNavigate)
        assertTrue(controls.canSeek)
    }

    @Test
    fun retryCommandsPreserveTargetInformation() {
        val seek =
            status(
                    action = TransportAction.SEEK,
                    phase = TransportCommandPhase.REJECTED,
                    requestedPositionMs = 42_000,
                )
                .retryCommandOrNull()
        val playItem =
            status(
                    action = TransportAction.PLAY_ITEM,
                    phase = TransportCommandPhase.REJECTED,
                    queueItemId = QueueItemId("queue-item"),
                )
                .retryCommandOrNull()

        assertEquals(42_000, (seek as AppCommand.Seek).positionMs)
        assertEquals(QueueItemId("queue-item"), (playItem as AppCommand.PlayQueueItem).queueItemId)
    }

    @Test
    fun incompleteRetryCommandIsNotOffered() {
        val issue =
            RoomIssue(
                code = RoomIssueCode.PLAYBACK_ACTION_FAILED,
                message = "failed",
                recoveryAction = RoomRecoveryAction.RETRY,
            )
        val incompleteSeek = status(TransportAction.SEEK, TransportCommandPhase.REJECTED)

        assertNull(incompleteSeek.retryCommandOrNull())
        assertNull(RoomPlaybackUiPolicy.issueAction(issue, incompleteSeek))
    }

    @Test
    fun issueActionsAreConcreteAndSafe() {
        val retryIssue = issue(RoomRecoveryAction.RETRY)
        val filesIssue = issue(RoomRecoveryAction.READD_TRACK)
        val leaveIssue = issue(RoomRecoveryAction.LEAVE_ROOM)
        val reconnectIssue = issue(RoomRecoveryAction.RECONNECT)

        assertEquals(
            RoomPlaybackUiPolicy.IssueAction.RETRY_TRANSPORT,
            RoomPlaybackUiPolicy.issueAction(
                retryIssue,
                status(TransportAction.PLAY, TransportCommandPhase.REJECTED),
            ),
        )
        assertEquals(
            RoomPlaybackUiPolicy.IssueAction.CHOOSE_FILES,
            RoomPlaybackUiPolicy.issueAction(filesIssue, null),
        )
        assertEquals(
            RoomPlaybackUiPolicy.IssueAction.LEAVE_ROOM,
            RoomPlaybackUiPolicy.issueAction(leaveIssue, null),
        )
        assertNull(RoomPlaybackUiPolicy.issueAction(reconnectIssue, null))
        assertNotNull(
            status(TransportAction.PLAY, TransportCommandPhase.REJECTED).retryCommandOrNull()
        )
    }

    private fun status(
        action: TransportAction,
        phase: TransportCommandPhase,
        queueItemId: QueueItemId? = null,
        requestedPositionMs: Long? = null,
    ) =
        TransportCommandStatus(
            commandId = "command",
            action = action,
            phase = phase,
            queueItemId = queueItemId,
            requestedPositionMs = requestedPositionMs,
        )

    private fun issue(action: RoomRecoveryAction) =
        RoomIssue(
            code = RoomIssueCode.PLAYBACK_ACTION_FAILED,
            message = "failed",
            recoveryAction = action,
        )
}
