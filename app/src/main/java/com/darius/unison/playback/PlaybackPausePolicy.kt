package com.darius.unison.playback

/** Prevents stale watchdog reconciliation from interrupting a healthy natural item transition. */
object PlaybackPausePolicy {
    fun shouldApply(
        cause: PlaybackPauseCause,
        playWhenReady: Boolean,
        lastNaturalTransitionNs: Long,
        nowNs: Long,
    ): Boolean {
        if (cause != PlaybackPauseCause.WATCHDOG_RECONCILIATION) return true
        if (!playWhenReady || lastNaturalTransitionNs == Long.MIN_VALUE) return true
        return nowNs - lastNaturalTransitionNs > NATURAL_TRANSITION_GUARD_NS
    }

    const val NATURAL_TRANSITION_GUARD_NS = 1_500_000_000L
}
