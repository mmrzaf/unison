package com.darius.unison.ui

import com.darius.unison.model.MemberTrackState
import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomLifecycleState
import com.darius.unison.model.RoomMediaReadiness
import com.darius.unison.model.TrackId
import com.darius.unison.model.TransferProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomQueueUiPolicyTest {
    @Test
    fun unavailableMediaExplainsPrepareInsteadOfPlay() {
        val presentation =
            RoomQueueUiPolicy.mediaPresentation(
                readiness = RoomMediaReadiness.NEEDS_PREPARATION,
                transfer = null,
                current = false,
                playing = false,
            )

        assertEquals(RoomQueueUiPolicy.TapAction.PREPARE, presentation.tapAction)
        assertEquals("Needs preparation", presentation.detail)
    }

    @Test
    fun readyMediaExplainsPlay() {
        val presentation =
            RoomQueueUiPolicy.mediaPresentation(
                readiness = RoomMediaReadiness.READY,
                transfer = null,
                current = false,
                playing = false,
            )

        assertEquals(RoomQueueUiPolicy.TapAction.PLAY, presentation.tapAction)
        assertEquals("Ready", presentation.detail)
    }

    @Test
    fun preparingMediaReportsProgressAndDoesNotInviteAnotherTap() {
        val transfer =
            TransferProgress(
                trackId = TrackId("a".repeat(64)),
                bytesTransferred = 420,
                totalBytes = 1_000,
                sourcePeerId = PeerId("source-123456789"),
                destinationPeerId = PeerId("dest-12345678901"),
                state = MemberTrackState.RECEIVING,
            )

        val presentation =
            RoomQueueUiPolicy.mediaPresentation(
                readiness = RoomMediaReadiness.PREPARING,
                transfer = transfer,
                current = false,
                playing = false,
            )

        assertEquals(RoomQueueUiPolicy.TapAction.NONE, presentation.tapAction)
        assertEquals("Preparing · 42%", presentation.detail)
    }

    @Test
    fun failedPreparationMakesRetryIntentExplicit() {
        val transfer =
            TransferProgress(
                trackId = TrackId("b".repeat(64)),
                bytesTransferred = 50,
                totalBytes = 1_000,
                sourcePeerId = null,
                destinationPeerId = null,
                state = MemberTrackState.FAILED,
            )

        val presentation =
            RoomQueueUiPolicy.mediaPresentation(
                readiness = RoomMediaReadiness.NEEDS_PREPARATION,
                transfer = transfer,
                current = false,
                playing = false,
            )

        assertEquals(RoomQueueUiPolicy.TapAction.PREPARE, presentation.tapAction)
        assertEquals("Preparation failed", presentation.detail)
    }

    @Test
    fun queueSummaryShowsUsefulReadinessCounts() {
        val summary =
            RoomQueueUiPolicy.queueSummary(
                queueSize = 5,
                readiness =
                    listOf(
                        RoomMediaReadiness.READY,
                        RoomMediaReadiness.READY,
                        RoomMediaReadiness.PREPARING,
                        RoomMediaReadiness.NEEDS_PREPARATION,
                        RoomMediaReadiness.NEEDS_PREPARATION,
                    ),
            )

        assertEquals("5 songs · 2 ready · 1 preparing", summary)
    }

    @Test
    fun missingReadinessEntriesAreCountedAsNeedingPreparation() {
        val summary =
            RoomQueueUiPolicy.queueSummary(
                queueSize = 3,
                readiness = listOf(RoomMediaReadiness.READY),
            )

        assertEquals("3 songs · 1 ready", summary)
    }

    @Test
    fun reconnectingPresenceIsTruthful() {
        assertEquals(
            "Reconnecting…",
            RoomQueueUiPolicy.roomPresenceLabel(RoomLifecycleState.RECONNECTING, 2),
        )
        assertTrue(
            RoomQueueUiPolicy.roomPresenceLabel(RoomLifecycleState.CONNECTED, 2).contains("2")
        )
    }
}
