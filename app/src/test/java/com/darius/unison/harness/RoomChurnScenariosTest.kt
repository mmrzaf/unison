package com.darius.unison.harness

import com.darius.unison.model.AppCommand
import com.darius.unison.model.RoomJoinCredential
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class RoomChurnScenariosTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun leavingWhileTheJoinIsInFlightLeavesNoSession() = runTest {
        withDevices { harness, host, guest ->
            val join =
                guest.runtime.submit(
                    AppCommand.JoinRoom(
                        host.discoveredRoom()!!,
                        RoomJoinCredential.Pin(host.roomPin()!!),
                    )
                )
            guest.runtime.submit(AppCommand.LeaveRoom).await()
            join.await()
            settle(3_000)

            assertNull("the in-flight join did not land", guest.roomId())
            assertEquals("the coordinator does not keep a phantom member", 1, host.memberCount())
            RoomInvariants.assertNoFailedEvents(host, guest)
        }
    }

    @Test
    fun closingTheRuntimeReleasesEveryControlLink() = runTest {
        withDevices { harness, host, guest ->
            guest.runtime
                .submit(
                    AppCommand.JoinRoom(
                        host.discoveredRoom()!!,
                        RoomJoinCredential.Pin(host.roomPin()!!),
                    )
                )
                .await()
            settle(2_000)
            assertEquals(1, harness.hub.liveLinkCount())
            host.runtime.close()
            guest.runtime.close()
            settle(500)

            assertEquals(
                "both control links were closed by their runtime",
                0,
                harness.hub.liveLinkCount(),
            )
        }
    }

    private suspend fun TestScope.withDevices(
        block: suspend TestScope.(RoomHarness, SimDevice, SimDevice) -> Unit
    ) =
        withRoom(temporaryFolder, joined = false) { harness, host, guests ->
            block(harness, host, guests.single())
        }
}
