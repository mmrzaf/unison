package com.darius.unison.room

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomIngressAuthorityTest {
    @Test
    fun acceptedSessionRequiresRoomGenerationAndCoordinatorAuthority() {
        val provenance = RoomSessionProvenance(roomId = "room-a", generation = 4L)

        assertTrue(
            RoomIngressAuthority.acceptsSession(
                provenance = provenance,
                currentRoomId = "room-a",
                currentGeneration = 4L,
                coordinatorIsAuthoritative = true,
            )
        )
        assertFalse(
            RoomIngressAuthority.acceptsSession(
                provenance = provenance,
                currentRoomId = "room-b",
                currentGeneration = 4L,
                coordinatorIsAuthoritative = true,
            )
        )
        assertFalse(
            RoomIngressAuthority.acceptsSession(
                provenance = provenance,
                currentRoomId = "room-a",
                currentGeneration = 5L,
                coordinatorIsAuthoritative = true,
            )
        )
        assertFalse(
            RoomIngressAuthority.acceptsSession(
                provenance = provenance,
                currentRoomId = "room-a",
                currentGeneration = 4L,
                coordinatorIsAuthoritative = false,
            )
        )
    }

    @Test
    fun connectionAuthorityUsesIdentityNotEquality() {
        data class ValueConnection(val value: String)

        val current = ValueConnection("same")
        val equalButDifferent = ValueConnection("same")

        assertTrue(RoomIngressAuthority.isCurrentConnection(current, current))
        assertFalse(RoomIngressAuthority.isCurrentConnection(current, equalButDifferent))
        assertFalse(RoomIngressAuthority.isCurrentConnection(null, current))
    }
}
