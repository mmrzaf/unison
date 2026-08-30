package com.darius.unison.room

/** Small, process-local authority checks used at asynchronous room ingress boundaries. */
internal object RoomIngressAuthority {
    fun acceptsSession(
        provenance: RoomSessionProvenance,
        currentRoomId: String?,
        currentGeneration: Long,
        coordinatorIsAuthoritative: Boolean,
    ): Boolean =
        coordinatorIsAuthoritative &&
            currentRoomId != null &&
            provenance.roomId == currentRoomId &&
            provenance.generation == currentGeneration

    /** Connection authority is intentionally object identity, matching stale-close handling. */
    fun <C : Any> isCurrentConnection(current: C?, source: C): Boolean = current === source
}
