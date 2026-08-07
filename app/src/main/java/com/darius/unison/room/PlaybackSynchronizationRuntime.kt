package com.darius.unison.room

import com.darius.unison.playback.PlaybackSpeedCommandGate
import com.darius.unison.sync.PlaybackSyncController
import com.darius.unison.sync.PlaybackSyncDecision
import com.darius.unison.sync.PlaybackSyncInput
import com.darius.unison.sync.PlaybackSyncProfile
import com.darius.unison.sync.PlaybackSyncState
import com.darius.unison.sync.PlaybackSyncTuning
import com.darius.unison.sync.tuning

/**
 * Local synchronization subsystem. Measurement, correction and player-actuation bounds share one
 * immutable tuning object, and profile changes perform a clean reacquisition instead of mutating a
 * live feedback loop in place.
 */
internal class PlaybackSynchronizationRuntime(
    initialProfile: PlaybackSyncProfile = PlaybackSyncProfile.BALANCED,
) {
    private val controller = PlaybackSyncController(initialProfile.tuning())
    private val speedGate = PlaybackSpeedCommandGate(initialProfile.tuning())

    var profile: PlaybackSyncProfile = initialProfile
        private set

    val tuning: PlaybackSyncTuning
        get() = controller.tuning

    val state: PlaybackSyncState
        get() = controller.state

    fun evaluate(input: PlaybackSyncInput): PlaybackSyncDecision = controller.evaluate(input)

    fun holdForFutureCommand(): PlaybackSyncDecision = controller.holdForFutureCommand()

    fun selectSpeed(requestedSpeed: Float, actualSpeed: Float, nowNs: Long): Float? =
        speedGate.select(requestedSpeed, actualSpeed, nowNs)

    fun reset(preserveLearnedBaseline: Boolean = true) {
        controller.reset(preserveLearnedBaseline)
        speedGate.reset()
    }

    /** Returns true only when a different profile was installed. */
    fun updateProfile(value: PlaybackSyncProfile): Boolean {
        if (value == profile) return false
        profile = value
        val tuning = value.tuning()
        controller.updateTuning(tuning)
        speedGate.updateTuning(tuning)
        return true
    }
}
