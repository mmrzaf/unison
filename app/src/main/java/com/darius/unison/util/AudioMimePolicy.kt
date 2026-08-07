package com.darius.unison.util

import java.util.Locale

/** Deterministic audio MIME selection independent of document-provider/OEM quirks. */
object AudioMimePolicy {
    data class Resolution(
        val mimeType: String?,
        val source: Source,
    )

    enum class Source {
        PROVIDER,
        METADATA,
        EXTENSION,
        UNKNOWN,
    }

    fun resolve(
        providerMimeType: String?,
        metadataMimeType: String?,
        fileName: String?,
    ): Resolution {
        trustedAudioMime(providerMimeType)?.let { return Resolution(it, Source.PROVIDER) }
        trustedAudioMime(metadataMimeType)?.let { return Resolution(it, Source.METADATA) }
        extensionMime(fileName)?.let { return Resolution(it, Source.EXTENSION) }
        return Resolution(null, Source.UNKNOWN)
    }

    fun trustedAudioMime(value: String?): String? {
        val normalized = value?.trim()?.lowercase(Locale.ROOT)?.substringBefore(';')?.trim()
            ?.takeIf(String::isNotBlank) ?: return null
        if (normalized in GENERIC_MIME_TYPES) return null
        if (!normalized.startsWith("audio/")) return null
        return ALIASES[normalized] ?: normalized
    }

    fun extensionMime(fileName: String?): String? {
        val extension = fileName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return EXTENSION_MIME_TYPES[extension]
    }

    private val GENERIC_MIME_TYPES =
        setOf(
            "application/octet-stream",
            "binary/octet-stream",
            "application/unknown",
            "application/x-unknown-content-type",
            "*/*",
        )

    private val ALIASES =
        mapOf(
            "audio/mp3" to "audio/mpeg",
            "audio/x-mp3" to "audio/mpeg",
            "audio/x-mpeg" to "audio/mpeg",
            "audio/x-wav" to "audio/wav",
            "audio/wave" to "audio/wav",
            "audio/x-flac" to "audio/flac",
            "audio/x-m4a" to "audio/mp4",
            "audio/m4a" to "audio/mp4",
            "audio/x-aac" to "audio/aac",
        )

    private val EXTENSION_MIME_TYPES =
        mapOf(
            "mp3" to "audio/mpeg",
            "m4a" to "audio/mp4",
            "mp4" to "audio/mp4",
            "aac" to "audio/aac",
            "wav" to "audio/wav",
            "flac" to "audio/flac",
            "ogg" to "audio/ogg",
            "oga" to "audio/ogg",
            "opus" to "audio/opus",
            "amr" to "audio/amr",
            "3gp" to "audio/3gpp",
            "3gpp" to "audio/3gpp",
        )
}
