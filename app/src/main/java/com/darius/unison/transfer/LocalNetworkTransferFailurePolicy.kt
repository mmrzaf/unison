package com.darius.unison.transfer

import com.darius.unison.network.LocalNetworkRouteException
import com.darius.unison.network.LocalNetworkRouteFailureReason
import com.darius.unison.protocol.TransferFailureBlame
import com.darius.unison.protocol.TransferFailureCode

/**
 * Maps pre-connect Android LAN routing failures onto Protocol 2's existing transfer failure model.
 *
 * Policy/access denials are local to the receiving phone and deterministic until the environment
 * changes, so retrying the same assignment cannot help. Network loss and otherwise-unclassified
 * socket provisioning failures remain route failures; the coordinator's bounded route circuit
 * breaker decides whether and when to try them again.
 */
internal object LocalNetworkTransferFailurePolicy {
    fun classify(error: LocalNetworkRouteException): TransferFailureDisposition =
        when (error.reason) {
            LocalNetworkRouteFailureReason.POLICY_BLOCKED,
            LocalNetworkRouteFailureReason.ACCESS_DENIED ->
                TransferFailureDisposition(
                    code = TransferFailureCode.CONNECT_FAILED,
                    blame = TransferFailureBlame.DESTINATION,
                    retryable = false,
                )

            LocalNetworkRouteFailureReason.NETWORK_LOST,
            LocalNetworkRouteFailureReason.SOCKET_PROVISION_FAILED ->
                TransferFailureDisposition(
                    code = TransferFailureCode.CONNECT_FAILED,
                    blame = TransferFailureBlame.ROUTE,
                    retryable = true,
                )
        }
}

internal data class TransferFailureDisposition(
    val code: TransferFailureCode,
    val blame: TransferFailureBlame,
    val retryable: Boolean,
)
