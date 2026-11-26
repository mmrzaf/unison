package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import com.darius.unison.model.RoomIssueCode
import com.darius.unison.model.RoomLifecycleState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RoomLifecycleScenariosTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun leavingAsParticipantLeavesCanonicalMembership() = runTest {
        withHarness { harness, host, guest ->
            guest.runtime.submit(AppCommand.LeaveRoom).await()
            settle()

            val hostEvents = host.log.snapshot().map { it.eventName }
            val guestEvents = guest.log.snapshot().map { it.eventName }
            assertEquals(
                "participant is gone; host=$hostEvents guest=$guestEvents seq=" +
                    host.store.structure.value.snapshot?.sequence,
                1,
                host.memberCount(),
            )
            assertEquals(
                "participant returns to idle",
                RoomLifecycleState.IDLE,
                guest.store.structure.value.lifecycle,
            )
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    @Test
    fun controlLossReconnectsWithinTheGraceWindow() = runTest {
        withHarness { harness, host, guest ->
            val roomId = host.roomId()
            harness.hub.partitionAll()
            settle(1_000)
            assertNotNull("guest still owns the session while recovering", guest.roomId())
            settle(10_000)

            assertEquals("guest reconnected to the same room", roomId, guest.roomId())
            assertEquals("membership reconverged", 2, host.memberCount())
            assertEquals(2, guest.memberCount())
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    @Test
    fun coordinatorLeaveEndsTheRoomImmediately() = runTest {
        withHarness { harness, host, guest ->
            host.runtime.submit(AppCommand.LeaveRoom).await()
            settle(2_000)

            assertEquals(
                "participants end with the room",
                RoomLifecycleState.FAILED,
                guest.store.structure.value.lifecycle,
            )
            assertEquals(
                RoomIssueCode.ROOM_ENDED,
                guest.store.structure.value.issue?.code,
            )
            assertNull("no phantom snapshot remains", guest.store.structure.value.snapshot)
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    @Test
    fun permanentCoordinatorLossEndsTheRoom() = runTest {
        withHarness { harness, host, guest ->
            host.runtime.close()
            settle(90_000)

            assertEquals(
                "the guest does not keep a zombie room",
                RoomLifecycleState.FAILED,
                guest.store.structure.value.lifecycle,
            )
            assertEquals(
                "the end is truthful",
                RoomIssueCode.ROOM_ENDED,
                guest.store.structure.value.issue?.code,
            )
            assertNull("no phantom snapshot remains", guest.store.structure.value.snapshot)
            RoomInvariants.assertNoFailedEvents(guest)
        }
    }

    private suspend fun TestScope.withHarness(
        block: suspend TestScope.(RoomHarness, SimDevice, SimDevice) -> Unit
    ) =
        withRoom(temporaryFolder, joined = true) { harness, host, guests ->
            block(harness, host, guests.single())
        }
}
