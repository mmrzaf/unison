package com.darius.unison.room

import com.darius.unison.protocol.HandshakeRejectionCode
import com.darius.unison.protocol.ProtocolException
import java.io.EOFException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale

/** Deterministic retry decisions for initial room admission. */
internal object JoinRetryPolicy {
    const val MAX_ATTEMPTS = 4

    data class Decision(
        val retry: Boolean,
        val delayMs: Long = 0,
        val message: String? = null,
    )

    fun decide(error: Throwable, completedAttempts: Int): Decision {
        if (completedAttempts >= MAX_ATTEMPTS) return Decision(retry = false)
        val protocol = error.findProtocolException()
        if (protocol != null) {
            return when (protocol.rejectionCode) {
                HandshakeRejectionCode.RATE_LIMITED ->
                    Decision(
                        retry = true,
                        delayMs = 2_500L + (completedAttempts - 1).coerceAtLeast(0) * 1_000L,
                        message = "The room is busy. Retrying…",
                    )

                HandshakeRejectionCode.ROOM_INACTIVE,
                HandshakeRejectionCode.COORDINATOR_MOVED,
                null -> transientDecision(completedAttempts)

                HandshakeRejectionCode.AUTHENTICATION_FAILED,
                HandshakeRejectionCode.WRONG_ROOM,
                HandshakeRejectionCode.PROTOCOL_MISMATCH,
                HandshakeRejectionCode.IDENTITY_COLLISION,
                HandshakeRejectionCode.INVALID_REQUEST,
                HandshakeRejectionCode.ROOM_FULL -> Decision(retry = false)
            }
        }
        return if (error.isTransientNetworkFailure() || error is JoinAcceptanceTimeoutException) {
            transientDecision(completedAttempts)
        } else {
            Decision(retry = false)
        }
    }

    private fun transientDecision(completedAttempts: Int): Decision =
        Decision(
            retry = true,
            delayMs =
                when (completedAttempts) {
                    1 -> 350L
                    2 -> 900L
                    else -> 1_800L
                },
            message = "Connection interrupted. Retrying…",
        )

    private fun Throwable.isTransientNetworkFailure(): Boolean {
        var current: Throwable? = this
        repeat(8) {
            when (current) {
                is ConnectException,
                is SocketTimeoutException,
                is NoRouteToHostException,
                is UnknownHostException,
                is EOFException -> return true

                is SocketException -> {
                    val text = current.message.orEmpty().lowercase(Locale.ROOT)
                    if (
                        "closed" in text ||
                            "reset" in text ||
                            "broken pipe" in text ||
                            "unreachable" in text ||
                            "network" in text ||
                            "timed out" in text
                    )
                        return true
                }
            }
            current = current?.cause ?: return false
        }
        return false
    }

    private fun Throwable.findProtocolException(): ProtocolException? {
        var current: Throwable? = this
        repeat(8) {
            val candidate = current
            if (candidate is ProtocolException) return candidate
            current = current?.cause ?: return null
        }
        return null
    }
}

internal class JoinAcceptanceTimeoutException : IllegalStateException("Join acceptance timed out")
