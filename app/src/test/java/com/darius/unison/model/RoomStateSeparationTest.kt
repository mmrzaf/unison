package com.darius.unison.model

import com.darius.unison.app.RoomStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStateSeparationTest {
    private val peer = PeerId("peer-123456789012")

    @Test
    fun playbackTicksDoNotChangeStructuralFlow() {
        val store = RoomStore()
        val structure =
            RoomStructureState(
                lifecycle = RoomLifecycleState.CONNECTED,
                localIdentity = LocalIdentity(peer, "Phone"),
            )
        store.updateStructure { structure }
        val before = store.structure.value

        store.updatePlayback { it.copy(localPositionMs = 1_250L, localIsPlaying = true) }

        assertEquals(before, store.structure.value)
        assertEquals(1_250L, store.playback.value.localPositionMs)
        assertEquals(1_250L, store.currentState().localPlaybackPositionMs)
    }

    @Test
    fun memberPlaybackIsRemovedFromStructuralSnapshot() {
        val snapshot =
            RoomSnapshot(
                roomId = "room-1234",
                roomName = "Room",
                term = CoordinatorTerm(1, peer),
                sequence = 1,
                members =
                    listOf(MemberSnapshot(peer, "Phone", playbackPositionMs = 99L, driftMs = 7L)),
            )
        val store = RoomStore()
        store.set(RoomUiState(snapshot = snapshot))

        val structuralMember = store.structure.value.snapshot!!.members.single()
        assertNull(structuralMember.playbackPositionMs)
        assertNull(structuralMember.driftMs)
        assertEquals(99L, store.playback.value.memberPlayback[peer]?.positionMs)
    }

    @Test
    fun unknownLocalPositionIsNotRepresentedAsZeroInTelemetry() {
        val store = RoomStore()
        assertNull(store.playback.value.localPositionMs)
        assertEquals(0L, store.currentState().localPlaybackPositionMs)
    }

    @Test
    fun transferUpdatesDoNotChangeStructureOrPlayback() {
        val store = RoomStore()
        val beforeStructure = store.structure.value
        val beforePlayback = store.playback.value
        val trackId = TrackId("a".repeat(64))
        store.updateTransfers {
            it.copy(
                transfers =
                    mapOf(
                        trackId to
                            TransferProgress(
                                trackId = trackId,
                                bytesTransferred = 5,
                                totalBytes = 10,
                                sourcePeerId = peer,
                                destinationPeerId = null,
                                state = MemberTrackState.RECEIVING,
                            )
                    )
            )
        }
        assertEquals(beforeStructure, store.structure.value)
        assertEquals(beforePlayback, store.playback.value)
        assertTrue(store.transfers.value.transfers.containsKey(trackId))
    }
}
