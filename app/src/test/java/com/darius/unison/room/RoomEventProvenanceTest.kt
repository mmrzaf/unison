package com.darius.unison.room

import com.darius.unison.model.AppCommand
import com.darius.unison.model.PeerId
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.TrackId
import com.darius.unison.protocol.TransferFailureBlame
import com.darius.unison.protocol.TransferFailureCode
import com.darius.unison.protocol.TransferFailureStage
import com.darius.unison.transfer.TransferFailure
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Test

class RoomEventProvenanceTest {
    @Test
    fun heartbeatCarriesGenerationAndRequiresSessionProvenance() {
        val event = RoomEvent.HeartbeatTick(generation = 42L)

        assertEquals(42L, event.generation)
        assertEquals(RoomEventProvenanceRequirement.SESSION, event.provenanceRequirement)
    }

    @Test
    fun deviceGlobalIngressIsExplicitlyClassified() {
        val event = RoomEvent.LocalAddressChanged(address = "192.168.1.10")

        assertEquals(RoomEventProvenanceRequirement.DEVICE_GLOBAL, event.provenanceRequirement)
    }

    @Test
    fun orderedLocalCommandIngressIsActorLocal() {
        val event =
            RoomEvent.AppCommandReceived(
                command = AppCommand.LeaveRoom,
                completion = CompletableDeferred(),
            )

        assertEquals(RoomEventProvenanceRequirement.ACTOR_LOCAL, event.provenanceRequirement)
    }

    @Test
    fun transferCompletionCarriesSessionGeneration() {
        val descriptor = TrackDescriptor(trackId = TrackId("a".repeat(64)), sizeBytes = 42L)
        val event = RoomEvent.TransferCompleted(generation = 9L, descriptor = descriptor)

        assertEquals(9L, event.generation)
        assertEquals(RoomEventProvenanceRequirement.SESSION, event.provenanceRequirement)
    }

    @Test
    fun transferFailureCarriesSessionGeneration() {
        val failure =
            TransferFailure(
                trackId = TrackId("b".repeat(64)),
                sourcePeerId = PeerId("peer"),
                stage = TransferFailureStage.BODY,
                code = TransferFailureCode.IO,
                blame = TransferFailureBlame.UNKNOWN,
                retryable = true,
                message = "test",
            )
        val event = RoomEvent.TransferFailed(generation = 11L, failure = failure)

        assertEquals(11L, event.generation)
        assertEquals(RoomEventProvenanceRequirement.SESSION, event.provenanceRequirement)
    }
}
