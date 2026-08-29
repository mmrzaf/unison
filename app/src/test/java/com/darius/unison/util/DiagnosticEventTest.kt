package com.darius.unison.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticEventTest {
    @Test
    fun jsonLineUsesStableStructuredFieldsAndEscapesValues() {
        val event =
            DiagnosticEvent(
                sequence = 7,
                timestamp = "2026-08-07T10:00:00Z",
                observedTimestamp = "2026-08-07T10:00:00Z",
                monotonicTimeNs = 123,
                severity = DiagnosticSeverity.WARN,
                eventName = "room.test_event",
                body = "hello \"room\"\nnext",
                component = "RoomTest",
                category = DiagnosticCategory.ROOM,
                attributes = mapOf("peer.id" to "abc", "retry.count" to 2, "ok" to true),
                error = DiagnosticError("java.lang.IllegalStateException", "failed"),
            )

        val json = event.toJsonLine()

        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"schemaVersion\":1"))
        assertTrue(json.contains("\"severityText\":\"WARN\""))
        assertTrue(json.contains("\"severityNumber\":13"))
        assertTrue(json.contains("\"eventName\":\"room.test_event\""))
        assertTrue(json.contains("\"service.name\":\"unison\""))
        assertTrue(json.contains("\"log.category\":\"room\""))
        assertTrue(json.contains("hello \\\"room\\\"\\nnext"))
        assertFalse(json.contains("\nnext"))
    }

    @Test
    fun severityNumbersFollowOpenTelemetryRanges() {
        assertEquals(5, DiagnosticSeverity.DEBUG.severityNumber)
        assertEquals(9, DiagnosticSeverity.INFO.severityNumber)
        assertEquals(13, DiagnosticSeverity.WARN.severityNumber)
        assertEquals(17, DiagnosticSeverity.ERROR.severityNumber)
    }
    @Test
    fun logcatProjectionStaysBoundedAndPreservesAnalyzerCriticalAttributes() {
        val event =
            DiagnosticEvent(
                sequence = 9,
                timestamp = "2026-08-07T10:00:00Z",
                observedTimestamp = "2026-08-07T10:00:00Z",
                monotonicTimeNs = 999,
                severity = DiagnosticSeverity.INFO,
                eventName = "sync.sample",
                body = "x".repeat(4_096),
                component = "SynchronizationDiagnostics",
                category = DiagnosticCategory.SYNC,
                attributes =
                    buildMap {
                        repeat(32) { index -> put("extra.field_$index", "v".repeat(768)) }
                        put("room.session_id", "session-1")
                        put("sync.filtered_drift_ms", 42.5)
                        put("sync.action", "set_speed")
                    },
                error = DiagnosticError("java.lang.IllegalStateException", "e".repeat(1_024)),
            )

        val json = event.toLogcatJsonLine()

        assertTrue(json.length <= 3_500)
        assertTrue(json.startsWith("{"))
        assertTrue(json.endsWith("}"))
        assertTrue(json.contains("\"sync.filtered_drift_ms\":42.5"))
        assertTrue(json.contains("\"sync.action\":\"set_speed\""))
        assertTrue(json.contains("\"room.session_id\":\"session-1\""))
        assertFalse(json.contains("x".repeat(4_096)))
    }

    @Test
    fun logcatProjectionPreservesTransferCausalityAttributes() {
        val event =
            DiagnosticEvent(
                sequence = 10,
                timestamp = "2026-08-29T10:00:00Z",
                observedTimestamp = "2026-08-29T10:00:00Z",
                monotonicTimeNs = 1_000,
                severity = DiagnosticSeverity.WARN,
                eventName = "transfer.track.failed",
                body = "x".repeat(4_096),
                component = "TransferManager",
                category = DiagnosticCategory.TRANSFER,
                attributes =
                    buildMap {
                        repeat(32) { index -> put("extra.transfer_$index", "v".repeat(768)) }
                        put("track.id", "abcdef123456")
                        put("peer.id", "peer12345678")
                        put("transfer.operation_id", "operation-123")
                        put("transfer.assignment_id", "assignment-123")
                        put("transfer.phase", "HANDSHAKE")
                    },
                error = DiagnosticError("java.net.SocketException", "Socket closed"),
            )

        val json = event.toLogcatJsonLine()

        assertTrue(json.length <= 3_500)
        assertTrue(json.contains("\"transfer.operation_id\":\"operation-123\""))
        assertTrue(json.contains("\"transfer.assignment_id\":\"assignment-123\""))
        assertTrue(json.contains("\"transfer.phase\":\"HANDSHAKE\""))
        assertTrue(json.contains("\"track.id\":\"abcdef123456\""))
    }

    @Test
    fun `optional null fields are omitted from ndjson`() {
        val json =
            DiagnosticEvent(
                sequence = 2,
                timestamp = "2026-08-07T10:00:00Z",
                observedTimestamp = "2026-08-07T10:00:00Z",
                monotonicTimeNs = 2L,
                severity = DiagnosticSeverity.INFO,
                eventName = "room.idle",
                body = null,
                component = "RoomRuntime",
                category = DiagnosticCategory.ROOM,
                attributes = emptyMap(),
                error = DiagnosticError("Example", null),
            ).toJsonLine()

        assertFalse(json.contains("\"body\":null"))
        assertFalse(json.contains("\"message\":null"))
    }

}
