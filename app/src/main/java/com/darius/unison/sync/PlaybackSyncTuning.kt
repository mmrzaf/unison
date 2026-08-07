package com.darius.unison.sync

/** User-facing local synchronization behavior. It never changes room protocol or other devices. */
enum class PlaybackSyncProfile {
    TIGHT,
    BALANCED,
    SMOOTH,
}

/**
 * Immutable synchronization tuning shared by measurement cadence, correction decisions and player
 * actuation. Stability-sensitive values live here so the playback stack has one source of truth.
 */
data class PlaybackSyncTuning(
    val referenceIntervalMs: Long,
    val driftEnterThresholdMs: Long,
    val driftExitThresholdMs: Long,
    val hardSeekThresholdMs: Long,
    val maxSpeedDelta: Float,
    val requiredConsistentSamples: Int,
    val filterWindowSize: Int,
    val hardSeekCooldownMs: Long,
    val settleAfterSeekMs: Long,
    val maxClockUncertaintyMs: Long,
    val speedSmoothing: Float,
    val baselineLimit: Float,
    val baselineLearningWindowMs: Long,
    val speedQuantizationStep: Float,
    val speedCommandIntervalMs: Long,
    val urgentSpeedDelta: Float,
    val actualSpeedMatchTolerance: Float,
    val activeCorrectionIntervalMs: Long,
    val stablePlayingIntervalMs: Long,
    val waitingIntervalMs: Long,
    val suspendedRecheckIntervalMs: Long,
) {
    init {
        require(referenceIntervalMs > 0)
        require(driftEnterThresholdMs >= 0)
        require(driftExitThresholdMs in 0..driftEnterThresholdMs)
        require(hardSeekThresholdMs > driftEnterThresholdMs)
        require(maxSpeedDelta in 0f..0.05f)
        require(requiredConsistentSamples >= 2)
        require(filterWindowSize >= requiredConsistentSamples)
        require(hardSeekCooldownMs >= 0)
        require(settleAfterSeekMs >= 0)
        require(maxClockUncertaintyMs >= 0)
        require(speedSmoothing in 0f..1f)
        require(baselineLimit in 0f..maxSpeedDelta)
        require(baselineLearningWindowMs > 0)
        require(speedQuantizationStep > 0f)
        require(speedCommandIntervalMs >= 0L)
        require(urgentSpeedDelta >= speedQuantizationStep)
        require(actualSpeedMatchTolerance >= 0f)
        require(activeCorrectionIntervalMs > 0L)
        require(stablePlayingIntervalMs >= activeCorrectionIntervalMs)
        require(waitingIntervalMs >= activeCorrectionIntervalMs)
        require(suspendedRecheckIntervalMs > 0L)
        require(referenceIntervalMs >= activeCorrectionIntervalMs)
    }

    val minimumSpeed: Float
        get() = 1f - maxSpeedDelta

    val maximumSpeed: Float
        get() = 1f + maxSpeedDelta
}

fun PlaybackSyncProfile.tuning(): PlaybackSyncTuning =
    when (this) {
        PlaybackSyncProfile.TIGHT ->
            PlaybackSyncTuning(
                referenceIntervalMs = 800L,
                driftEnterThresholdMs = 55L,
                driftExitThresholdMs = 18L,
                hardSeekThresholdMs = 450L,
                maxSpeedDelta = 0.005f,
                requiredConsistentSamples = 3,
                filterWindowSize = 5,
                hardSeekCooldownMs = 10_000L,
                settleAfterSeekMs = 2_500L,
                maxClockUncertaintyMs = 35L,
                speedSmoothing = 0.32f,
                baselineLimit = 0.002f,
                baselineLearningWindowMs = 60_000L,
                speedQuantizationStep = 0.00025f,
                speedCommandIntervalMs = 2_500L,
                urgentSpeedDelta = 0.0025f,
                actualSpeedMatchTolerance = 0.00010f,
                activeCorrectionIntervalMs = 400L,
                stablePlayingIntervalMs = 800L,
                waitingIntervalMs = 1_200L,
                suspendedRecheckIntervalMs = 1_000L,
            )
        PlaybackSyncProfile.BALANCED ->
            PlaybackSyncTuning(
                referenceIntervalMs = 1_000L,
                driftEnterThresholdMs = 80L,
                driftExitThresholdMs = 20L,
                hardSeekThresholdMs = 500L,
                maxSpeedDelta = 0.005f,
                requiredConsistentSamples = 3,
                filterWindowSize = 5,
                hardSeekCooldownMs = 12_000L,
                settleAfterSeekMs = 3_000L,
                maxClockUncertaintyMs = 40L,
                speedSmoothing = 0.35f,
                baselineLimit = 0.002f,
                baselineLearningWindowMs = 60_000L,
                speedQuantizationStep = 0.00025f,
                speedCommandIntervalMs = 4_000L,
                urgentSpeedDelta = 0.002f,
                actualSpeedMatchTolerance = 0.00010f,
                activeCorrectionIntervalMs = 500L,
                stablePlayingIntervalMs = 1_000L,
                waitingIntervalMs = 1_500L,
                suspendedRecheckIntervalMs = 1_000L,
            )
        PlaybackSyncProfile.SMOOTH ->
            PlaybackSyncTuning(
                referenceIntervalMs = 1_400L,
                driftEnterThresholdMs = 120L,
                driftExitThresholdMs = 35L,
                hardSeekThresholdMs = 700L,
                maxSpeedDelta = 0.0035f,
                requiredConsistentSamples = 4,
                filterWindowSize = 7,
                hardSeekCooldownMs = 15_000L,
                settleAfterSeekMs = 3_500L,
                maxClockUncertaintyMs = 50L,
                speedSmoothing = 0.25f,
                baselineLimit = 0.0015f,
                baselineLearningWindowMs = 75_000L,
                speedQuantizationStep = 0.00025f,
                speedCommandIntervalMs = 6_000L,
                urgentSpeedDelta = 0.00175f,
                actualSpeedMatchTolerance = 0.00010f,
                activeCorrectionIntervalMs = 700L,
                stablePlayingIntervalMs = 1_400L,
                waitingIntervalMs = 1_800L,
                suspendedRecheckIntervalMs = 1_200L,
            )
    }
