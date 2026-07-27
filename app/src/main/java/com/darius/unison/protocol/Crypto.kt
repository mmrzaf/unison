package com.darius.unison.protocol

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private const val PIN_KDF_ITERATIONS = 120_000
    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray {
        require(size in 1..MAX_RANDOM_BYTES) { "Invalid random byte count" }
        return ByteArray(size).also(secureRandom::nextBytes)
    }
    fun randomBase64(size: Int): String = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes(size))
    fun randomSixDigitPin(): String = (100_000 + secureRandom.nextInt(900_000)).toString()

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    fun sha256Hex(bytes: ByteArray): String = sha256(bytes).joinToString("") { "%02x".format(it) }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run {
        require(key.isNotEmpty()) { "HMAC key must not be empty" }
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(data)
    }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun hkdfSha256(input: ByteArray, salt: ByteArray, info: ByteArray, length: Int = 32): ByteArray {
        require(input.isNotEmpty()) { "HKDF input must not be empty" }
        require(length in 1..MAX_HKDF_BYTES) { "Invalid HKDF output length" }
        val effectiveSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmacSha256(effectiveSalt, input)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < length) {
            val blockInput = previous + info + byteArrayOf(counter.toByte())
            previous = hmacSha256(prk, blockInput)
            val count = minOf(previous.size, length - written)
            previous.copyInto(output, written, 0, count)
            written += count
            counter++
        }
        return output
    }

    fun deriveSessionKey(roomSecret: ByteArray, clientNonce: String, serverNonce: String): ByteArray = hkdfSha256(
        input = roomSecret,
        salt = (clientNonce + serverNonce).encodeToByteArray(),
        info = "unison-protocol-v1".encodeToByteArray(),
    )

    fun pinProof(roomId: String, pin: String, clientNonce: String): String {
        val key = derivePinKey(roomId, pin, clientNonce)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            hmacSha256(key, "unison-pin-proof:$roomId:$clientNonce".encodeToByteArray())
        )
    }

    fun derivePinKey(roomId: String, pin: String, clientNonce: String): ByteArray {
        val salt = sha256("unison-pin-salt:$roomId:$clientNonce".encodeToByteArray())
        val spec = PBEKeySpec(pin.toCharArray(), salt, PIN_KDF_ITERATIONS, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    data class EncryptedValue(val ciphertext: ByteArray, val iv: ByteArray)

    fun encryptAesGcm(key: ByteArray, plaintext: ByteArray, associatedData: ByteArray): EncryptedValue {
        requireAesKey(key)
        val iv = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return EncryptedValue(cipher.doFinal(plaintext), iv)
    }

    fun decryptAesGcm(key: ByteArray, ciphertext: ByteArray, iv: ByteArray, associatedData: ByteArray): ByteArray {
        requireAesKey(key)
        require(iv.size == 12) { "Invalid AES-GCM IV" }
        require(ciphertext.size >= 16) { "Invalid AES-GCM ciphertext" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(ciphertext)
    }

    private fun requireAesKey(key: ByteArray) {
        require(key.size == 16 || key.size == 24 || key.size == 32) { "Invalid AES key size" }
    }

    private const val MAX_RANDOM_BYTES = 1024 * 1024
    private const val MAX_HKDF_BYTES = 255 * 32

}
