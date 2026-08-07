package com.darius.unison.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioMimePolicyTest {
    @Test
    fun genericProviderMimeDoesNotOverrideDetectedAudio() {
        val result =
            AudioMimePolicy.resolve(
                providerMimeType = "application/octet-stream",
                metadataMimeType = "audio/mpeg",
                fileName = "song.mp3",
            )
        assertEquals("audio/mpeg", result.mimeType)
        assertEquals(AudioMimePolicy.Source.METADATA, result.source)
    }

    @Test
    fun extensionRecoversMimeWhenProviderAndMetadataAreGeneric() {
        val result =
            AudioMimePolicy.resolve(
                providerMimeType = "application/octet-stream",
                metadataMimeType = null,
                fileName = "recording.M4A",
            )
        assertEquals("audio/mp4", result.mimeType)
        assertEquals(AudioMimePolicy.Source.EXTENSION, result.source)
    }

    @Test
    fun trustedProviderAudioMimeWinsAndAliasesNormalize() {
        val result =
            AudioMimePolicy.resolve(
                providerMimeType = "audio/x-mp3; charset=binary",
                metadataMimeType = "audio/aac",
                fileName = "song.aac",
            )
        assertEquals("audio/mpeg", result.mimeType)
        assertEquals(AudioMimePolicy.Source.PROVIDER, result.source)
    }

    @Test
    fun unknownNonAudioInputDoesNotInventMime() {
        val result =
            AudioMimePolicy.resolve(
                providerMimeType = "application/pdf",
                metadataMimeType = "application/octet-stream",
                fileName = "document.bin",
            )
        assertNull(result.mimeType)
        assertEquals(AudioMimePolicy.Source.UNKNOWN, result.source)
    }
}
