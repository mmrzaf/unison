package com.darius.unison.protocol

import com.darius.unison.model.LocalPlaybackParticipation
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
                participation = LocalPlaybackParticipation.ACTIVE,
                driftMs = null,
                playbackRevision = 0,
                queueRevision = 0,
                canonicalSequence = 0,
            )
        assertNull(report.driftMs)
    }

    @Test
    fun convergenceRevisionsArePreserved() {
        val report =
            ProtocolBody.PlaybackStatusReport(
                queueItemId = null,
                positionMs = 250,
                isPlaying = false,
                participation = LocalPlaybackParticipation.OUTPUT_INHIBITED,
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
                participation = LocalPlaybackParticipation.ACTIVE,
                driftMs = -17,
                playbackRevision = 0,
                queueRevision = 0,
                canonicalSequence = 0,
            )
        assertEquals(-17L, report.driftMs)
    }
}
