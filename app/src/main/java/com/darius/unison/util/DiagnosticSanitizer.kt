package com.darius.unison.util

/** Redacts credentials and sensitive local identifiers before diagnostics leave call sites. */
internal object DiagnosticSanitizer {
    const val MAX_MESSAGE_CHARS = 4_096
    private const val MAX_ATTRIBUTE_CHARS = 768

    private val secretAssignment =
        Regex(
            "(?i)(secret|token|passphrase|pin|password|credential|authorization)(\\s*[:=]\\s*)([^\\s,;]+)"
        )
    private val bearerCredential = Regex("(?i)(bearer)(\\s+)([^\\s,;]+)")
    private val contentUri = Regex("content://[^\\s,;]+")
    private val privatePath = Regex("/(?:data|storage|sdcard)/[^\\s,;]+")

    fun sanitize(value: String): String =
        secretAssignment
            .replace(value) { match -> "${match.groupValues[1]}${match.groupValues[2]}<redacted>" }
            .let { bearerCredential.replace(it, "$1 <redacted>") }
            .let { contentUri.replace(it, "content://<redacted>") }
            .let { privatePath.replace(it, "/<redacted-path>") }
            .take(MAX_MESSAGE_CHARS)

    fun sanitizeAttribute(value: String): String = sanitize(value).take(MAX_ATTRIBUTE_CHARS)
}
