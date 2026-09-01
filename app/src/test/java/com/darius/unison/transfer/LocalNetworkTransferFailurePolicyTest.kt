package com.darius.unison.transfer

import com.darius.unison.network.LocalNetworkRouteException
import com.darius.unison.network.LocalNetworkRouteFailureReason
import com.darius.unison.protocol.TransferFailureBlame
import com.darius.unison.protocol.TransferFailureCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTransferFailurePolicyTest {
    @Test
    fun vpnPolicyDenialIsTerminalDestinationFailure() {
        val decision =
            LocalNetworkTransferFailurePolicy.classify(
                routeFailure(LocalNetworkRouteFailureReason.POLICY_BLOCKED)
            )

        assertEquals(TransferFailureCode.CONNECT_FAILED, decision.code)
        assertEquals(TransferFailureBlame.DESTINATION, decision.blame)
        assertFalse(decision.retryable)
    }

    @Test
    fun accessDenialIsTerminalDestinationFailure() {
        val decision =
            LocalNetworkTransferFailurePolicy.classify(
                routeFailure(LocalNetworkRouteFailureReason.ACCESS_DENIED)
            )

        assertEquals(TransferFailureBlame.DESTINATION, decision.blame)
        assertFalse(decision.retryable)
    }

    @Test
    fun networkLossRemainsRetryableRouteFailure() {
        val decision =
            LocalNetworkTransferFailurePolicy.classify(
                routeFailure(LocalNetworkRouteFailureReason.NETWORK_LOST)
            )

        assertEquals(TransferFailureBlame.ROUTE, decision.blame)
        assertTrue(decision.retryable)
    }

    @Test
    fun unknownSocketProvisionFailureUsesBoundedRouteRetry() {
        val decision =
            LocalNetworkTransferFailurePolicy.classify(
                routeFailure(LocalNetworkRouteFailureReason.SOCKET_PROVISION_FAILED)
            )

        assertEquals(TransferFailureBlame.ROUTE, decision.blame)
        assertTrue(decision.retryable)
    }

    private fun routeFailure(reason: LocalNetworkRouteFailureReason) =
        LocalNetworkRouteException(
            reason = reason,
            errno = if (reason == LocalNetworkRouteFailureReason.POLICY_BLOCKED) "EPERM" else null,
            errnoCode = null,
            message = reason.name,
        )
}
