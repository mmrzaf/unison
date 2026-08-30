package com.darius.unison.transfer

/**
 * One authoritative transfer-capacity model shared by coordinator admission and transport guards.
 *
 * Capacity is directional. A destination may receive from multiple independent sources, a source may
 * serve multiple destinations, but one source/destination pair is intentionally serialized. The
 * transport semaphores remain defensive guards; normal scheduling must respect these limits before a
 * socket is opened.
 */
data class TransferCapacityPolicy(
    val maxInboundPerDestination: Int = 2,
    val maxOutboundPerSource: Int = 3,
    val maxPerSourceDestinationPair: Int = 1,
) {
    init {
        require(maxInboundPerDestination > 0) { "Inbound transfer capacity must be positive" }
        require(maxOutboundPerSource > 0) { "Outbound transfer capacity must be positive" }
        require(maxPerSourceDestinationPair > 0) { "Pair transfer capacity must be positive" }
        require(maxPerSourceDestinationPair <= maxInboundPerDestination) {
            "Pair capacity cannot exceed destination capacity"
        }
        require(maxPerSourceDestinationPair <= maxOutboundPerSource) {
            "Pair capacity cannot exceed source capacity"
        }
    }

    companion object {
        val DEFAULT = TransferCapacityPolicy()
    }
}
