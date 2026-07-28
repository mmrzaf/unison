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
    fun reconnectCredentialIsPeerAndNonceBound() {
        val secret = ByteArray(32) { (it * 7).toByte() }
        val first = Crypto.reconnectProof(secret, "room", "peer-a", "nonce-one")
        val same = Crypto.reconnectProof(secret, "room", "peer-a", "nonce-one")
        val otherPeer = Crypto.reconnectProof(secret, "room", "peer-b", "nonce-one")
        val otherNonce = Crypto.reconnectProof(secret, "room", "peer-a", "nonce-two")

        assertEquals(first, same)
        assertFalse(first == otherPeer)
        assertFalse(first == otherNonce)
    }

    @Test
    fun pinProofChangesWithNonce() {
        val first = Crypto.pinProof("room", "123456", "nonce-one")
        val second = Crypto.pinProof("room", "123456", "nonce-two")
        assertFalse(first == second)
        assertTrue(first.isNotBlank())
    }
}
