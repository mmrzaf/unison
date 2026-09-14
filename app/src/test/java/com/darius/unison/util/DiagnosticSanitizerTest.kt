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
        assertFalse(safe.contains("xyz"))
        assertTrue(safe.contains("<redacted>"))
    }

    @Test
    fun redactsQuotedCredentialsUrisAndLocalNetworkAddresses() {
        val safe =
            DiagnosticSanitizer.sanitize(
                "{\"token\":\"abc\"} content://provider/private file:///data/user/0/file " +
                    "/storage/emulated/0/Music/secret.mp3 192.168.1.22:42100 [fe80::1%wlan0]:42100"
            )

        assertFalse(safe.contains("abc"))
        assertFalse(safe.contains("provider/private"))
        assertFalse(safe.contains("/data/user/0/file"))
        assertFalse(safe.contains("/storage/emulated"))
        assertFalse(safe.contains("192.168.1.22"))
        assertFalse(safe.contains("fe80::1"))
        assertTrue(safe.contains("<redacted>"))
        assertTrue(safe.contains("<redacted-ip>"))
    }

    @Test
    fun redactsCredentialsInsideEscapedJsonText() {
        val safe = DiagnosticSanitizer.sanitize("payload={\\\"token\\\":\\\"nested-secret\\\"}")

        assertFalse(safe.contains("nested-secret"))
        assertTrue(safe.contains("<redacted>"))
    }

    @Test
    fun capsUntrustedMessageLength() {
        val safe =
            DiagnosticSanitizer.sanitize("x".repeat(DiagnosticSanitizer.MAX_MESSAGE_CHARS * 2))

        assertTrue(safe.length <= DiagnosticSanitizer.MAX_MESSAGE_CHARS)
    }
}
