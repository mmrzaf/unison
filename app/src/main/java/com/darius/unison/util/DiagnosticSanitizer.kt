package com.darius.unison.util

/** Redacts credentials before data reaches either Logcat or persistent diagnostics. */
internal object DiagnosticSanitizer {
    const val MAX_MESSAGE_CHARS = 8_192
    private val secretAssignment = Regex("(?i)(secret|token|passphrase|pin)=\\S+")
    private val passwordAssignment = Regex("(?i)(password)(:|=)\\s*\\S+")
    private val bearerCredential = Regex("(?i)(authorization\\s*:\\s*bearer)\\s+\\S+")

    fun sanitize(value: String): String =
        secretAssignment
            .replace(value, "$1=<redacted>")
            .let { passwordAssignment.replace(it, "$1$2 <redacted>") }
            .let { bearerCredential.replace(it, "$1 <redacted>") }
            .take(MAX_MESSAGE_CHARS)

    fun throwableSummary(throwable: Throwable): String = buildString {
        append(throwable::class.simpleName ?: "Throwable")
        sanitize(throwable.message.orEmpty()).takeIf(String::isNotBlank)?.let {
            append(": ").append(it)
        }
    }
}
