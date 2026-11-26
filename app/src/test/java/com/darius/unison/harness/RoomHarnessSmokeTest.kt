package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import com.darius.unison.model.RoomJoinCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RoomHarnessSmokeTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun coordinatorAndGuestConvergeOnOneSnapshot() = runTest {
        val harness =
            RoomHarness(
                scope = backgroundScope,
                dispatcher = StandardTestDispatcher(testScheduler),
                clock = SimClock(testScheduler),
                root = temporaryFolder.newFolder("harness"),
            )
        try {
            val host = harness.device("Host")
            val guest = harness.device("Guest")

            host.runtime.submit(AppCommand.CreateRoom("Test room")).await()
            runCurrent()

            val roomId = host.roomId()
            val pin = host.roomPin()
            assertNotNull("host created a room", roomId)
            assertNotNull("host published a room pin", pin)
            assertEquals(1, host.memberCount())

            guest.runtime
                .submit(AppCommand.JoinRoom(host.discoveredRoom()!!, RoomJoinCredential.Pin(pin!!)))
                .await()
            repeat(20) {
                advanceTimeBy(250)
                runCurrent()
                if (guest.memberCount() == 2) return@repeat
            }

            assertEquals("guest joined", roomId, guest.roomId())
            assertEquals("both phones see the same membership", 2, guest.memberCount())
            assertEquals(2, host.memberCount())

            // S1 (quiet steady state): after settling, room time and heartbeats must not
            // manufacture Media3 work on their own.
            repeat(20) {
                advanceTimeBy(250)
                runCurrent()
            }
            val setQueueCalls = guest.player.setQueueCalls.get()
            val seekCalls = guest.player.seekItemCalls.get()
            val speedCalls = guest.player.speedCalls.get()
            advanceTimeBy(60_000)
            runCurrent()
            assertEquals(
                "no timeline rebuilds while idle",
                setQueueCalls,
                guest.player.setQueueCalls.get(),
            )
            assertEquals("no item seeks while idle", seekCalls, guest.player.seekItemCalls.get())
            assertEquals("no speed churn while idle", speedCalls, guest.player.speedCalls.get())
        } finally {
            harness.close()
        }
    }
}
