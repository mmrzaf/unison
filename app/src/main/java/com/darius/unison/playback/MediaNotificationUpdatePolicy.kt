package com.darius.unison.playback

/**
 * Bounds notification-manager churn while preserving urgent foreground-service starts.
 *
 * Media3 may request several updates for one physical transition (timeline, metadata, buffering,
 * readiness, and play state). Only the first request needs to be posted immediately; a trailing
 * update captures the final stable state.
 */
internal object MediaNotificationUpdatePolicy {
    data class Decision(
        val updateNow: Boolean,
        val delayMs: Long?,
    )

    fun decide(
        nowElapsedMs: Long,
        lastUpdateElapsedMs: Long?,
        minimumIntervalMs: Long,
        urgentForegroundStart: Boolean,
        renderedContentChanged: Boolean = true,
    ): Decision {
        require(nowElapsedMs >= 0)
        require(minimumIntervalMs > 0)
        if (urgentForegroundStart || lastUpdateElapsedMs == null) {
            return Decision(updateNow = true, delayMs = 0)
        }
        if (!renderedContentChanged) return Decision(updateNow = false, delayMs = null)
        val elapsed = (nowElapsedMs - lastUpdateElapsedMs).coerceAtLeast(0)
        return if (elapsed >= minimumIntervalMs) {
            Decision(updateNow = true, delayMs = 0)
        } else {
            Decision(updateNow = false, delayMs = minimumIntervalMs - elapsed)
        }
    }
}
