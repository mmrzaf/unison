package com.darius.unison.protocol

import com.darius.unison.model.PeerId
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class FrameCodecTest {
    @Test
    fun frameRoundTripsAndAuthenticates() {
        val key = Crypto.randomBytes(32)
        val codec = FrameCodec(key, "room")
        val envelope =
            Envelope(
                protocolVersion = PROTOCOL_VERSION,
                roomId = "room",
                term = 1,
                senderPeerId = PeerId("peer"),
                messageId = UUID.randomUUID().toString(),
                sentAtElapsedNs = 10,
                body = ProtocolBody.Heartbeat(7),
            )
        val output = ByteArrayOutputStream()
        codec.write(output, envelope)
        assertEquals(envelope, codec.read(ByteArrayInputStream(output.toByteArray())))
    }

    @Test
    fun modifiedFrameIsRejected() {
        val key = Crypto.randomBytes(32)
        val codec = FrameCodec(key, "room")
        val envelope =
            Envelope(
                protocolVersion = PROTOCOL_VERSION,
                roomId = "room",
                term = 1,
                senderPeerId = PeerId("peer"),
                messageId = UUID.randomUUID().toString(),
                sentAtElapsedNs = 10,
                body = ProtocolBody.Heartbeat(7),
            )
        val output = ByteArrayOutputStream().also { codec.write(it, envelope) }.toByteArray()
        output[35] = (output[35].toInt() xor 1).toByte()
        assertThrows(ProtocolException::class.java) { codec.read(ByteArrayInputStream(output)) }
    }

    @Test
    fun envelopeForAnotherRoomIsRejectedBeforeWrite() {
        val codec = FrameCodec(Crypto.randomBytes(32), "accepted-room")
        val envelope =
            Envelope(
                protocolVersion = PROTOCOL_VERSION,
                roomId = "",
                term = 0,
                senderPeerId = PeerId("peer"),
                messageId = UUID.randomUUID().toString(),
                sentAtElapsedNs = 10,
                body = ProtocolBody.ClockPing("ping", 10),
            )

        assertThrows(IllegalArgumentException::class.java) {
            codec.write(ByteArrayOutputStream(), envelope)
        }
    }

    @Test
    fun roomSecretIsProtectedByAuthenticatedHandshakeKey() {
        val secret = Crypto.randomBytes(32)
        val key = Crypto.randomBytes(32)
        val encrypted = Crypto.encryptAesGcm(key, secret, "room:peer".encodeToByteArray())
        val decrypted =
            Crypto.decryptAesGcm(
                key,
                encrypted.ciphertext,
                encrypted.iv,
                "room:peer".encodeToByteArray(),
            )
        assertEquals(secret.toList(), decrypted.toList())
    }
}
