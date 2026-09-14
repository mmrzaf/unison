package com.darius.unison.util

/** Redacts credentials and sensitive local identifiers before diagnostics leave call sites. */
internal object DiagnosticSanitizer {
    const val MAX_MESSAGE_CHARS = 4_096
    private const val MAX_ATTRIBUTE_CHARS = 768

    private val quotedSecretAssignment =
        Regex(
            "(?i)([\"']?(?:secret|token|passphrase|pin|password|credential|authorization)[\"']?\\s*[:=]\\s*)([\"'])([^\"']*)\\2"
        )
    private val escapedQuotedSecretAssignment =
        Regex(
            "(?i)(\\\\[\"'](?:secret|token|passphrase|pin|password|credential|authorization)\\\\[\"']\\s*[:=]\\s*\\\\[\"'])([^\\\\\"']*)(\\\\[\"'])"
        )
    private val secretAssignment =
        Regex(
            "(?i)(secret|token|passphrase|pin|password|credential|authorization)(\\s*[:=]\\s*)([^\\s,;]+)"
        )
    private val bearerCredential = Regex("(?i)(bearer)(\\s+)([^\\s,;]+)")
    private val contentUri = Regex("(?i)content://[^\\s,;]+")
    private val fileUri = Regex("(?i)file://[^\\s,;]+")
    private val privatePath = Regex("/(?:data|storage|sdcard|mnt)/[^\\s,;]+")
    private val ipv4Address = Regex("(?<![\\w.])(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?")
    private val bracketedIpv6Address =
        Regex("\\[[0-9a-fA-F:.]+(?:%[A-Za-z0-9_.-]+)?](?::\\d{1,5})?")

    fun sanitize(value: String): String =
        bearerCredential
            .replace(value, "$1 <redacted>")
            .let {
                escapedQuotedSecretAssignment.replace(it) { match ->
                    "${match.groupValues[1]}<redacted>${match.groupValues[3]}"
                }
            }
            .let {
                quotedSecretAssignment.replace(it) { match ->
                    "${match.groupValues[1]}${match.groupValues[2]}<redacted>${match.groupValues[2]}"
                }
            }
            .let {
                secretAssignment.replace(it) { match ->
                    "${match.groupValues[1]}${match.groupValues[2]}<redacted>"
                }
            }
            .let { contentUri.replace(it, "content://<redacted>") }
            .let { fileUri.replace(it, "file://<redacted>") }
            .let { privatePath.replace(it, "/<redacted-path>") }
            .let { ipv4Address.replace(it, "<redacted-ip>") }
            .let { bracketedIpv6Address.replace(it, "<redacted-ip>") }
            .take(MAX_MESSAGE_CHARS)

    fun sanitizeAttribute(value: String): String = sanitize(value).take(MAX_ATTRIBUTE_CHARS)
}
