package com.darius.unison.protocol

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthenticatedFileStreamCodecTest {
    @Test
    fun encryptedChunksRoundTripAcrossRecordBoundaries() {
        val bytes =
            ByteArray(AuthenticatedFileStreamCodec.MAX_CHUNK_BYTES * 2 + 137) {
                (it % 251).toByte()
            }
        val key = Crypto.randomBytes(32)
        val nonce = Crypto.randomBytes(12)
        val aad = "room-track-request".encodeToByteArray()
        val wire = ByteArrayOutputStream()

        AuthenticatedFileStreamCodec.writeBody(
            ByteArrayInputStream(bytes),
            wire,
            bytes.size.toLong(),
            key,
            nonce,
            aad,
        )
        val decoded =
            AuthenticatedFileStreamCodec.bodyInputStream(
                    ByteArrayInputStream(wire.toByteArray()),
                    bytes.size.toLong(),
                    key,
                    nonce,
                    aad,
                )
                .readBytes()

        assertTrue(bytes.contentEquals(decoded))
    }

    @Test
    fun tamperedChunkIsRejectedBeforePlaintextIsReturned() {
        val bytes = ByteArray(4096) { (it % 239).toByte() }
        val key = Crypto.randomBytes(32)
        val nonce = Crypto.randomBytes(12)
        val aad = "bound-transcript".encodeToByteArray()
        val wire =
            ByteArrayOutputStream()
                .also {
                    AuthenticatedFileStreamCodec.writeBody(
                        ByteArrayInputStream(bytes),
                        it,
                        bytes.size.toLong(),
                        key,
                        nonce,
                        aad,
                    )
                }
                .toByteArray()
        wire[wire.lastIndex] = (wire.last().toInt() xor 1).toByte()

        assertProtocolFailure {
            AuthenticatedFileStreamCodec.bodyInputStream(
                    ByteArrayInputStream(wire),
                    bytes.size.toLong(),
                    key,
                    nonce,
                    aad,
                )
                .readBytes()
        }
    }

    @Test
    fun replayedRecordSequenceIsRejected() {
        val key = Crypto.randomBytes(32)
        val nonce = Crypto.randomBytes(12)
        val aad = "bound-transcript".encodeToByteArray()
        val wire =
            ByteArrayOutputStream().also {
                AuthenticatedFileStreamCodec.writeRecord(
                    it,
                    key,
                    nonce,
                    aad,
                    3,
                    byteArrayOf(1, 2, 3),
                )
            }

        assertProtocolFailure {
            AuthenticatedFileStreamCodec.readRecord(
                ByteArrayInputStream(wire.toByteArray()),
                key,
                nonce,
                aad,
                expectedSequence = 4,
            )
        }
    }

    @Test
    fun deterministicBoundaryAndRandomSizesRoundTrip() {
        val random = java.util.Random(7L)
        val sizes = buildList {
            addAll(listOf(1, 2, 15, 16, 17, 255, 1_024, 65_535, 65_536, 65_537, 131_071))
            repeat(40) { add(1 + random.nextInt(200_000)) }
        }
        sizes.forEach { size ->
            val bytes = ByteArray(size).also(random::nextBytes)
            val key = Crypto.randomBytes(32)
            val nonce = Crypto.randomBytes(12)
            val aad = "fuzz-$size".encodeToByteArray()
            val wire = ByteArrayOutputStream()
            AuthenticatedFileStreamCodec.writeBody(
                ByteArrayInputStream(bytes),
                wire,
                size.toLong(),
                key,
                nonce,
                aad,
            )
            val decoded =
                AuthenticatedFileStreamCodec.bodyInputStream(
                        ByteArrayInputStream(wire.toByteArray()),
                        size.toLong(),
                        key,
                        nonce,
                        aad,
                    )
                    .readBytes()
            if (!bytes.contentEquals(decoded))
                throw AssertionError("round trip failed at size=$size")
        }
    }

    @Test
    fun wrongAssociatedDataIsRejected() {
        val bytes = ByteArray(128) { it.toByte() }
        val key = Crypto.randomBytes(32)
        val nonce = Crypto.randomBytes(12)
        val wire =
            ByteArrayOutputStream().also {
                AuthenticatedFileStreamCodec.writeBody(
                    ByteArrayInputStream(bytes),
                    it,
                    bytes.size.toLong(),
                    key,
                    nonce,
                    "correct".encodeToByteArray(),
                )
            }

        assertProtocolFailure {
            AuthenticatedFileStreamCodec.bodyInputStream(
                    ByteArrayInputStream(wire.toByteArray()),
                    bytes.size.toLong(),
                    key,
                    nonce,
                    "wrong".encodeToByteArray(),
                )
                .readBytes()
        }
    }

    @Test
    fun malformedDeclaredRecordLengthIsRejectedBeforeAllocation() {
        val malformed =
            ByteArrayOutputStream()
                .also { output ->
                    java.io.DataOutputStream(output).apply {
                        writeInt(1)
                        writeInt(Int.MAX_VALUE)
                    }
                }
                .toByteArray()

        assertProtocolFailure {
            AuthenticatedFileStreamCodec.readRecord(
                ByteArrayInputStream(malformed),
                Crypto.randomBytes(32),
                Crypto.randomBytes(12),
                byteArrayOf(1),
                expectedSequence = 1,
            )
        }
    }

    private fun assertProtocolFailure(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected ProtocolException")
        } catch (_: ProtocolException) {
            // Expected.
        }
    }
}
