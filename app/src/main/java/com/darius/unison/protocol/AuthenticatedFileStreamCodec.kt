package com.darius.unison.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AES-GCM records for file headers and chunks. Every chunk is authenticated before it is exposed.
 */
object AuthenticatedFileStreamCodec {
    private const val TAG_BYTES = 16
    private const val NONCE_BYTES = 12
    const val MAX_CHUNK_BYTES = 64 * 1024

    fun writeRecord(
        output: OutputStream,
        key: ByteArray,
        baseNonce: ByteArray,
        associatedData: ByteArray,
        sequence: Int,
        plaintext: ByteArray,
    ) {
        require(sequence >= 0) { "Invalid file record sequence" }
        require(plaintext.size in 1..MAX_CHUNK_BYTES) { "Invalid file record size" }
        val nonce = nonceFor(baseNonce, sequence)
        val recordData = recordAssociatedData(associatedData, sequence, plaintext.size)
        val ciphertext =
            try {
                Crypto.encryptAesGcm(
                    key = key,
                    plaintext = plaintext,
                    iv = nonce,
                    associatedData = recordData,
                )
            } finally {
                nonce.fill(0)
                recordData.fill(0)
            }
        try {
            DataOutputStream(output).apply {
                writeInt(sequence)
                writeInt(plaintext.size)
                write(ciphertext)
                flush()
            }
        } finally {
            ciphertext.fill(0)
        }
    }

    fun readRecord(
        input: InputStream,
        key: ByteArray,
        baseNonce: ByteArray,
        associatedData: ByteArray,
        expectedSequence: Int,
        maxPlaintextBytes: Int = MAX_CHUNK_BYTES,
    ): ByteArray {
        val data = DataInputStream(input)
        val sequence = data.readInt()
        if (sequence != expectedSequence) throw ProtocolException("Unexpected file record sequence")
        val plaintextSize = data.readInt()
        if (plaintextSize !in 1..minOf(MAX_CHUNK_BYTES, maxPlaintextBytes)) {
            throw ProtocolException("Invalid file record size")
        }
        val ciphertext = ByteArray(plaintextSize + TAG_BYTES)
        data.readFully(ciphertext)
        val nonce = nonceFor(baseNonce, sequence)
        val recordData = recordAssociatedData(associatedData, sequence, plaintextSize)
        return try {
            Crypto.decryptAesGcm(
                key = key,
                ciphertext = ciphertext,
                iv = nonce,
                associatedData = recordData,
            )
        } catch (error: Exception) {
            throw ProtocolException("File record authentication failed", error)
        } finally {
            ciphertext.fill(0)
            nonce.fill(0)
            recordData.fill(0)
        }
    }

    fun writeBody(
        input: InputStream,
        output: OutputStream,
        byteCount: Long,
        key: ByteArray,
        baseNonce: ByteArray,
        associatedData: ByteArray,
        onBytesWritten: (Long) -> Unit = {},
    ) {
        require(byteCount >= 0L) { "Invalid file body size" }
        var remaining = byteCount
        var sequence = 1
        var total = 0L
        val buffer = ByteArray(MAX_CHUNK_BYTES)
        try {
            while (remaining > 0L) {
                val wanted = minOf(buffer.size.toLong(), remaining).toInt()
                var filled = 0
                while (filled < wanted) {
                    val read = input.read(buffer, filled, wanted - filled)
                    if (read < 0) throw ProtocolException("Source file ended early")
                    if (read == 0) continue
                    filled += read
                }
                val chunk = buffer.copyOf(filled)
                try {
                    writeRecord(output, key, baseNonce, associatedData, sequence, chunk)
                } finally {
                    chunk.fill(0)
                }
                sequence++
                remaining -= filled
                total += filled
                onBytesWritten(total)
            }
        } finally {
            buffer.fill(0)
        }
    }

    fun bodyInputStream(
        input: InputStream,
        expectedBytes: Long,
        key: ByteArray,
        baseNonce: ByteArray,
        associatedData: ByteArray,
    ): InputStream {
        require(expectedBytes >= 0L) { "Invalid file body size" }
        return AuthenticatedBodyInputStream(input, expectedBytes, key, baseNonce, associatedData)
    }

    private class AuthenticatedBodyInputStream(
        private val source: InputStream,
        private var remaining: Long,
        private val key: ByteArray,
        private val baseNonce: ByteArray,
        private val associatedData: ByteArray,
    ) : InputStream() {
        private var sequence = 1
        private var current = ByteArray(0)
        private var currentOffset = 0

        override fun read(): Int {
            val one = ByteArray(1)
            return try {
                val count = read(one, 0, 1)
                if (count < 0) -1 else one[0].toInt() and 0xff
            } finally {
                one.fill(0)
            }
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            require(offset >= 0 && length >= 0 && offset + length <= buffer.size)
            if (length == 0) return 0
            if (remaining == 0L) return -1
            if (currentOffset >= current.size) loadNextRecord()
            val count = minOf(length, current.size - currentOffset)
            current.copyInto(buffer, offset, currentOffset, currentOffset + count)
            currentOffset += count
            remaining -= count
            if (currentOffset >= current.size) clearCurrent()
            return count
        }

        override fun close() {
            clearCurrent()
            remaining = 0L
        }

        private fun loadNextRecord() {
            clearCurrent()
            val maxSize = minOf(MAX_CHUNK_BYTES.toLong(), remaining).toInt()
            current = readRecord(source, key, baseNonce, associatedData, sequence, maxSize)
            currentOffset = 0
            sequence++
        }

        private fun clearCurrent() {
            current.fill(0)
            current = ByteArray(0)
            currentOffset = 0
        }
    }

    private fun nonceFor(baseNonce: ByteArray, sequence: Int): ByteArray {
        require(baseNonce.size == NONCE_BYTES) { "Invalid file base nonce" }
        val nonce = baseNonce.copyOf()
        val sequenceBytes =
            ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(sequence.toLong()).array()
        try {
            for (index in sequenceBytes.indices) {
                val nonceIndex = nonce.size - sequenceBytes.size + index
                nonce[nonceIndex] =
                    (nonce[nonceIndex].toInt() xor sequenceBytes[index].toInt()).toByte()
            }
            return nonce
        } finally {
            sequenceBytes.fill(0)
        }
    }

    private fun recordAssociatedData(
        base: ByteArray,
        sequence: Int,
        plaintextSize: Int,
    ): ByteArray =
        ByteBuffer.allocate(base.size + 8)
            .order(ByteOrder.BIG_ENDIAN)
            .apply {
                put(base)
                putInt(sequence)
                putInt(plaintextSize)
            }
            .array()
}
