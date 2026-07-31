package com.darius.unison.protocol

class ProtocolException(
    message: String,
    cause: Throwable? = null,
    val rejectionCode: HandshakeRejectionCode = HandshakeRejectionCode.UNKNOWN,
) : Exception(message, cause)
