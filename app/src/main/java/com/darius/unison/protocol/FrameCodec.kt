package com.darius.unison.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class FrameCodec(
    writeKey: ByteArray,
    readKey: ByteArray = writeKey,
    private val expectedRoomId: String? = null,
) {
    private val writeKey = writeKey.copyOf()
    private val readKey = readKey.copyOf()

    constructor(sessionKey: ByteArray, expectedRoomId: String?) : this(
        writeKey = sessionKey,
        readKey = sessionKey,
        expectedRoomId = expectedRoomId,
    )

    init {
        require(this.writeKey.size >= 16) { "Write key is too short" }
        require(this.readKey.size >= 16) { "Read key is too short" }
    }

    fun write(output: OutputStream, envelope: Envelope, channelType: ChannelType = ChannelType.CONTROL) {
        require(envelope.protocolVersion == PROTOCOL_VERSION)
        if (expectedRoomId != null) require(envelope.roomId == expectedRoomId)
        val payload = ProtocolJson.encodeToString(envelope).encodeToByteArray()
        require(payload.size <= MAX_CONTROL_PAYLOAD_BYTES) { "Control payload too large" }
        val messageUuid = UUID.fromString(envelope.messageId)
        val header = ByteBuffer.allocate(28).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC)
            putShort(PROTOCOL_VERSION.toShort())
            put(channelType.ordinal.toByte())
            put(0)
            putInt(payload.size)
            putLong(messageUuid.mostSignificantBits)
            putLong(messageUuid.leastSignificantBits)
        }.array()
        val hmac = Crypto.hmacSha256(writeKey, header + payload)
        DataOutputStream(output).apply {
            write(header)
            write(payload)
            write(hmac)
            flush()
        }
    }

    fun read(input: InputStream): Envelope {
        val data = DataInputStream(input)
        val header = ByteArray(28)
        data.readFully(header)
        val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4).also(buffer::get)
        if (!magic.contentEquals(MAGIC)) throw ProtocolException("Invalid frame magic")
        val version = buffer.short.toInt() and 0xffff
        if (version != PROTOCOL_VERSION) throw ProtocolException("Unsupported protocol version $version")
        val channel = buffer.get().toInt() and 0xff
        if (channel != ChannelType.CONTROL.ordinal) throw ProtocolException("Unexpected channel $channel")
        buffer.get() // flags
        val length = buffer.int
        if (length !in 1..MAX_CONTROL_PAYLOAD_BYTES) throw ProtocolException("Invalid payload length $length")
        val uuid = UUID(buffer.long, buffer.long)
        val payload = ByteArray(length)
        data.readFully(payload)
        val receivedHmac = ByteArray(32)
        data.readFully(receivedHmac)
        val expectedHmac = Crypto.hmacSha256(readKey, header + payload)
        if (!Crypto.constantTimeEquals(
                expectedHmac,
                receivedHmac
            )
        ) throw ProtocolException("Frame authentication failed")
        val envelope = try {
            ProtocolJson.decodeFromString<Envelope>(payload.decodeToString())
        } catch (e: Exception) {
            throw ProtocolException("Invalid frame JSON", e)
        }
        if (envelope.messageId != uuid.toString()) throw ProtocolException("Message ID mismatch")
        if (envelope.protocolVersion != version) throw ProtocolException("Envelope protocol mismatch")
        if (expectedRoomId != null && envelope.roomId != expectedRoomId) throw ProtocolException("Wrong room")
        return envelope
    }

    companion object {
        private val MAGIC = byteArrayOf('U'.code.toByte(), 'N'.code.toByte(), 'S'.code.toByte(), 'N'.code.toByte())
    }
}
