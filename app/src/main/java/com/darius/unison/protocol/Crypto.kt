package com.darius.unison.protocol

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object Crypto {
    private val secureRandom = SecureRandom()

    fun randomBytes(size: Int): ByteArray {
        require(size in 1..MAX_RANDOM_BYTES) { "Invalid random byte count" }
        return ByteArray(size).also(secureRandom::nextBytes)
    }

    fun randomBase64(size: Int): String {
        val bytes = randomBytes(size)
        return try {
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    fun randomFourDigitPin(): String = secureRandom.nextInt(10_000).toString().padStart(4, '0')

    fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    fun sha256Hex(bytes: ByteArray): String {
        val digest = sha256(bytes)
        return try {
            buildString(digest.size * 2) {
                digest.forEach { value ->
                    val unsigned = value.toInt() and 0xff
                    append(HEX[unsigned ushr 4])
                    append(HEX[unsigned and 0x0f])
                }
            }
        } finally {
            digest.fill(0)
        }
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            require(key.isNotEmpty()) { "HMAC key must not be empty" }
            init(SecretKeySpec(key, "HmacSHA256"))
            doFinal(data)
        }

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

    fun hkdfSha256(
        input: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int = 32,
    ): ByteArray {
        require(input.isNotEmpty()) { "HKDF input must not be empty" }
        require(length in 1..MAX_HKDF_BYTES) { "Invalid HKDF output length" }
        val generatedSalt = salt.isEmpty()
        val effectiveSalt = if (generatedSalt) ByteArray(32) else salt
        val prk = hmacSha256(effectiveSalt, input)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        try {
            var written = 0
            var counter = 1
            while (written < length) {
                val blockInput = ByteArray(previous.size + info.size + 1)
                try {
                    previous.copyInto(blockInput)
                    info.copyInto(blockInput, previous.size)
                    blockInput[blockInput.lastIndex] = counter.toByte()
                    val next = hmacSha256(prk, blockInput)
                    previous.fill(0)
                    previous = next
                } finally {
                    blockInput.fill(0)
                }
                val count = minOf(previous.size, length - written)
                previous.copyInto(output, written, 0, count)
                written += count
                counter++
            }
            return output
        } finally {
            previous.fill(0)
            prk.fill(0)
            if (generatedSalt) effectiveSalt.fill(0)
        }
    }

    data class ControlSessionKeys(
        val clientToCoordinator: ByteArray,
        val coordinatorToClient: ByteArray,
    )

    fun deriveControlSessionKeys(
        roomSecret: ByteArray,
        clientNonce: String,
        serverNonce: String,
    ): ControlSessionKeys {
        val salt = (clientNonce + serverNonce).encodeToByteArray()
        val clientInfo = "unison-control-client-to-coordinator-v1".encodeToByteArray()
        val coordinatorInfo = "unison-control-coordinator-to-client-v1".encodeToByteArray()
        return try {
            val clientKey = hkdfSha256(roomSecret, salt, clientInfo)
            try {
                ControlSessionKeys(
                    clientToCoordinator = clientKey,
                    coordinatorToClient = hkdfSha256(roomSecret, salt, coordinatorInfo),
                )
            } catch (error: Throwable) {
                clientKey.fill(0)
                throw error
            }
        } finally {
            salt.fill(0)
            clientInfo.fill(0)
            coordinatorInfo.fill(0)
        }
    }

    fun reconnectProof(
        roomSecret: ByteArray,
        roomId: String,
        peerId: String,
        clientNonce: String,
    ): String {
        val key = deriveReconnectKey(roomSecret, roomId, peerId, clientNonce)
        val transcript = "unison-reconnect-proof:$roomId:$peerId:$clientNonce".encodeToByteArray()
        return try {
            val proof = hmacSha256(key, transcript)
            try {
                Base64.getUrlEncoder().withoutPadding().encodeToString(proof)
            } finally {
                proof.fill(0)
            }
        } finally {
            key.fill(0)
            transcript.fill(0)
        }
    }

    fun deriveReconnectKey(
        roomSecret: ByteArray,
        roomId: String,
        peerId: String,
        clientNonce: String,
    ): ByteArray {
        val saltInput = "unison-reconnect-salt:$roomId:$peerId:$clientNonce".encodeToByteArray()
        val salt =
            try {
                sha256(saltInput)
            } finally {
                saltInput.fill(0)
            }
        val info = "unison-reconnect-credential-v1".encodeToByteArray()
        return try {
            hkdfSha256(roomSecret, salt, info)
        } finally {
            salt.fill(0)
            info.fill(0)
        }
    }

    fun deriveFileTransferAuthenticationKey(
        authorizationToken: String,
        roomId: String,
        trackId: String,
    ): ByteArray {
        val tokenBytes = authorizationToken.encodeToByteArray()
        val saltInput = "unison-file-transfer-salt:$roomId:$trackId".encodeToByteArray()
        val salt =
            try {
                sha256(saltInput)
            } finally {
                saltInput.fill(0)
            }
        val info = "unison-file-transfer-authentication-v1".encodeToByteArray()
        return try {
            hkdfSha256(tokenBytes, salt, info)
        } finally {
            tokenBytes.fill(0)
            salt.fill(0)
            info.fill(0)
        }
    }

    fun fileTransferAuthorizationId(authorizationToken: String): String {
        val input = "unison-file-authorization-id-v1:$authorizationToken".encodeToByteArray()
        val digest =
            try {
                sha256(input)
            } finally {
                input.fill(0)
            }
        return try {
            val truncated = digest.copyOf(16)
            try {
                Base64.getUrlEncoder().withoutPadding().encodeToString(truncated)
            } finally {
                truncated.fill(0)
            }
        } finally {
            digest.fill(0)
        }
    }

    fun fileTransferProof(
        authorizationToken: String,
        roomId: String,
        trackId: String,
        requestId: String,
        sourcePeerId: String,
        destinationPeerId: String,
        offset: Long,
        clientNonce: String,
        serverNonce: String,
    ): String {
        val key = deriveFileTransferAuthenticationKey(authorizationToken, roomId, trackId)
        val transcript =
            fileTransferTranscript(
                roomId,
                trackId,
                requestId,
                sourcePeerId,
                destinationPeerId,
                offset,
                clientNonce,
                serverNonce,
            )
        return try {
            val proof = hmacSha256(key, transcript)
            try {
                Base64.getUrlEncoder().withoutPadding().encodeToString(proof)
            } finally {
                proof.fill(0)
            }
        } finally {
            key.fill(0)
            transcript.fill(0)
        }
    }

    fun deriveFileTransferSessionKey(
        authorizationToken: String,
        roomId: String,
        trackId: String,
        clientNonce: String,
        serverNonce: String,
    ): ByteArray {
        val authenticationKey =
            deriveFileTransferAuthenticationKey(authorizationToken, roomId, trackId)
        val saltInput = "unison-file-session-salt-v1:$clientNonce:$serverNonce".encodeToByteArray()
        val salt =
            try {
                sha256(saltInput)
            } finally {
                saltInput.fill(0)
            }
        val info = "unison-file-session-encryption-v1".encodeToByteArray()
        return try {
            hkdfSha256(authenticationKey, salt, info)
        } finally {
            authenticationKey.fill(0)
            salt.fill(0)
            info.fill(0)
        }
    }

    fun fileTransferAssociatedData(
        roomId: String,
        trackId: String,
        requestId: String,
        sourcePeerId: String,
        destinationPeerId: String,
        offset: Long,
        clientNonce: String,
        serverNonce: String,
    ): ByteArray =
        fileTransferTranscript(
            roomId,
            trackId,
            requestId,
            sourcePeerId,
            destinationPeerId,
            offset,
            clientNonce,
            serverNonce,
        )

    private fun fileTransferTranscript(
        roomId: String,
        trackId: String,
        requestId: String,
        sourcePeerId: String,
        destinationPeerId: String,
        offset: Long,
        clientNonce: String,
        serverNonce: String,
    ): ByteArray =
        listOf(
                "unison-file-transfer-v1",
                roomId,
                trackId,
                requestId,
                sourcePeerId,
                destinationPeerId,
                offset.toString(),
                clientNonce,
                serverNonce,
            )
            .joinToString("\u0000")
            .encodeToByteArray()

    data class EncryptedValue(val ciphertext: ByteArray, val iv: ByteArray)

    fun encryptAesGcm(
        key: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
    ): EncryptedValue {
        val iv = randomBytes(12)
        return EncryptedValue(encryptAesGcm(key, plaintext, iv, associatedData), iv)
    }

    fun encryptAesGcm(
        key: ByteArray,
        plaintext: ByteArray,
        iv: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        requireAesKey(key)
        require(iv.size == 12) { "Invalid AES-GCM IV" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(associatedData)
        return cipher.doFinal(plaintext)
    }

    fun decryptAesGcm(
        key: ByteArray,
        ciphertext: ByteArray,
        iv: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
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
    private const val HEX = "0123456789abcdef"
}
