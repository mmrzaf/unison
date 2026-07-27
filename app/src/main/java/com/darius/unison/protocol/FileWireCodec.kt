package com.darius.unison.protocol

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream

object FileWireCodec {
    private const val MAGIC = 0x554E5346 // UNSF
    private const val MAX_HEADER = 64 * 1024

    fun writeHeader(output: OutputStream, header: FileResponseHeader) {
        val bytes = ProtocolJson.encodeToString(header).encodeToByteArray()
        require(bytes.size <= MAX_HEADER)
        DataOutputStream(output).apply {
            writeInt(MAGIC)
            writeInt(bytes.size)
            write(bytes)
            flush()
        }
    }

    fun readHeader(input: InputStream): FileResponseHeader {
        val data = DataInputStream(input)
        if (data.readInt() != MAGIC) throw ProtocolException("Invalid file response magic")
        val length = data.readInt()
        if (length !in 1..MAX_HEADER) throw ProtocolException("Invalid file header size")
        val bytes = ByteArray(length)
        data.readFully(bytes)
        return ProtocolJson.decodeFromString(bytes.decodeToString())
    }
}
