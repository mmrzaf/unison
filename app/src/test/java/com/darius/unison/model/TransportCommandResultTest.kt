package com.darius.unison.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TransportCommandResultTest {
    @Test
    fun activePhasesHaveNoTerminalResult() {
        TransportCommandPhase.entries
            .filterNot { it.isTerminal }
            .forEach { phase ->
                assertNull(
                    TransportCommandStatus(
                            commandId = "command",
                            action = TransportAction.PLAY,
                            phase = phase,
                        )
                        .resultOrNull()
                )
            }
    }

    @Test
    fun terminalPhasesMapToTypedResults() {
        val settled =
            TransportCommandStatus(
                    commandId = "play",
                    action = TransportAction.PLAY,
                    phase = TransportCommandPhase.SETTLED,
                    queueItemId = QueueItemId("item"),
                )
                .resultOrNull()
        val superseded =
            TransportCommandStatus(
                    commandId = "seek",
                    action = TransportAction.SEEK,
                    phase = TransportCommandPhase.SUPERSEDED,
                    message = "newer seek",
                )
                .resultOrNull()
        val rejected =
            TransportCommandStatus(
                    commandId = "next",
                    action = TransportAction.NEXT,
                    phase = TransportCommandPhase.REJECTED,
                    message = "unavailable",
                )
                .resultOrNull()

        assertEquals(QueueItemId("item"), (settled as TransportCommandResult.Settled).queueItemId)
        assertEquals("newer seek", (superseded as TransportCommandResult.Superseded).message)
        assertEquals("unavailable", (rejected as TransportCommandResult.Rejected).message)
    }
}
