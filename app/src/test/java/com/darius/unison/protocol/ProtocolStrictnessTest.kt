package com.darius.unison.protocol

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProtocolStrictnessTest {
    @Test
    fun protocolOneRejectsUnsupportedHelloShape() {
        val unsupported =
            """{"type":"unsupported_hello","peerId":"peer-123456789012","displayName":"Guest","appVersion":"1.0.0","protocolVersion":1,"listeningPort":4321,"roomId":"room-1234","clientNonce":"nonce-123456789012"}"""

        assertThrows(SerializationException::class.java) {
            ProtocolJson.decodeFromString<HandshakeMessage>(unsupported)
        }
    }

    @Test
    fun protocolOneRejectsUnknownFields() {
        val hello =
            HandshakeMessage.ReconnectClientHello(
                peerId = com.darius.unison.model.PeerId("peer-123456789012"),
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION,
                listeningPort = 4321,
                roomId = "room-1234",
                clientNonce = "nonce-123456789012",
            )
        val encoded = ProtocolJson.encodeToString<HandshakeMessage>(hello)
        val withUnknown = encoded.dropLast(1) + ",\"unsupportedField\":true}"

        assertThrows(SerializationException::class.java) {
            ProtocolJson.decodeFromString<HandshakeMessage>(withUnknown)
        }
    }

    @Test
    fun fileHelloRoundTripsAsItsExactProtocolShape() {
        val hello =
            HandshakeMessage.FileClientHello(
                peerId = com.darius.unison.model.PeerId("peer-123456789012"),
                displayName = "Guest",
                appVersion = "1.0.0",
                protocolVersion = PROTOCOL_VERSION,
                listeningPort = 4321,
                roomId = "room-1234",
                clientNonce = "nonce-123456789012",
                request =
                    FileRequest(
                        requestId = "request-123456789012",
                        roomId = "room-1234",
                        trackId = com.darius.unison.model.TrackId("a".repeat(64)),
                        offset = 0,
                        authorizationId = "authorization-123456789012",
                    ),
            )

        val encoded = ProtocolJson.encodeToString<HandshakeMessage>(hello)
        val decoded = ProtocolJson.decodeFromString<HandshakeMessage>(encoded)

        assertEquals(hello, decoded)
    }
}
