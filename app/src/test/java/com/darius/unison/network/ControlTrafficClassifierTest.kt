package com.darius.unison.network

import com.darius.unison.model.PeerId
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.ProtocolBody
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class ControlTrafficClassifierTest {
    private val peer = PeerId("11111111-1111-1111-1111-111111111111")

    private fun envelope(body: ProtocolBody, sequence: Long? = null) =
        Envelope(
            protocolVersion = com.darius.unison.protocol.PROTOCOL_VERSION,
            roomId = "room",
            term = 1,
            coordinatorPeerId = peer,
            senderPeerId = peer,
            sequence = sequence,
            messageId = UUID.randomUUID().toString(),
            sentAtElapsedNs = 1,
            body = body,
        )

    @Test
    fun canonicalSequenceAlwaysUsesGuaranteedQueue() {
        assertEquals(
            ControlTrafficClass.GUARANTEED,
            ControlTrafficClassifier.classify(envelope(ProtocolBody.Heartbeat(0), sequence = 9)),
        )
    }

    @Test
    fun timingAndPlaybackReferencesUseIndependentQueues() {
        assertEquals(
            ControlTrafficClass.CLOCK,
            ControlTrafficClassifier.classify(envelope(ProtocolBody.ClockPing("p", 1))),
        )
        assertEquals(
            ControlTrafficClass.PLAYBACK_REFERENCE,
            ControlTrafficClassifier.classify(
                envelope(
                    ProtocolBody.PlaybackStateSync(
                        playback = com.darius.unison.model.CanonicalPlaybackState(),
                        canonicalSequence = 0,
                        queueRevision = 0,
                        recovery = false,
                    )
                )
            ),
        )
        assertEquals(
            ControlTrafficClass.GUARANTEED,
            ControlTrafficClassifier.classify(
                envelope(
                    ProtocolBody.PlaybackStateSync(
                        playback = com.darius.unison.model.CanonicalPlaybackState(),
                        canonicalSequence = 0,
                        queueRevision = 0,
                        recovery = true,
                    )
                )
            ),
        )
    }

    @Test
    fun telemetryAndTransferDoNotUseGuaranteedQueue() {
        assertEquals(
            ControlTrafficClass.TELEMETRY,
            ControlTrafficClassifier.classify(
                envelope(ProtocolBody.PlaybackStatusReport(null, 0, false, 0, 0, 0, 0))
            ),
        )
        assertEquals(
            ControlTrafficClass.TRANSFER,
            ControlTrafficClassifier.classify(
                envelope(ProtocolBody.TrackNeed(com.darius.unison.model.TrackId("a".repeat(64))))
            ),
        )
    }
}
