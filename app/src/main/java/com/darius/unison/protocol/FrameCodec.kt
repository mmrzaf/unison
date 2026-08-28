package com.darius.unison.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Length-delimited, directionally encrypted control frames.
 *
 * Protocol 2 uses AES-GCM. The fixed header is authenticated as associated data and the message
 * UUID remains available before JSON decoding for replay accounting and framing validation.
 */
class FrameCodec(
    writeKey: ByteArray,
    readKey: ByteArray = writeKey,
    private val expectedRoomId: String? = null,
) : AutoCloseable {
    private val writeKey = writeKey.copyOf()
    private val readKey = readKey.copyOf()

    constructor(
        sessionKey: ByteArray,
        expectedRoomId: String?,
    ) : this(
        writeKey = sessionKey,
        readKey = sessionKey,
        expectedRoomId = expectedRoomId,
    )

    init {
        require(this.writeKey.size in AES_KEY_SIZES) { "Invalid write key size" }
        require(this.readKey.size in AES_KEY_SIZES) { "Invalid read key size" }
    }

    fun write(
        output: OutputStream,
        envelope: Envelope,
    ) {
        require(envelope.protocolVersion == PROTOCOL_VERSION)
        if (expectedRoomId != null) require(envelope.roomId == expectedRoomId)
        val plaintext = ProtocolJson.encodeToString(envelope).encodeToByteArray()
        try {
            require(plaintext.size <= MAX_CONTROL_PAYLOAD_BYTES) { "Control payload too large" }
            val messageUuid = UUID.fromString(envelope.messageId)
            val encryptedLength = plaintext.size + GCM_TAG_BYTES
            val header =
                ByteBuffer.allocate(HEADER_BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .apply {
                        put(MAGIC)
                        putShort(PROTOCOL_VERSION.toShort())
                        put(FLAG_ENCRYPTED)
                        putInt(encryptedLength)
                        putLong(messageUuid.mostSignificantBits)
                        putLong(messageUuid.leastSignificantBits)
                    }
                    .array()
            val encrypted = Crypto.encryptAesGcm(writeKey, plaintext, header)
            try {
                check(encrypted.ciphertext.size == encryptedLength)
                DataOutputStream(output).apply {
                    write(header)
                    write(encrypted.iv)
                    write(encrypted.ciphertext)
                    flush()
                }
            } finally {
                encrypted.iv.fill(0)
                encrypted.ciphertext.fill(0)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    fun read(input: InputStream): Envelope {
        val data = DataInputStream(input)
        val header = ByteArray(HEADER_BYTES)
        data.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw ProtocolException("Invalid frame magic")
        val version = buffer.short.toInt() and 0xffff
        if (version != PROTOCOL_VERSION)
            throw ProtocolException("Unsupported protocol version $version")
        val flags = buffer.get()
        if (flags != FLAG_ENCRYPTED) throw ProtocolException("Unsupported frame flags")
        val encryptedLength = buffer.int
        if (encryptedLength !in GCM_TAG_BYTES..MAX_ENCRYPTED_CONTROL_PAYLOAD_BYTES) {
            throw ProtocolException("Invalid payload length $encryptedLength")
        }
        val uuid = UUID(buffer.long, buffer.long)
        val iv = ByteArray(GCM_IV_BYTES)
        data.readFully(iv)
        val encrypted = ByteArray(encryptedLength)
        data.readFully(encrypted)
        val plaintext =
            try {
                Crypto.decryptAesGcm(readKey, encrypted, iv, header)
            } catch (error: Exception) {
                throw ProtocolException("Frame authentication failed", error)
            } finally {
                encrypted.fill(0)
                iv.fill(0)
            }
        try {
            if (plaintext.isEmpty() || plaintext.size > MAX_CONTROL_PAYLOAD_BYTES) {
                throw ProtocolException("Invalid decrypted payload length")
            }
            val envelope =
                try {
                    ProtocolJson.decodeFromString<Envelope>(plaintext.decodeToString())
                } catch (error: Exception) {
                    throw ProtocolException("Invalid frame JSON", error)
                }
            if (envelope.messageId != uuid.toString())
                throw ProtocolException("Message ID mismatch")
            if (envelope.protocolVersion != version)
                throw ProtocolException("Envelope protocol mismatch")
            if (expectedRoomId != null && envelope.roomId != expectedRoomId)
                throw ProtocolException("Wrong room")
            return envelope
        } finally {
            plaintext.fill(0)
        }
    }

    override fun close() {
        writeKey.fill(0)
        readKey.fill(0)
    }

    companion object {
        private const val HEADER_BYTES = 27
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BYTES = 16
        private const val MAX_ENCRYPTED_CONTROL_PAYLOAD_BYTES =
            MAX_CONTROL_PAYLOAD_BYTES + GCM_TAG_BYTES
        private const val FLAG_ENCRYPTED: Byte = 1
        private val AES_KEY_SIZES = setOf(16, 24, 32)
        private val MAGIC =
            byteArrayOf('U'.code.toByte(), 'N'.code.toByte(), 'S'.code.toByte(), 'N'.code.toByte())
    }
}
