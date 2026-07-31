package com.darius.unison.protocol

import java.math.BigInteger
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Balanced SRP-6a exchange for the short room code.
 *
 * The four-digit code is never sent and cannot be tested from a passively captured handshake.
 * Admission throttling remains mandatory because an active client can still make online guesses.
 */
object PinPake {
    data class Challenge(
        val saltBase64: String,
        val serverPublicValueBase64: String,
        val serverNonce: String,
    )

    data class ClientProof(
        val proofBase64: String,
        val sessionKey: ByteArray,
        val expectedServerProofBase64: String,
    )

    data class ServerProof(
        val proofBase64: String,
        val sessionKey: ByteArray,
    )

    class ClientSession
    private constructor(
        private val roomId: String,
        private val peerId: String,
        private val clientNonce: String,
        private val pin: String,
        private val privateValue: BigInteger,
        private val publicValue: BigInteger,
    ) {
        private val consumed = AtomicBoolean(false)
        val publicValueBase64: String = encodeElement(publicValue)

        fun answer(challenge: Challenge): ClientProof {
            check(consumed.compareAndSet(false, true)) { "PIN exchange was already used" }
            requirePin(pin)
            val salt = decodeBytes(challenge.saltBase64, SALT_BYTES, "Invalid PIN challenge salt")
            val transcript = transcript(roomId, peerId, clientNonce, challenge.serverNonce)
            var clientProof: ByteArray? = null
            var expectedServerProof: ByteArray? = null
            try {
                val serverPublic = decodeElement(challenge.serverPublicValueBase64)
                require(serverPublic.mod(MODULUS) != ZERO) { "Invalid PIN challenge" }
                val scrambling = hashPaddedIntegers(publicValue, serverPublic)
                require(scrambling != ZERO) { "Invalid PIN challenge" }
                val x = privateKey(roomId, pin, salt)
                val gx = GENERATOR.modPow(x, MODULUS)
                val base = serverPublic.subtract(MULTIPLIER.multiply(gx)).mod(MODULUS)
                require(base != ZERO) { "Invalid PIN challenge" }
                val exponent = privateValue.add(scrambling.multiply(x))
                val sharedSecret = base.modPow(exponent, MODULUS)
                val sessionKey = deriveSessionKey(sharedSecret, transcript)
                clientProof =
                    proof(sessionKey, CLIENT_PROOF_DOMAIN, transcript, publicValue, serverPublic)
                expectedServerProof = serverProof(sessionKey, transcript, publicValue, clientProof)
                return ClientProof(
                    proofBase64 = encodeBytes(clientProof),
                    sessionKey = sessionKey,
                    expectedServerProofBase64 = encodeBytes(expectedServerProof),
                )
            } finally {
                salt.fill(0)
                transcript.fill(0)
                clientProof?.fill(0)
                expectedServerProof?.fill(0)
            }
        }

        companion object {
            fun start(
                roomId: String,
                peerId: String,
                clientNonce: String,
                pin: String,
            ): ClientSession {
                requirePin(pin)
                val privateValue = randomExponent()
                val publicValue = GENERATOR.modPow(privateValue, MODULUS)
                return ClientSession(roomId, peerId, clientNonce, pin, privateValue, publicValue)
            }
        }
    }

