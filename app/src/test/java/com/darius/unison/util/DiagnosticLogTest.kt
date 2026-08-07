package com.darius.unison.util

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticLogTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun roomSessionScopesEventsAndRedactsSensitiveData() = runBlocking {
        val file = File(temporaryFolder.root, "diagnostics/unison.ndjson")
        val log = DiagnosticLog(file)
        val sessionId = log.beginRoom("raw-room-id", "participant")
        val logger = log.scoped("DiagnosticLogTest", DiagnosticCategory.ROOM)

        logger.warn(
            eventName = "room.test_event",
            body = "pin=1234 failed",
            attributes =
                mapOf(
                    "peer.id" to "peer-123",
                    "auth.password" to "hunter2",
                    "retry.count" to 2,
                ),
            throwable = IllegalStateException("token=abc"),
        )

        val raw = log.readRaw()
        val events = log.snapshot(sessionId)
        log.close()

        assertEquals(1, events.size)
        val event = events.single()
        assertEquals(sessionId, event.roomSessionId)
        assertNotEquals("raw-room-id", event.roomIdHash)
        assertEquals("<redacted>", event.attributes["auth.password"])
        assertEquals(2, event.attributes["retry.count"])
        assertFalse(raw.contains("raw-room-id"))
        assertFalse(raw.contains("1234"))
        assertFalse(raw.contains("hunter2"))
        assertFalse(raw.contains("token=abc"))
        assertTrue(raw.contains("<redacted>"))
        assertTrue(raw.contains("\"severityNumber\":13"))
        assertTrue(raw.endsWith("\n"))
    }

    @Test
    fun invalidEventNameCannotBreakApplicationWork() = runBlocking {
        val log = DiagnosticLog(temporaryFolder.newFile("invalid.ndjson"))
        log.info(
            component = "Test",
            category = DiagnosticCategory.APP,
            eventName = "Not Structured",
        )
        log.readRaw()
        val event = log.snapshot().single()
        log.close()

        assertEquals("diagnostic.invalid_event_name", event.eventName)
        assertEquals("Not Structured", event.attributes["diagnostic.original_event_name"])
    }
    @Test
    fun endingRoomStopsSessionScopeWithoutLosingLaterAppDiagnostics() = runBlocking {
        val log = DiagnosticLog(temporaryFolder.newFile("scope.ndjson"))
        val sessionId = log.beginRoom("room-a", "coordinator")
        val roomLogger = log.scoped("RoomTest", DiagnosticCategory.ROOM)
        val appLogger = log.scoped("AppTest", DiagnosticCategory.APP)

        roomLogger.info("room.scope.before_end")
        log.endRoom(sessionId)
        appLogger.info("app.scope.after_end")
        log.readRaw()

        val all = log.snapshot()
        val room = log.snapshot(sessionId)
        log.close()

        assertEquals(2, all.size)
        assertEquals(1, room.size)
        assertEquals("room.scope.before_end", room.single().eventName)
        assertEquals(null, all.last().roomSessionId)
    }

}
