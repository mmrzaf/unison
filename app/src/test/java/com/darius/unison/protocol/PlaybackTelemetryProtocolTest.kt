package com.darius.unison.protocol

import com.darius.unison.model.PeerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackTelemetryProtocolTest {
    @Test
    fun unknownParticipantDriftRemainsUnknown() {
        val report =
            ProtocolBody.PlaybackStatusReport(
                queueItemId = null,
                positionMs = 250,
                isPlaying = true,
                driftMs = null,
                playbackRevision = 0,
                queueRevision = 0,
                canonicalSequence = 0,
            )
        assertNull(report.driftMs)
    }

    @Test
    fun unknownMemberDriftRemainsUnknown() {
        val status =
            ProtocolBody.MemberPlaybackStatus(
                peerId = PeerId("peer-123456789012"),
                queueItemId = null,
                positionMs = 250,
                isPlaying = true,
                driftMs = null,
                playbackRevision = 0,
            )
        assertNull(status.driftMs)
    }

    @Test
    fun convergenceRevisionsArePreserved() {
        val report =
            ProtocolBody.PlaybackStatusReport(
                queueItemId = null,
                positionMs = 250,
                isPlaying = false,
                driftMs = null,
                playbackRevision = 11,
                queueRevision = 9,
                canonicalSequence = 13,
            )
        assertEquals(11L, report.playbackRevision)
        assertEquals(9L, report.queueRevision)
        assertEquals(13L, report.canonicalSequence)
    }

    @Test
    fun measuredDriftIsPreserved() {
        val report =
            ProtocolBody.PlaybackStatusReport(
                queueItemId = null,
                positionMs = 250,
                isPlaying = true,
                driftMs = -17,
                playbackRevision = 0,
                queueRevision = 0,
                canonicalSequence = 0,
            )
        assertEquals(-17L, report.driftMs)
    }
}