    class ServerSession
    private constructor(
        private val roomId: String,
        private val peerId: String,
        private val clientNonce: String,
        private val clientPublic: BigInteger,
        private val privateValue: BigInteger,
        private val verifier: BigInteger,
        private val serverPublic: BigInteger,
        private val salt: ByteArray,
        val challenge: Challenge,
    ) {
        private val consumed = AtomicBoolean(false)

        fun verify(clientProofBase64: String): ServerProof? {
            if (!consumed.compareAndSet(false, true)) return null
            val providedProof =
                runCatching { decodeBytes(clientProofBase64, PROOF_BYTES, "Invalid PIN proof") }
                    .getOrNull() ?: return null
            val transcript = transcript(roomId, peerId, clientNonce, challenge.serverNonce)
            var expectedProof: ByteArray? = null
            var responseProof: ByteArray? = null
            var sessionKey: ByteArray? = null
            try {
                val scrambling = hashPaddedIntegers(clientPublic, serverPublic)
                if (scrambling == ZERO) return null
                val sharedSecret =
                    clientPublic
                        .multiply(verifier.modPow(scrambling, MODULUS))
                        .mod(MODULUS)
                        .modPow(privateValue, MODULUS)
                sessionKey = deriveSessionKey(sharedSecret, transcript)
                expectedProof =
                    proof(sessionKey, CLIENT_PROOF_DOMAIN, transcript, clientPublic, serverPublic)
                if (!Crypto.constantTimeEquals(expectedProof, providedProof)) {
                    sessionKey.fill(0)
                    sessionKey = null
                    return null
                }
                responseProof = serverProof(sessionKey, transcript, clientPublic, providedProof)
                return ServerProof(encodeBytes(responseProof), sessionKey).also {
                    sessionKey = null
                }
            } finally {
                providedProof.fill(0)
                expectedProof?.fill(0)
                responseProof?.fill(0)
                sessionKey?.fill(0)
                transcript.fill(0)
                salt.fill(0)
            }
        }

        companion object {
            fun start(
                roomId: String,
                peerId: String,
                clientNonce: String,
                pin: String,
                clientPublicValueBase64: String,
            ): ServerSession {
                requirePin(pin)
                val clientPublic = decodeElement(clientPublicValueBase64)
                require(clientPublic.mod(MODULUS) != ZERO) { "Invalid PIN public value" }
                val salt = Crypto.randomBytes(SALT_BYTES)
                val x = privateKey(roomId, pin, salt)
                val verifier = GENERATOR.modPow(x, MODULUS)
                val privateValue = randomExponent()
                val serverPublic =
                    MULTIPLIER.multiply(verifier)
                        .add(GENERATOR.modPow(privateValue, MODULUS))
                        .mod(MODULUS)
                require(serverPublic != ZERO) { "Invalid PIN challenge" }
                val serverNonce = Crypto.randomBase64(18)
                return ServerSession(
                    roomId = roomId,
                    peerId = peerId,
                    clientNonce = clientNonce,
                    clientPublic = clientPublic,
                    privateValue = privateValue,
                    verifier = verifier,
                    serverPublic = serverPublic,
                    salt = salt,
                    challenge =
                        Challenge(
                            saltBase64 = encodeBytes(salt),
                            serverPublicValueBase64 = encodeElement(serverPublic),
                            serverNonce = serverNonce,
                        ),
                )
            }
        }
    }

    fun verifyServerProof(expectedBase64: String, actualBase64: String): Boolean {
        val expected =
            runCatching { decodeBytes(expectedBase64, PROOF_BYTES, "Invalid server proof") }
                .getOrNull() ?: return false
        val actual =
            runCatching { decodeBytes(actualBase64, PROOF_BYTES, "Invalid server proof") }
                .getOrNull() ?: return false
        return try {
            Crypto.constantTimeEquals(expected, actual)
        } finally {
            expected.fill(0)
            actual.fill(0)
        }
    }

    private fun privateKey(roomId: String, pin: String, salt: ByteArray): BigInteger {
        val identity = "$roomId:$pin".encodeToByteArray()
        val identityHash =
            try {
                hash(identity)
            } finally {
                identity.fill(0)
            }
        return try {
            hashInteger(salt, identityHash)
        } finally {
            identityHash.fill(0)
        }
    }

    private fun deriveSessionKey(sharedSecret: BigInteger, transcript: ByteArray): ByteArray {
        val paddedSecret = pad(sharedSecret)
        val sharedHash =
            try {
                hash(paddedSecret)
            } finally {
                paddedSecret.fill(0)
            }
        val transcriptHash = hash(transcript)
        return try {
            Crypto.hkdfSha256(
                input = sharedHash,
                salt = transcriptHash,
                info = SESSION_KEY_DOMAIN,
            )
        } finally {
            sharedHash.fill(0)
            transcriptHash.fill(0)
        }
    }

    private fun proof(
        sessionKey: ByteArray,
        domain: ByteArray,
        transcript: ByteArray,
        clientPublic: BigInteger,
        serverPublic: BigInteger,
    ): ByteArray {
        val clientBytes = pad(clientPublic)
        val serverBytes = pad(serverPublic)
        val payload = join(domain, transcript, clientBytes, serverBytes)
        return try {
            Crypto.hmacSha256(sessionKey, payload)
        } finally {
            clientBytes.fill(0)
            serverBytes.fill(0)
            payload.fill(0)
        }
    }

    private fun serverProof(
        sessionKey: ByteArray,
        transcript: ByteArray,
        clientPublic: BigInteger,
        clientProof: ByteArray,
    ): ByteArray {
        val clientBytes = pad(clientPublic)
        val payload = join(SERVER_PROOF_DOMAIN, transcript, clientBytes, clientProof)
        return try {
            Crypto.hmacSha256(sessionKey, payload)
        } finally {
            clientBytes.fill(0)
            payload.fill(0)
        }
    }

    private fun transcript(
        roomId: String,
        peerId: String,
        clientNonce: String,
        serverNonce: String,
    ): ByteArray =
        listOf(TRANSCRIPT_DOMAIN, roomId, peerId, clientNonce, serverNonce)
            .joinToString("\u0000")
            .encodeToByteArray()

