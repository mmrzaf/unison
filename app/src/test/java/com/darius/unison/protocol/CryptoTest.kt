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
    fun pinProofChangesWithNonce() {
        val first = Crypto.pinProof("room", "123456", "nonce-one")
        val second = Crypto.pinProof("room", "123456", "nonce-two")
        assertFalse(first == second)
        assertTrue(first.isNotBlank())
    }
}
