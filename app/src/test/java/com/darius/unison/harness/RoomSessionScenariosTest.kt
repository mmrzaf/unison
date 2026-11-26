package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import com.darius.unison.model.RoomJoinCredential
import com.darius.unison.model.RoomLifecycleState
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** S2, S5, S12, S13: admission, link health, session boundaries and shutdown. */
@OptIn(ExperimentalCoroutinesApi::class)
class RoomSessionScenariosTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun aWrongPinNeverAdmitsAGuest() = runTest {
        withRoom(temporaryFolder, joined = false) { harness, host, _ ->
            val intruder = harness.device("Intruder")
            intruder.runtime
                .submit(
                    AppCommand.JoinRoom(host.discoveredRoom()!!, RoomJoinCredential.Pin("000000"))
                )
                .await()
            settle(15_000)

            assertEquals("the coordinator admitted nobody", 1, host.memberCount())
            assertNull("the intruder holds no session", intruder.roomId())
            RoomInvariants.assertNoFailedEvents(host, intruder)
        }
    }

    @Test
    fun aRoomAdmitsAtMostEightMembers() = runTest {
        withRoom(temporaryFolder, guestCount = 7) { harness, host, _ ->
            assertEquals(8, host.memberCount())
            val ninth = harness.device("Ninth")
            ninth.runtime
                .submit(
                    AppCommand.JoinRoom(
                        host.discoveredRoom()!!,
                        RoomJoinCredential.Pin(host.roomPin()!!),
                    )
                )
                .await()
            settle(15_000)

            assertEquals("the room is full", 8, host.memberCount())
            assertNull(ninth.roomId())
            RoomInvariants.assertNoFailedEvents(host, ninth)
        }
    }

    @Test
    fun aGuestThatVanishesIsRemovedOnlyAfterTheGraceWindow() = runTest {
        withRoom(temporaryFolder) { _, host, guests ->
            guests.single().runtime.close()
            settle(2_000)
            assertEquals("still a member inside the grace window", 2, host.memberCount())

            settle(15_000)
            assertEquals("removed once the grace window expired", 1, host.memberCount())
            RoomInvariants.assertNoFailedEvents(host)
        }
    }

    @Test
    fun aHalfOpenLinkIsDetectedAndTheRoomReconverges() = runTest {
        withRoom(temporaryFolder) { harness, host, guests ->
            val guest = guests.single()
            harness.hub.stalled = true
            settle(60_000)
            harness.hub.stalled = false
            settle(60_000)

            assertEquals("membership reconverged", 2, host.memberCount())
            assertEquals(host.roomId(), guest.roomId())
            assertEquals(RoomLifecycleState.CONNECTED, guest.store.structure.value.lifecycle)
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    @Test
    fun workStartedInOneRoomNeverLandsInTheNextOne() = runTest {
        withRoom(temporaryFolder, joined = false) { _, host, _ ->
            val tracks = (1..4).map { host.addLocalTrack("old-room-$it") }
            host.runtime.submit(AppCommand.AddTracks(tracks.map { it.trackId }))
            host.runtime.submit(AppCommand.LeaveRoom).await()
            host.runtime.submit(AppCommand.CreateRoom("Second room")).await()
            settle(10_000)

            val snapshot = host.store.structure.value.snapshot
            assertNotNull(snapshot)
            assertEquals("Second room", snapshot!!.roomName)
            assertTrue("the old room's queue never leaks", snapshot.queue.isEmpty())
            RoomInvariants.assertNoFailedEvents(host)
        }
    }

    @Test
    fun shuttingDownAtAnyPointLeavesNothingBehind() = runTest {
        repeat(CUT_POINTS) { attempt ->
            val cutMs = Random(attempt.toLong()).nextLong(0, 12_000)
            val harness =
                RoomHarness(
                    scope = backgroundScope,
                    dispatcher = StandardTestDispatcher(testScheduler),
                    clock = SimClock(testScheduler),
                    root = temporaryFolder.newFolder("cut-$attempt"),
                )
            val host = harness.device("Host")
            val guest = harness.device("Guest")
            host.runtime.submit(AppCommand.CreateRoom("Cut $attempt")).await()
            settle(250)
            joinRoomNoWait(guest, host)
            host.runtime.submit(
                AppCommand.AddTracks(listOf(host.addLocalTrack("cut-$attempt").trackId))
            )
            settle(cutMs)

            host.runtime.close()
            guest.runtime.close()
            settle(3_000)

            assertEquals("attempt $attempt left no control link", 0, harness.hub.liveLinkCount())
            RoomInvariants.assertNoFailedEvents(host, guest)
            harness.close()
        }
    }

    @Test
    fun joiningARoomThatIsNotThereFailsAfterABoundedNumberOfAttempts() = runTest {
        withRoom(temporaryFolder, joined = false) { harness, host, _ ->
            val guest = harness.device("Lost")
            val missing = host.discoveredRoom()!!.copy(port = 9)
            guest.runtime
                .submit(AppCommand.JoinRoom(missing, RoomJoinCredential.Pin(host.roomPin()!!)))
                .await()
            settle(60_000)

            assertNull("no session was created", guest.roomId())
            val attempts = guest.events().count { it.eventName == "room.join.started" }
            assertTrue("join attempts are bounded ($attempts)", attempts in 1..6)
            assertEquals(1, host.memberCount())
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    @Test
    fun aBriefLocalNetworkLossKeepsTheRoomAndALongOneEndsItTruthfully() = runTest {
        withRoom(temporaryFolder) { _, host, guests ->
            val guest = guests.single()
            val roomId = host.roomId()

            host.localNetwork.address = null
            settle(6_000)
            host.localNetwork.address = java.net.InetAddress.getByName(host.address)
            settle(30_000)
            assertEquals("a short outage does not end the room", roomId, host.roomId())
            assertEquals(2, host.memberCount())
            assertEquals(RoomLifecycleState.CONNECTED, host.store.structure.value.lifecycle)

            host.localNetwork.address = null
            settle(60_000)
            assertEquals(
                "a long outage ends the room instead of leaving a zombie",
                RoomLifecycleState.FAILED,
                host.store.structure.value.lifecycle,
            )
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    private suspend fun joinRoomNoWait(guest: SimDevice, host: SimDevice) {
        guest.runtime
            .submit(
                AppCommand.JoinRoom(
                    host.discoveredRoom()!!,
                    RoomJoinCredential.Pin(host.roomPin()!!),
                )
            )
            .await()
    }

    private companion object {
        const val CUT_POINTS = 12
    }
}
