package com.darius.unison.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticSanitizerTest {
    @Test
    fun redactsKnownCredentialShapes() {
        val safe =
            DiagnosticSanitizer.sanitize(
                "pin=123456 token=abc password: hunter2 Authorization: Bearer xyz"
            )

        assertFalse(safe.contains("123456"))
        assertFalse(safe.contains("hunter2"))
        assertFalse(safe.contains("Bearer xyz"))
        assertTrue(safe.contains("<redacted>"))
    }

    @Test
    fun capsUntrustedMessageLength() {
        val safe =
            DiagnosticSanitizer.sanitize("x".repeat(DiagnosticSanitizer.MAX_MESSAGE_CHARS * 2))

        assertTrue(safe.length <= DiagnosticSanitizer.MAX_MESSAGE_CHARS)
    }
}
