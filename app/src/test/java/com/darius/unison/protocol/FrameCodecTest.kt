package com.darius.unison.protocol

import com.darius.unison.model.PeerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.UUID

class FrameCodecTest {
    @Test
    fun frameRoundTripsAndAuthenticates() {
        val key = Crypto.randomBytes(32)
        val codec = FrameCodec(key, "room")
        val envelope = Envelope(
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
        val envelope = Envelope(
            roomId = "room", term = 1, senderPeerId = PeerId("peer"),
            messageId = UUID.randomUUID().toString(), sentAtElapsedNs = 10,
            body = ProtocolBody.Heartbeat(7),
        )
        val output = ByteArrayOutputStream().also { codec.write(it, envelope) }.toByteArray()
        output[35] = (output[35].toInt() xor 1).toByte()
        assertThrows(ProtocolException::class.java) { codec.read(ByteArrayInputStream(output)) }
    }

    @Test
    fun roomSecretIsProtectedByPinKey() {
        val secret = Crypto.randomBytes(32)
        val key = Crypto.derivePinKey("room", "123456", "nonce")
        val encrypted = Crypto.encryptAesGcm(key, secret, "room:peer".encodeToByteArray())
        val decrypted = Crypto.decryptAesGcm(key, encrypted.ciphertext, encrypted.iv, "room:peer".encodeToByteArray())
        assertEquals(secret.toList(), decrypted.toList())
    }
}
