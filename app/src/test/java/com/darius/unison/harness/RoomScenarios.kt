package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import com.darius.unison.model.RoomJoinCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.rules.TemporaryFolder

/**
 * Runs [block] against a coordinator and [guestCount] guests on one virtual LAN and one virtual
 * clock. With [joined] the guests are already members when the block starts.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.withRoom(
    folder: TemporaryFolder,
    guestCount: Int = 1,
    joined: Boolean = true,
    block: suspend TestScope.(RoomHarness, SimDevice, List<SimDevice>) -> Unit,
) {
    val harness =
        RoomHarness(
            scope = backgroundScope,
            dispatcher = StandardTestDispatcher(testScheduler),
            clock = SimClock(testScheduler),
            root = folder.newFolder("harness"),
        )
    try {
        val host = harness.device("Host")
        val guests = (1..guestCount).map { harness.device("Guest $it") }
        host.runtime.submit(AppCommand.CreateRoom("Test room")).await()
        settle(250)
        if (joined) guests.forEach { guest -> joinRoom(guest, host) }
        block(harness, host, guests)
    } finally {
        harness.close()
    }
}

/** Joins [host]'s room with its PIN and waits until the membership has converged. */
suspend fun TestScope.joinRoom(guest: SimDevice, host: SimDevice) {
    guest.runtime
        .submit(
            AppCommand.JoinRoom(host.discoveredRoom()!!, RoomJoinCredential.Pin(host.roomPin()!!))
        )
        .await()
    settle(2_000)
    assertEquals("${guest.index} joined", host.roomId(), guest.roomId())
}
