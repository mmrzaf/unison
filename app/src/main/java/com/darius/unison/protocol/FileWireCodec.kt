package com.darius.unison.protocol

import java.io.InputStream
import java.io.OutputStream

object FileWireCodec {
    private const val MAX_HEADER = 64 * 1024

    fun writeEncryptedHeader(
        output: OutputStream,
        header: FileResponseHeader,
        key: ByteArray,
        baseNonce: ByteArray,
        associatedData: ByteArray,
    ) {
        val bytes = ProtocolJson.encodeToString(header).encodeToByteArray()
        try {
            require(bytes.size <= MAX_HEADER) { "File header too large" }
            AuthenticatedFileStreamCodec.writeRecord(
                output,
                key,
                baseNonce,
                associatedData,
                sequence = 0,
                plaintext = bytes,
            )
        } finally {
            bytes.fill(0)
        }
    }

    fun readEncryptedHeader(
        input: InputStream,
        key: ByteArray,
        baseNonce: ByteArray,
        associatedData: ByteArray,
    ): FileResponseHeader {
        val bytes =
            AuthenticatedFileStreamCodec.readRecord(
                input,
                key,
                baseNonce,
                associatedData,
                expectedSequence = 0,
                maxPlaintextBytes = MAX_HEADER,
            )
        return try {
            ProtocolJson.decodeFromString(bytes.decodeToString())
        } catch (error: Exception) {
            throw ProtocolException("Invalid file response header", error)
        } finally {
            bytes.fill(0)
        }
    }

    fun writeEncryptedBody(
        input: InputStream,
        output: OutputStream,
        byteCount: Long,
        key: ByteArray,
        baseNonce: ByteArray,
        associatedData: ByteArray,
        onBytesWritten: (Long) -> Unit = {},
    ) =
        AuthenticatedFileStreamCodec.writeBody(
            input,
            output,
            byteCount,
            key,
            baseNonce,
            associatedData,
            onBytesWritten,
        )

    fun encryptedBodyInputStream(
        input: InputStream,
        expectedBytes: Long,
        key: ByteArray,
        baseNonce: ByteArray,
        associatedData: ByteArray,
    ): InputStream =
        AuthenticatedFileStreamCodec.bodyInputStream(
            input,
            expectedBytes,
            key,
            baseNonce,
            associatedData,
        )
}
