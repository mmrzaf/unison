package com.darius.unison.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoTest {
    @Test
    fun aesGcmRoundTripsWithAssociatedData() {
        val key = Crypto.randomBytes(32)
        val plaintext = Crypto.randomBytes(64)
        val aad = "room:peer".encodeToByteArray()
        val encrypted = Crypto.encryptAesGcm(key, plaintext, aad)
        val decrypted = Crypto.decryptAesGcm(key, encrypted.ciphertext, encrypted.iv, aad)
        assertEquals(plaintext.toList(), decrypted.toList())
    }

    @Test
    fun directionalControlKeysAreDistinctAndStable() {
        val secret = ByteArray(32) { it.toByte() }
        val first = Crypto.deriveControlSessionKeys(secret, "client", "server")
        val second = Crypto.deriveControlSessionKeys(secret, "client", "server")

        assertFalse(first.clientToCoordinator.contentEquals(first.coordinatorToClient))
        assertTrue(first.clientToCoordinator.contentEquals(second.clientToCoordinator))
        assertTrue(first.coordinatorToClient.contentEquals(second.coordinatorToClient))
    }

    @Test
    fun reconnectCredentialIsBoundToBothPeersFreshNonces() {
        val secret = ByteArray(32) { (it * 7).toByte() }
        val first = Crypto.reconnectProof(secret, "room", "peer-a", "client-one", "server-one")
        val same = Crypto.reconnectProof(secret, "room", "peer-a", "client-one", "server-one")
        val otherPeer = Crypto.reconnectProof(secret, "room", "peer-b", "client-one", "server-one")
        val otherClientNonce =
            Crypto.reconnectProof(secret, "room", "peer-a", "client-two", "server-one")
        val otherServerNonce =
            Crypto.reconnectProof(secret, "room", "peer-a", "client-one", "server-two")

        assertEquals(first, same)
        assertFalse(first == otherPeer)
        assertFalse(first == otherClientNonce)
        assertFalse(first == otherServerNonce)
    }

    @Test
    fun fileTransferProofIsBoundToTheFullRequestTranscript() {
        val token = "transfer-secret"
        val proof =
            Crypto.fileTransferProof(
                token,
                "room",
                "track",
                "request",
                "source",
                "destination",
                128,
                "client-nonce",
                "server-nonce",
            )
        val same =
            Crypto.fileTransferProof(
                token,
                "room",
                "track",
                "request",
                "source",
                "destination",
                128,
                "client-nonce",
                "server-nonce",
            )
        val otherOffset =
            Crypto.fileTransferProof(
                token,
                "room",
                "track",
                "request",
                "source",
                "destination",
                129,
                "client-nonce",
                "server-nonce",
            )
        val otherDestination =
            Crypto.fileTransferProof(
                token,
                "room",
                "track",
                "request",
                "source",
                "other",
                128,
                "client-nonce",
                "server-nonce",
            )

        assertEquals(proof, same)
        assertFalse(proof == otherOffset)
        assertFalse(proof == otherDestination)
    }

    @Test
    fun fileTransferSessionKeysAreFreshAndAuthorizationIdDoesNotRevealToken() {
        val token = "very-secret-token"
        val id = Crypto.fileTransferAuthorizationId(token)
        val first = Crypto.deriveFileTransferSessionKey(token, "room", "track", "client", "server")
        val same = Crypto.deriveFileTransferSessionKey(token, "room", "track", "client", "server")
        val fresh =
            Crypto.deriveFileTransferSessionKey(token, "room", "track", "client", "server-2")

        assertTrue(id.isNotBlank())
        assertFalse(id.contains(token))
        assertTrue(first.contentEquals(same))
        assertFalse(first.contentEquals(fresh))
    }

    @Test
    fun generatedRoomCodeAlwaysHasFourDigits() {
        repeat(200) {
            val code = Crypto.randomFourDigitPin()
            assertEquals(4, code.length)
            assertTrue(code.all(Char::isDigit))
        }
    }
}
