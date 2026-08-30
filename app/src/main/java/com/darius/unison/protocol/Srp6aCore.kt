package com.darius.unison.protocol

import java.math.BigInteger
import java.security.MessageDigest

/**
 * Minimal SRP-6a arithmetic shared by the room-code PAKE and deterministic conformance tests.
 *
 * This deliberately contains only the standard verifier/public/shared-secret equations. Unison's
 * transcript binding, HMAC proofs, HKDF session-key derivation, admission throttling, and secret
 * cleanup remain in [PinPake]. [BigInteger.modPow] is not a constant-time primitive on the JVM; the
 * local-network threat-model implication is documented in SECURITY.md.
 */
internal object Srp6aCore {
    fun multiplier(
        modulus: BigInteger,
        generator: BigInteger,
        digestAlgorithm: String,
    ): BigInteger {
        val paddedModulus = pad(modulus, modulus)
        val paddedGenerator = pad(generator, modulus)
        return try {
            hashInteger(digestAlgorithm, paddedModulus, paddedGenerator)
        } finally {
            paddedModulus.fill(0)
            paddedGenerator.fill(0)
        }
    }

    fun privateKey(
        identity: String,
        password: String,
        salt: ByteArray,
        digestAlgorithm: String,
    ): BigInteger {
        val identitySecret = "$identity:$password".encodeToByteArray()
        val identityHash =
            try {
                hash(digestAlgorithm, identitySecret)
            } finally {
                identitySecret.fill(0)
            }
        return try {
            hashInteger(digestAlgorithm, salt, identityHash)
        } finally {
            identityHash.fill(0)
        }
    }

    fun verifier(
        modulus: BigInteger,
        generator: BigInteger,
        privateKey: BigInteger,
    ): BigInteger = generator.modPow(privateKey, modulus)

    fun clientPublicValue(
        modulus: BigInteger,
        generator: BigInteger,
        clientPrivateValue: BigInteger,
    ): BigInteger = generator.modPow(clientPrivateValue, modulus)

    fun serverPublicValue(
        modulus: BigInteger,
        generator: BigInteger,
        multiplier: BigInteger,
        verifier: BigInteger,
        serverPrivateValue: BigInteger,
    ): BigInteger =
        multiplier
            .multiply(verifier)
            .add(generator.modPow(serverPrivateValue, modulus))
            .mod(modulus)

    fun scramblingParameter(
        modulus: BigInteger,
        clientPublicValue: BigInteger,
        serverPublicValue: BigInteger,
        digestAlgorithm: String,
    ): BigInteger {
        val paddedClient = pad(clientPublicValue, modulus)
        val paddedServer = pad(serverPublicValue, modulus)
        return try {
            hashInteger(digestAlgorithm, paddedClient, paddedServer)
        } finally {
            paddedClient.fill(0)
            paddedServer.fill(0)
        }
    }

    fun clientSharedSecret(
        modulus: BigInteger,
        generator: BigInteger,
        multiplier: BigInteger,
        privateKey: BigInteger,
        clientPrivateValue: BigInteger,
        serverPublicValue: BigInteger,
        scramblingParameter: BigInteger,
    ): BigInteger {
        val gx = generator.modPow(privateKey, modulus)
        val base = serverPublicValue.subtract(multiplier.multiply(gx)).mod(modulus)
        require(base != BigInteger.ZERO) { "Invalid SRP server public value" }
        val exponent = clientPrivateValue.add(scramblingParameter.multiply(privateKey))
        return base.modPow(exponent, modulus)
    }

    fun serverSharedSecret(
        modulus: BigInteger,
        verifier: BigInteger,
        clientPublicValue: BigInteger,
        serverPrivateValue: BigInteger,
        scramblingParameter: BigInteger,
    ): BigInteger =
        clientPublicValue
            .multiply(verifier.modPow(scramblingParameter, modulus))
            .mod(modulus)
            .modPow(serverPrivateValue, modulus)

    private fun hashInteger(
        digestAlgorithm: String,
        vararg values: ByteArray,
    ): BigInteger {
        val digest = hash(digestAlgorithm, *values)
        return try {
            BigInteger(1, digest)
        } finally {
            digest.fill(0)
        }
    }

    private fun hash(
        digestAlgorithm: String,
        vararg values: ByteArray,
    ): ByteArray =
        MessageDigest.getInstance(digestAlgorithm).run {
            values.forEach(::update)
            digest()
        }

    private fun pad(value: BigInteger, modulus: BigInteger): ByteArray {
        val modulusBytes = (modulus.bitLength() + 7) / 8
        val encoded = value.toByteArray()
        val raw =
            if (encoded.size > 1 && encoded[0] == 0.toByte()) encoded.copyOfRange(1, encoded.size)
            else encoded
        return try {
            require(raw.size <= modulusBytes) { "SRP value exceeds modulus width" }
            ByteArray(modulusBytes).also { raw.copyInto(it, modulusBytes - raw.size) }
        } finally {
            if (raw !== encoded) raw.fill(0)
            encoded.fill(0)
        }
    }
}
