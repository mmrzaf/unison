package com.darius.unison.playback

import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaybackOutputStateTest {
    @Test
    fun transientAudioFocusIsAutoRejoinEligibleButBlockedUntilPlatformClear() {
        val state = LocalPlaybackOutputState()

        state.applyPlatformSuppression(LocalPlaybackInhibitionReason.AUDIO_FOCUS)
        assertEquals(LocalPlaybackParticipation.OUTPUT_INHIBITED, state.participation)
        assertEquals(LocalPlaybackInhibitionReason.AUDIO_FOCUS, state.inhibitionReason)
        assertTrue(state.outputResumeBlocked)
        assertTrue(state.automaticRejoinAllowed)

        state.clearPlatformSuppression()
        assertEquals(LocalPlaybackParticipation.OUTPUT_INHIBITED, state.participation)
        assertEquals(LocalPlaybackInhibitionReason.AUDIO_FOCUS, state.inhibitionReason)
        assertFalse(state.outputResumeBlocked)
        assertTrue(state.automaticRejoinAllowed)

        state.markRejoined()
        assertEquals(LocalPlaybackParticipation.ACTIVE, state.participation)
        assertEquals(null, state.inhibitionReason)
        assertFalse(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)
    }

    @Test
    fun permanentAudioFocusLossNeverCreatesAutomaticRejoin() {
        val state = LocalPlaybackOutputState()

        state.applyLocalInterruption(LocalPlaybackInhibitionReason.AUDIO_FOCUS)

        assertEquals(LocalPlaybackParticipation.OUTPUT_INHIBITED, state.participation)
        assertEquals(LocalPlaybackInhibitionReason.AUDIO_FOCUS, state.inhibitionReason)
        assertFalse(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)
    }

    @Test
    fun localInterruptionWinsOverOverlappingTransientSuppression() {
        val state = LocalPlaybackOutputState()
        state.applyLocalInterruption(LocalPlaybackInhibitionReason.BECOMING_NOISY)

        state.applyPlatformSuppression(LocalPlaybackInhibitionReason.AUDIO_FOCUS)
        assertEquals(LocalPlaybackInhibitionReason.BECOMING_NOISY, state.inhibitionReason)
        assertTrue(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)

        state.clearPlatformSuppression()
        assertEquals(LocalPlaybackParticipation.OUTPUT_INHIBITED, state.participation)
        assertEquals(LocalPlaybackInhibitionReason.BECOMING_NOISY, state.inhibitionReason)
        assertFalse(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)
    }

    @Test
    fun unsuitableOutputClearRemainsManualRejoinOnly() {
        val state = LocalPlaybackOutputState()
        state.applyPlatformSuppression(LocalPlaybackInhibitionReason.UNSUITABLE_OUTPUT)

        assertTrue(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)

        state.clearPlatformSuppression()
        assertEquals(LocalPlaybackParticipation.OUTPUT_INHIBITED, state.participation)
        assertEquals(LocalPlaybackInhibitionReason.UNSUITABLE_OUTPUT, state.inhibitionReason)
        assertFalse(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)
    }

    @Test
    fun unsuitableOutputCannotBeOverwrittenIntoAutomaticFocusRejoin() {
        val state = LocalPlaybackOutputState()
        state.applyPlatformSuppression(LocalPlaybackInhibitionReason.UNSUITABLE_OUTPUT)

        state.applyPlatformSuppression(LocalPlaybackInhibitionReason.AUDIO_FOCUS)
        assertEquals(LocalPlaybackInhibitionReason.UNSUITABLE_OUTPUT, state.inhibitionReason)
        assertTrue(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)

        state.clearPlatformSuppression()
        assertEquals(LocalPlaybackInhibitionReason.UNSUITABLE_OUTPUT, state.inhibitionReason)
        assertFalse(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)
    }

    @Test
    fun sessionBoundaryDropsStaleResumeIntentButKeepsLiveSuppressionUnsafe() {
        val state = LocalPlaybackOutputState()
        state.applyPlatformSuppression(LocalPlaybackInhibitionReason.AUDIO_FOCUS)
        state.clearPlatformSuppression()
        assertTrue(state.automaticRejoinAllowed)

        state.resetForSessionBoundary(activeSuppression = null)
        assertEquals(LocalPlaybackParticipation.ACTIVE, state.participation)
        assertEquals(null, state.inhibitionReason)
        assertFalse(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)

        state.resetForSessionBoundary(LocalPlaybackInhibitionReason.AUDIO_FOCUS)
        assertEquals(LocalPlaybackParticipation.OUTPUT_INHIBITED, state.participation)
        assertEquals(LocalPlaybackInhibitionReason.AUDIO_FOCUS, state.inhibitionReason)
        assertTrue(state.outputResumeBlocked)
        assertFalse(state.automaticRejoinAllowed)
    }
}
