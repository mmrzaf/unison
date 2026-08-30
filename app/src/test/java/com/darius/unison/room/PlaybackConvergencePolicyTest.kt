package com.darius.unison.room

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.CoordinatorTerm
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.ProtocolBody
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackConvergencePolicyTest {
    private val coordinator = PeerId("coordinator-123456")
    private val guest = PeerId("guest-peer-123456")
    private val item =
        QueueItem.create(
            TrackDescriptor(TrackId("a".repeat(64)), 1024, durationMs = 60_000),
            coordinator,
            1,
        )

    private fun snapshot(executeAtNs: Long = 1_000_000_000L) =
        RoomSnapshot(
            roomId = "room",
            roomName = "Room",
            term = CoordinatorTerm(1, coordinator),
            sequence = 12,
            members =
                listOf(
                    MemberSnapshot(coordinator, "Coordinator"),
                    MemberSnapshot(guest, "Guest"),
                ),
            queue = listOf(item),
            playback =
                CanonicalPlaybackState(
                    queueItemId = item.queueItemId,
                    positionAtTimestampMs = 0,
                    coordinatorTimestampNs = executeAtNs,
                    isPlaying = true,
                    revision = 12,
                ),
            queueRevision = 7,
        )

    private fun report(
        itemId: com.darius.unison.model.QueueItemId? = item.queueItemId,
        playing: Boolean = true,
        playbackRevision: Long = 12,
        queueRevision: Long = 7,
        participation: LocalPlaybackParticipation = LocalPlaybackParticipation.ACTIVE,
    ) =
        ProtocolBody.PlaybackStatusReport(
            queueItemId = itemId,
            positionMs = 1_000,
            isPlaying = playing,
            participation = participation,
            driftMs = null,
            playbackRevision = playbackRevision,
            queueRevision = queueRevision,
            canonicalSequence = 12,
        )

    @Test
    fun wrongSongTriggersPlaybackRepairAfterExecutionGrace() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val action =
            policy.decide(
                guest,
                snapshot(),
                report(itemId = null),
                coordinatorNowNs = 2_000_000_000L,
            )
        assertEquals(
            PlaybackConvergencePolicy.Action.SendPlaybackState("WRONG_QUEUE_ITEM"),
            action,
        )
    }

    @Test
    fun wrongPlayStateTriggersPlaybackRepair() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val action =
            policy.decide(
                guest,
                snapshot(),
                report(playing = false),
                coordinatorNowNs = 2_000_000_000L,
            )
        assertEquals(
            PlaybackConvergencePolicy.Action.SendPlaybackState("WRONG_PLAY_STATE"),
            action,
        )
    }


    @Test
    fun inhibitedPeerIsNeverRepairedForIntentionalLocalSilenceOrStaleItem() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val action =
            policy.decide(
                guest,
                snapshot(),
                report(
                    itemId = null,
                    playing = false,
                    participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
                ),
                coordinatorNowNs = 2_000_000_000L,
            )

        assertEquals(PlaybackConvergencePolicy.Action.None, action)
    }

    @Test
    fun staleQueueRequiresSnapshotInsteadOfTransportGuessing() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val action =
            policy.decide(
                guest,
                snapshot(),
                report(queueRevision = 6),
                coordinatorNowNs = 2_000_000_000L,
            )
        assertEquals(
            PlaybackConvergencePolicy.Action.SendSnapshot("QUEUE_REVISION_BEHIND"),
            action,
        )
    }

    @Test
    fun futureCommandDoesNotRepairTheExpectedPreExecutionState() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val action =
            policy.decide(
                guest,
                snapshot(executeAtNs = 10_000_000_000L),
                report(itemId = null, playing = false),
                coordinatorNowNs = 9_000_000_000L,
            )
        assertEquals(PlaybackConvergencePolicy.Action.None, action)
    }

    @Test
    fun duplicateRepairIsRateLimited() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 1_000_000_000L)
        val room = snapshot()
        val bad = report(playing = false)
        val first = policy.decide(guest, room, bad, 2_000_000_000L)
        val duplicate = policy.decide(guest, room, bad, 2_100_000_000L)

        assertEquals(
            PlaybackConvergencePolicy.Action.SendPlaybackState("WRONG_PLAY_STATE"),
            first,
        )
        assertEquals(PlaybackConvergencePolicy.Action.None, duplicate)
    }
    @Test
    fun unavailableMediaSuppressesWrongItemRepairStorm() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val action =
            policy.decide(
                guest,
                snapshot(),
                report(itemId = null, playing = false),
                coordinatorNowNs = 2_000_000_000L,
                playbackExecutable = false,
            )

        assertEquals(PlaybackConvergencePolicy.Action.None, action)
    }

    @Test
    fun unavailableMediaStillAllowsQueueRevisionRepair() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val action =
            policy.decide(
                guest,
                snapshot(),
                report(queueRevision = 6, itemId = null, playing = false),
                coordinatorNowNs = 2_000_000_000L,
                playbackExecutable = false,
            )

        assertEquals(
            PlaybackConvergencePolicy.Action.SendSnapshot("QUEUE_REVISION_BEHIND"),
            action,
        )
    }

    @Test
    fun unavailableMediaSuppressesPlaybackRevisionRepairToo() {
        val policy = PlaybackConvergencePolicy(minimumRepairIntervalNs = 0)
        val action =
            policy.decide(
                guest,
                snapshot(),
                report(playbackRevision = 11, itemId = null, playing = false),
                coordinatorNowNs = 2_000_000_000L,
                playbackExecutable = false,
            )

        assertEquals(PlaybackConvergencePolicy.Action.None, action)
    }

}
