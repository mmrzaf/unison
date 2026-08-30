package com.darius.unison.network

import com.darius.unison.model.LocalPlaybackParticipation
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.FrameCodec
import com.darius.unison.protocol.PROTOCOL_VERSION
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.util.DiagnosticLog
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlConnectionPriorityTest {
    @Test
    fun readyQueuesDrainInPriorityOrder() = withConnection { connection ->
        assertTrue(connection.trySend(envelope(ControlTrafficClass.TELEMETRY, 1)))
        assertTrue(connection.trySend(envelope(ControlTrafficClass.TRANSFER, 2)))
        assertTrue(connection.trySend(envelope(ControlTrafficClass.PLAYBACK_REFERENCE, 3)))
        assertTrue(connection.trySend(envelope(ControlTrafficClass.CLOCK, 4)))
        assertTrue(connection.trySend(envelope(ControlTrafficClass.GUARANTEED, 5)))

        val observed =
            List(5) {
                ControlTrafficClassifier.classify(checkNotNull(connection.nextOutgoingForTest()))
            }

        assertEquals(
            listOf(
                ControlTrafficClass.GUARANTEED,
                ControlTrafficClass.CLOCK,
                ControlTrafficClass.PLAYBACK_REFERENCE,
                ControlTrafficClass.TRANSFER,
                ControlTrafficClass.TELEMETRY,
            ),
            observed,
        )
    }

    @Test
    fun sustainedLowerPriorityReadinessCannotStarveGuaranteedOrClockTraffic() =
        withConnection { connection ->
            repeat(2_000) { iteration ->
                assertTrue(connection.trySend(envelope(ControlTrafficClass.TRANSFER, iteration * 10 + 1)))
                assertTrue(connection.trySend(envelope(ControlTrafficClass.TELEMETRY, iteration * 10 + 2)))
                assertTrue(
                    connection.trySend(
                        envelope(ControlTrafficClass.PLAYBACK_REFERENCE, iteration * 10 + 3)
                    )
                )
                assertTrue(connection.trySend(envelope(ControlTrafficClass.CLOCK, iteration * 10 + 4)))
                assertTrue(
                    connection.trySend(envelope(ControlTrafficClass.GUARANTEED, iteration * 10 + 5))
                )

                assertEquals(
                    ControlTrafficClass.GUARANTEED,
                    ControlTrafficClassifier.classify(checkNotNull(connection.nextOutgoingForTest())),
                )
                assertEquals(
                    ControlTrafficClass.CLOCK,
                    ControlTrafficClassifier.classify(checkNotNull(connection.nextOutgoingForTest())),
                )
                assertEquals(
                    ControlTrafficClass.PLAYBACK_REFERENCE,
                    ControlTrafficClassifier.classify(checkNotNull(connection.nextOutgoingForTest())),
                )
                assertEquals(
                    ControlTrafficClass.TRANSFER,
                    ControlTrafficClassifier.classify(checkNotNull(connection.nextOutgoingForTest())),
                )
                assertEquals(
                    ControlTrafficClass.TELEMETRY,
                    ControlTrafficClassifier.classify(checkNotNull(connection.nextOutgoingForTest())),
                )
            }
        }

    private fun <T> withConnection(block: suspend (ControlConnection) -> T): T = runBlocking {
        val server = ServerSocket(0)
        val client = Socket("127.0.0.1", server.localPort)
        val accepted = server.accept()
        server.close()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val logFile = File.createTempFile("unison-control-priority", ".ndjson")
        val diagnosticLog = DiagnosticLog(logFile)
        val connection =
            ControlConnection(
                peerId = PeerId("peer-priority"),
                endpoint =
                    PeerEndpoint(
                        peerId = PeerId("peer-priority"),
                        displayName = "Peer",
                        hostAddress = "127.0.0.1",
                        port = accepted.localPort,
                        appVersion = "test",
                    ),
                socket = accepted,
                codec = FrameCodec(ByteArray(32) { 7 }),
                parentScope = scope,
                log = diagnosticLog,
                onEnvelope = { _, _ -> },
                onClosed = { _, _ -> },
            )
        try {
            block(connection)
        } finally {
            connection.closeSilently()
            client.close()
            scope.cancel()
            diagnosticLog.close()
            logFile.delete()
        }
    }

    private fun envelope(trafficClass: ControlTrafficClass, seed: Int): Envelope {
        val body =
            when (trafficClass) {
                ControlTrafficClass.GUARANTEED -> ProtocolBody.QueueCleared
                ControlTrafficClass.CLOCK -> ProtocolBody.Heartbeat(seed.toLong())
                ControlTrafficClass.PLAYBACK_REFERENCE ->
                    ProtocolBody.PlaybackStateSync(
                        playback = com.darius.unison.model.CanonicalPlaybackState(),
                        canonicalSequence = seed.toLong(),
                        queueRevision = 0L,
                        recovery = false,
                    )
                ControlTrafficClass.TELEMETRY ->
                    ProtocolBody.PlaybackStatusReport(
                        queueItemId = QueueItemId("queue-$seed"),
                        positionMs = seed.toLong(),
                        isPlaying = false,
                        participation = LocalPlaybackParticipation.ACTIVE,
                        driftMs = null,
                        playbackRevision = 0L,
                        queueRevision = 0L,
                        canonicalSequence = 0L,
                    )
                ControlTrafficClass.TRANSFER -> ProtocolBody.TrackHave(TrackId(seed.toString(16).padStart(64, '0')))
            }
        return Envelope(
            protocolVersion = PROTOCOL_VERSION,
            roomId = "room",
            term = 1L,
            coordinatorPeerId = PeerId("peer-priority"),
            senderPeerId = PeerId("peer-priority"),
            sequence = if (trafficClass == ControlTrafficClass.GUARANTEED) seed.toLong() else null,
            messageId = UUID.randomUUID().toString(),
            sentAtElapsedNs = seed.toLong(),
            body = body,
        )
    }
}