    private fun randomExponent(): BigInteger {
        var value: BigInteger
        do {
            val bytes = Crypto.randomBytes(EXPONENT_BYTES)
            value =
                try {
                    BigInteger(1, bytes)
                } finally {
                    bytes.fill(0)
                }
        } while (value == ZERO)
        return value
    }

    private fun hash(vararg values: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            values.forEach(::update)
            digest()
        }

    private fun hashInteger(vararg values: ByteArray): BigInteger {
        val digest = hash(*values)
        return try {
            BigInteger(1, digest)
        } finally {
            digest.fill(0)
        }
    }

    private fun hashPaddedIntegers(vararg values: BigInteger): BigInteger {
        val padded = values.map(::pad)
        return try {
            hashInteger(*padded.toTypedArray())
        } finally {
            padded.forEach { it.fill(0) }
        }
    }

    private fun join(vararg values: ByteArray): ByteArray {
        val size = values.sumOf { it.size }
        return ByteArray(size).also { output ->
            var offset = 0
            values.forEach { value ->
                value.copyInto(output, offset)
                offset += value.size
            }
        }
    }

    private fun pad(value: BigInteger): ByteArray {
        val encoded = value.toByteArray()
        val raw =
            if (encoded.size > 1 && encoded[0] == 0.toByte()) encoded.copyOfRange(1, encoded.size)
            else encoded
        return try {
            require(raw.size <= MODULUS_BYTES) { "Invalid PIN exchange value" }
            ByteArray(MODULUS_BYTES).also { raw.copyInto(it, MODULUS_BYTES - raw.size) }
        } finally {
            if (raw !== encoded) raw.fill(0)
            encoded.fill(0)
        }
    }

    private fun encodeElement(value: BigInteger): String {
        val bytes = pad(value)
        return try {
            encodeBytes(bytes)
        } finally {
            bytes.fill(0)
        }
    }

    private fun decodeElement(value: String): BigInteger {
        val bytes = decodeBytes(value, MODULUS_BYTES, "Invalid PIN exchange value")
        return BigInteger(1, bytes)
            .also { bytes.fill(0) }
            .also {
                require(it > ZERO && it < MODULUS) { "Invalid PIN exchange value" }
            }
    }

    private fun encodeBytes(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun decodeBytes(value: String, expectedSize: Int, message: String): ByteArray {
        require(value.length <= MAX_ENCODED_VALUE_LENGTH) { message }
        val decoded =
            runCatching { Base64.getUrlDecoder().decode(value) }
                .getOrElse { throw IllegalArgumentException(message) }
        if (decoded.size != expectedSize) {
            decoded.fill(0)
            throw IllegalArgumentException(message)
        }
        return decoded
    }

    private fun requirePin(pin: String) {
        require(pin.length == 4 && pin.all(Char::isDigit)) { "Room code must contain four digits" }
    }

    private val MODULUS =
        BigInteger(
            "FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD129024E08" +
                "8A67CC74020BBEA63B139B22514A08798E3404DDEF9519B3CD3A431B" +
                "302B0A6DF25F14374FE1356D6D51C245E485B576625E7EC6F44C42E9" +
                "A637ED6B0BFF5CB6F406B7EDEE386BFB5A899FA5AE9F24117C4B1FE6" +
                "49286651ECE45B3DC2007CB8A163BF0598DA48361C55D39A69163FA8" +
                "FD24CF5F83655D23DCA3AD961C62F356208552BB9ED529077096966D" +
                "670C354E4ABC9804F1746C08CA18217C32905E462E36CE3BE39E772C" +
                "180E86039B2783A2EC07A28FB5C55DF06F4C52C9DE2BCBF695581718" +
                "3995497CEA956AE515D2261898FA051015728E5A8AACAA68FFFFFFFF" +
                "FFFFFFFF",
            16,
        )
    private val GENERATOR = BigInteger.valueOf(2)
    private val ZERO = BigInteger.ZERO
    private val MODULUS_BYTES = (MODULUS.bitLength() + 7) / 8
    private val MULTIPLIER = hashPaddedIntegers(MODULUS, GENERATOR)
    private const val SALT_BYTES = 16
    private const val EXPONENT_BYTES = 32
    private const val PROOF_BYTES = 32
    private const val MAX_ENCODED_VALUE_LENGTH = 768
    private const val TRANSCRIPT_DOMAIN = "unison-pin-srp-v1"
    private val SESSION_KEY_DOMAIN = "unison-pin-srp-wrap-v1".encodeToByteArray()
    private val CLIENT_PROOF_DOMAIN = "unison-pin-srp-client-proof-v1".encodeToByteArray()
    private val SERVER_PROOF_DOMAIN = "unison-pin-srp-server-proof-v1".encodeToByteArray()
}
