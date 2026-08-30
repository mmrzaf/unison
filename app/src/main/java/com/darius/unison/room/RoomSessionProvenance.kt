package com.darius.unison.room

/**
 * Immutable identity of the room session under which asynchronous work became valid.
 *
 * This is process-local provenance, not protocol or persisted state. Async work that can mutate the
 * current room should carry this identity until the authoritative room actor can prove it still
 * belongs to the active room/session.
 */
internal data class RoomSessionProvenance(
    val roomId: String,
    val generation: Long,
)
