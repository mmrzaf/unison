package com.darius.unison.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomIssueTest {
    @Test
    fun typedIssueBuildsStableContextualDeduplicationKey() {
        val issue =
            RoomIssue(
                code = RoomIssueCode.PLAYBACK_TRACK_UNAVAILABLE,
                message = "Unavailable",
                commandId = "command-1",
                queueItemId = QueueItemId("item-1"),
            )

        assertEquals(
            "PLAYBACK_TRACK_UNAVAILABLE:command:command-1:item:item-1",
            issue.deduplicationKey,
        )
    }

    @Test
    fun internalFailureDeduplicatesEquivalentMessages() {
        val first = RoomIssue.internalFailure(" Could not connect ")
        val second = RoomIssue.internalFailure("could not connect")

        assertEquals(first.deduplicationKey, second.deduplicationKey)
        assertTrue(first.message.isNotBlank())
    }

    @Test
    fun internalFailureKeyIsIndependentOfDeviceLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            assertEquals(
                "internal:internal issue",
                RoomIssue.internalFailure("INTERNAL ISSUE").deduplicationKey,
            )
        } finally {
            Locale.setDefault(original)
        }
    }
}
