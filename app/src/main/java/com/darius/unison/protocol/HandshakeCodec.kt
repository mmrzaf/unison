package com.darius.unison.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream

object HandshakeCodec {
    private const val MAGIC = 0x554E5348 // UNSH
    private const val MAX_HANDSHAKE = 64 * 1024

    fun write(output: OutputStream, message: HandshakeMessage) {
        val bytes = ProtocolJson.encodeToString<HandshakeMessage>(message).encodeToByteArray()
        try {
            require(bytes.size <= MAX_HANDSHAKE) { "Handshake too large" }
            DataOutputStream(output).apply {
                writeInt(MAGIC)
                writeInt(bytes.size)
                write(bytes)
                flush()
            }
        } finally {
            bytes.fill(0)
        }
    }

    fun read(input: InputStream): HandshakeMessage {
        val data = DataInputStream(input)
        val magic =
            try {
                data.readInt()
            } catch (e: EOFException) {
                throw ProtocolException("Handshake closed", e)
            }
        if (magic != MAGIC) throw ProtocolException("Invalid handshake magic")
        val length = data.readInt()
        if (length !in 1..MAX_HANDSHAKE) throw ProtocolException("Invalid handshake size: $length")
        val bytes = ByteArray(length)
        data.readFully(bytes)
        return try {
            ProtocolJson.decodeFromString<HandshakeMessage>(bytes.decodeToString())
        } catch (e: Exception) {
            throw ProtocolException("Invalid handshake JSON", e)
        } finally {
            bytes.fill(0)
        }
    }
}
