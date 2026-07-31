package com.darius.unison.room

import com.darius.unison.protocol.HandshakeRejectionCode
import com.darius.unison.protocol.ProtocolException
import java.net.ConnectException
import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinRetryPolicyTest {
    @Test
    fun transientNetworkFailuresRetryWithinBound() {
        assertTrue(JoinRetryPolicy.decide(ConnectException("refused"), 1).retry)
        assertTrue(JoinRetryPolicy.decide(SocketTimeoutException("timeout"), 3).retry)
        assertFalse(JoinRetryPolicy.decide(ConnectException("refused"), 4).retry)
    }

    @Test
    fun permanentAdmissionFailuresDoNotRetry() {
        listOf(
                HandshakeRejectionCode.AUTHENTICATION_FAILED,
                HandshakeRejectionCode.WRONG_ROOM,
                HandshakeRejectionCode.PROTOCOL_MISMATCH,
                HandshakeRejectionCode.ROOM_FULL,
                HandshakeRejectionCode.INVALID_REQUEST,
            )
            .forEach { code ->
                assertFalse(
                    JoinRetryPolicy.decide(ProtocolException("rejected", rejectionCode = code), 1)
                        .retry
                )
            }
    }

    @Test
    fun coordinatorRestartAndAcceptanceTimeoutRetry() {
        assertTrue(
            JoinRetryPolicy.decide(
                    ProtocolException(
                        "moving",
                        rejectionCode = HandshakeRejectionCode.COORDINATOR_MOVED,
                    ),
                    1,
                )
                .retry
        )
        assertTrue(JoinRetryPolicy.decide(JoinAcceptanceTimeoutException(), 1).retry)
    }
}
