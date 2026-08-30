package com.darius.unison.ui

import com.darius.unison.model.AppCommand
import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
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
    fun navigationRemainsReversibleWhileNavigationCommandIsActive() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                canonicalIsPlaying = true,
                status = status(TransportAction.NEXT, TransportCommandPhase.SCHEDULED),
            )

        assertTrue(controls.navigationPending)
        assertTrue(controls.canNavigate)
        assertTrue(controls.canSelectItem)
        assertFalse(controls.canSeek)
        assertTrue(controls.primaryActionEnabled)
    }

    @Test
    fun playPauseRemainsReversibleWhilePending() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                canonicalIsPlaying = false,
                status = status(TransportAction.PLAY, TransportCommandPhase.ACCEPTED),
            )

        assertTrue(controls.playPausePending)
        assertTrue(controls.displayedPlaying)
        assertTrue(controls.primaryActionEnabled)
        assertTrue(controls.canNavigate)
    }

    @Test
    fun terminalNavigationStatusDoesNotBlockControls() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                canonicalIsPlaying = false,
                status = status(TransportAction.NEXT, TransportCommandPhase.SETTLED),
            )

        assertFalse(controls.navigationPending)
        assertTrue(controls.canNavigate)
        assertTrue(controls.canSeek)
    }

    @Test
    fun queueItemCanBeSelectedBeforePlaybackStarts() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = false,
                hasSeekableDuration = false,
                canonicalIsPlaying = false,
                status = null,
            )

        assertFalse(controls.canNavigate)
        assertFalse(controls.primaryActionEnabled)
        assertTrue(controls.canSelectItem)
    }



    @Test
    fun unreadyCurrentItemOffersPrepareInsteadOfImpossiblePlay() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                canonicalIsPlaying = false,
                currentReadiness = RoomMediaReadiness.NEEDS_PREPARATION,
            )

        assertEquals(RoomPlaybackUiPolicy.PrimaryControl.PREPARE, controls.primaryControl)
        assertTrue(controls.primaryActionEnabled)
        assertFalse(controls.canSeek)
    }

    @Test
    fun preparingCurrentItemShowsBusyControlWithoutAnotherAction() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                canonicalIsPlaying = false,
                currentReadiness = RoomMediaReadiness.PREPARING,
            )

        assertEquals(RoomPlaybackUiPolicy.PrimaryControl.PREPARING, controls.primaryControl)
        assertFalse(controls.primaryActionEnabled)
    }

    @Test
    fun audioFocusInterruptionShowsRecoveringWhileNoisyRouteRequiresExplicitRejoin() {
        val recovering =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                canonicalIsPlaying = true,
                localParticipation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                localInhibitionReason = LocalPlaybackInhibitionReason.AUDIO_FOCUS,
            )
        val rejoin =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                canonicalIsPlaying = true,
                localParticipation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                localInhibitionReason = LocalPlaybackInhibitionReason.BECOMING_NOISY,
            )

        assertEquals(RoomPlaybackUiPolicy.PrimaryControl.RECOVERING, recovering.primaryControl)
        assertFalse(recovering.primaryActionEnabled)
        assertEquals(RoomPlaybackUiPolicy.PrimaryControl.REJOIN, rejoin.primaryControl)
        assertTrue(rejoin.primaryActionEnabled)
    }

    @Test
    fun pendingSuccessorOwnsCenterControlAndDisablesSeek() {
        val controls =
            RoomPlaybackUiPolicy.controls(
                hasCurrentItem = true,
                hasSeekableDuration = true,
                canonicalIsPlaying = false,
                pendingSuccessorQueueItemId = QueueItemId("next"),
            )

        assertEquals(RoomPlaybackUiPolicy.PrimaryControl.WAITING_FOR_NEXT, controls.primaryControl)
        assertFalse(controls.primaryActionEnabled)
        assertFalse(controls.canSeek)
        assertTrue(controls.canNavigate)
    }

    @Test
    fun activeTargetTransferIsPresentedAsWaitingForContent() {
        val peer = PeerId("peer-123456789012")
        val track =
            TrackDescriptor(
                trackId = TrackId("a".repeat(64)),
                sizeBytes = 1_000,
                durationMs = 60_000,
                title = "Target song",
            )
        val item = QueueItem(QueueItemId("queue-item-123456"), track, peer, 1)
        val snapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1, peer),
                sequence = 1,
                members = listOf(MemberSnapshot(peer, "Phone")),
                queue = listOf(item),
                playback = CanonicalPlaybackState(queueItemId = item.queueItemId, revision = 1),
                queueRevision = 1,
            )
        val status =
            TransportCommandStatus(
                commandId = "command",
                action = TransportAction.PLAY_ITEM,
                phase = TransportCommandPhase.ACCEPTED,
                queueItemId = item.queueItemId,
            )
        val transfer =
            TransferProgress(
                trackId = track.trackId,
                bytesTransferred = 500,
                totalBytes = 1_000,
                sourcePeerId = peer,
                destinationPeerId = peer,
                state = MemberTrackState.RECEIVING,
            )

        val presentation =
            RoomPlaybackUiPolicy.transition(
                snapshot = snapshot,
                lifecycle = RoomLifecycleState.CONNECTED,
                status = status,
                transfers = mapOf(track.trackId to transfer),
            )

        assertEquals(RoomPlaybackUiPolicy.TransitionKind.WAITING_FOR_CONTENT, presentation?.kind)
        assertEquals(0.5f, presentation?.progressFraction)
        assertTrue(presentation?.message?.contains("Target song") == true)
    }


    @Test
    fun pendingSuccessorIsPresentedEvenWithoutUserTransportCommand() {
        val peer = PeerId("peer-123456789012")
        val currentTrack = TrackDescriptor(TrackId("a".repeat(64)), 1_000, durationMs = 60_000, title = "A")
        val nextTrack = TrackDescriptor(TrackId("b".repeat(64)), 2_000, durationMs = 60_000, title = "B")
        val current = QueueItem(QueueItemId("current"), currentTrack, peer, 1)
        val next = QueueItem(QueueItemId("next"), nextTrack, peer, 2)
        val snapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1, peer),
                sequence = 2,
                members = listOf(MemberSnapshot(peer, "Phone")),
                queue = listOf(current, next),
                playback = CanonicalPlaybackState(queueItemId = current.queueItemId, revision = 2),
                queueRevision = 2,
            )
        val transfer =
            TransferProgress(
                trackId = nextTrack.trackId,
                bytesTransferred = 1_000,
                totalBytes = 2_000,
                sourcePeerId = peer,
                destinationPeerId = peer,
                state = MemberTrackState.RECEIVING,
            )

        val presentation =
            RoomPlaybackUiPolicy.transition(
                snapshot = snapshot,
                lifecycle = RoomLifecycleState.CONNECTED,
                status = null,
                transfers = mapOf(nextTrack.trackId to transfer),
                pendingSuccessorQueueItemId = next.queueItemId,
            )

        assertEquals(RoomPlaybackUiPolicy.TransitionKind.WAITING_FOR_CONTENT, presentation?.kind)
        assertEquals("Preparing next song…", presentation?.message)
        assertEquals(0.5f, presentation?.progressFraction)
    }

    @Test
    fun reconnectingHasExplicitRecoveryPresentation() {
        val peer = PeerId("peer-123456789012")
        val snapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1, peer),
                sequence = 0,
                members = listOf(MemberSnapshot(peer, "Phone")),
            )

        val presentation =
            RoomPlaybackUiPolicy.transition(
                snapshot = snapshot,
                lifecycle = RoomLifecycleState.RECONNECTING,
                status = null,
                transfers = emptyMap(),
            )

        assertEquals(RoomPlaybackUiPolicy.TransitionKind.RECOVERING, presentation?.kind)
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
        assertNotNull(
            status(TransportAction.PLAY, TransportCommandPhase.REJECTED).retryCommandOrNull()
        )
    }


    @Test
    fun roomEndedUsesTerminalHumanFacingIssueCopy() {
        val issue =
            RoomIssue(
                code = RoomIssueCode.ROOM_ENDED,
                message = "The room host ended the room",
                recoveryAction = RoomRecoveryAction.NONE,
            )

        val presentation = RoomPlaybackUiPolicy.issuePresentation(issue)

        assertEquals("Room ended", presentation.title)
        assertEquals("The room host ended the room", presentation.message)
        assertNull(RoomPlaybackUiPolicy.issueAction(issue, null))
    }

    @Test
    fun unavailableTrackUsesHumanFacingIssueCopy() {
        val issue =
            RoomIssue(
                code = RoomIssueCode.PLAYBACK_TRACK_UNAVAILABLE,
                message = "CONNECT_TIMEOUT source peer-123",
                recoveryAction = RoomRecoveryAction.NONE,
            )

        val presentation = RoomPlaybackUiPolicy.issuePresentation(issue)

        assertEquals("Song unavailable", presentation.title)
        assertEquals("Unison couldn't get the song needed for playback.", presentation.message)
        assertFalse(presentation.message.contains("TIMEOUT"))
    }

    @Test
    fun rejectedNavigationDoesNotExposeTransportFailureText() {
        val peer = PeerId("peer-123456789012")
        val snapshot =
            RoomSnapshot(
                roomId = "room",
                roomName = "Room",
                term = CoordinatorTerm(1, peer),
                sequence = 0,
                members = listOf(MemberSnapshot(peer, "Phone")),
            )
        val rejected =
            TransportCommandStatus(
                commandId = "command",
                action = TransportAction.NEXT,
                phase = TransportCommandPhase.REJECTED,
                message = "watchdog did not settle",
            )

        val presentation =
            RoomPlaybackUiPolicy.transition(
                snapshot = snapshot,
                lifecycle = RoomLifecycleState.CONNECTED,
                status = rejected,
                transfers = emptyMap(),
            )

        assertEquals(RoomPlaybackUiPolicy.TransitionKind.FAILED, presentation?.kind)
        assertEquals("Couldn't change songs", presentation?.message)
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
