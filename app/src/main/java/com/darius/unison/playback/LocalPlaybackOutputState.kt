package com.darius.unison.playback

import com.darius.unison.model.LocalPlaybackInhibitionReason
import com.darius.unison.model.LocalPlaybackParticipation

/**
 * Device-local output-safety state, independent from canonical room playback intent.
 *
 * Media3 exposes permanent/local interruptions (for example becoming-noisy or a permanent audio
 * focus loss) separately from temporary platform suppression. Those signals must not be collapsed:
 * only a transient audio-focus suppression is eligible for automatic rejoin, while a local
 * interruption always requires an explicit user rejoin. The platform-suppression latch reflects
 * current Media3 state only and therefore cannot leak across room sessions after suppression has
 * already cleared.
 */
internal class LocalPlaybackOutputState {
    data class Snapshot(
        val participation: LocalPlaybackParticipation,
        val inhibitionReason: LocalPlaybackInhibitionReason?,
        val outputResumeBlocked: Boolean,
        val automaticRejoinAllowed: Boolean,
    )

    var participation: LocalPlaybackParticipation = LocalPlaybackParticipation.ACTIVE
        private set

    var inhibitionReason: LocalPlaybackInhibitionReason? = null
        private set

    /** True only while Media3 currently reports an output-suppressing platform condition. */
    var outputResumeBlocked: Boolean = false
        private set

    /**
     * True only after a resumable transient audio-focus suppression in the current room session.
     * This intentionally survives suppression clear until rejoin succeeds or a newer local
     * interruption/session boundary invalidates it.
     */
    var automaticRejoinAllowed: Boolean = false
        private set

    private var manualResumeReason: LocalPlaybackInhibitionReason? = null

    fun snapshot(): Snapshot =
        Snapshot(
            participation = participation,
            inhibitionReason = inhibitionReason,
            outputResumeBlocked = outputResumeBlocked,
            automaticRejoinAllowed = automaticRejoinAllowed,
        )

    /** Records a local interruption that must never auto-resume. */
    fun applyLocalInterruption(reason: LocalPlaybackInhibitionReason) {
        manualResumeReason = reason
        automaticRejoinAllowed = false
        participation = LocalPlaybackParticipation.OUTPUT_INHIBITED
        inhibitionReason = reason
    }

    /** Records a currently active Media3 platform suppression. */
    fun applyPlatformSuppression(reason: LocalPlaybackInhibitionReason) {
        outputResumeBlocked = true
        participation = LocalPlaybackParticipation.OUTPUT_INHIBITED
        if (reason != LocalPlaybackInhibitionReason.AUDIO_FOCUS && manualResumeReason == null) {
            // Unsafe-output suppression requires an explicit user decision even if a later
            // transient focus suppression overlaps it before Media3 reports NONE.
            manualResumeReason = reason
        }
        val manualReason = manualResumeReason
        if (manualReason != null) {
            // A headphone disconnect/permanent focus loss/unsafe route remains authoritative even
            // if a later transient focus suppression temporarily overlaps it.
            inhibitionReason = manualReason
            automaticRejoinAllowed = false
        } else {
            inhibitionReason = reason
            automaticRejoinAllowed = true
        }
    }

    /** Clears only the live platform latch; local/manual interruption state remains intact. */
    fun clearPlatformSuppression() {
        outputResumeBlocked = false
        manualResumeReason?.let { reason ->
            participation = LocalPlaybackParticipation.OUTPUT_INHIBITED
            inhibitionReason = reason
            automaticRejoinAllowed = false
        }
    }

    /** Called only after an explicit/manual or eligible automatic rejoin succeeds. */
    fun markRejoined() {
        participation = LocalPlaybackParticipation.ACTIVE
        inhibitionReason = null
        outputResumeBlocked = false
        automaticRejoinAllowed = false
        manualResumeReason = null
    }

    /**
     * Session-local resume intent never crosses a room boundary. A suppression that is still active
     * remains output-unsafe, but it does not carry automatic-resume intent into the new room.
     */
    fun resetForSessionBoundary(activeSuppression: LocalPlaybackInhibitionReason?) {
        manualResumeReason = null
        automaticRejoinAllowed = false
        outputResumeBlocked = activeSuppression != null
        if (activeSuppression != null) {
            participation = LocalPlaybackParticipation.OUTPUT_INHIBITED
            inhibitionReason = activeSuppression
        } else {
            participation = LocalPlaybackParticipation.ACTIVE
            inhibitionReason = null
        }
    }
}
