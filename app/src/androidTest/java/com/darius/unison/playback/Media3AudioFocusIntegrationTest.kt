package com.darius.unison.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A phone call or another app taking transient audio focus must be one continuous suppression:
 * output stays blocked for the whole hold, and the automatic-rejoin edge appears only when the
 * platform actually releases focus.
 */
@RunWith(AndroidJUnit4::class)
class Media3AudioFocusIntegrationTest {
    @Test
    fun transientFocusHoldStaysSuppressedUntilTheRealRelease() = runBlocking {
        withMedia3Adapter { fixture ->
            val item = fixture.item("focus", durationMs = 60_000)
            fixture.adapter.setQueue(listOf(item), item.queueItemId, 0)
            assertTrue(fixture.adapter.play())
            fixture.adapter.state.first { it.isPlaying }

            val audioManager =
                ApplicationProvider.getApplicationContext<Context>()
                    .getSystemService(AudioManager::class.java)
            val request =
                AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener {}
                    .build()
            assertEquals(
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                audioManager.requestAudioFocus(request),
            )
            try {
                val suppressed =
                    withTimeout(FOCUS_TIMEOUT_MS) {
                        fixture.adapter.state.first {
                            it.participation == LocalPlaybackParticipation.OUTPUT_INHIBITED
                        }
                    }
                assertEquals(LocalPlaybackInhibitionReason.AUDIO_FOCUS, suppressed.inhibitionReason)
                assertTrue(suppressed.outputResumeBlocked)

                // The hold is still in force: nothing may report the suppression as cleared or
                // let a rejoin start while the other app still owns the audio.
                repeat(HOLD_SAMPLES) {
                    delay(HOLD_SAMPLE_INTERVAL_MS)
                    val during = fixture.adapter.state.value
                    assertEquals(
                        LocalPlaybackParticipation.OUTPUT_INHIBITED,
                        during.participation,
                    )
                    assertTrue("suppression cleared during the hold", during.outputResumeBlocked)
                    assertFalse(during.isPlaying)
                }
            } finally {
                audioManager.abandonAudioFocusRequest(request)
            }

            val released =
                withTimeout(FOCUS_TIMEOUT_MS) {
                    fixture.adapter.state.first {
                        it.participation == LocalPlaybackParticipation.OUTPUT_INHIBITED &&
                            !it.outputResumeBlocked
                    }
                }
            assertTrue(
                "a transient hold is eligible for automatic rejoin",
                released.automaticRejoinAllowed,
            )
            assertEquals(LocalPlaybackInhibitionReason.AUDIO_FOCUS, released.inhibitionReason)
        }
    }

    private companion object {
        const val FOCUS_TIMEOUT_MS = 8_000L
        const val HOLD_SAMPLES = 6
        const val HOLD_SAMPLE_INTERVAL_MS = 250L
    }
}
