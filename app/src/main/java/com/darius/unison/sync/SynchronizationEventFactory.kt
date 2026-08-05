package com.darius.unison.sync

import com.darius.unison.model.PeerId
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.playback.AudioOutputRoute

/** Builds stable, sanitized synchronization diagnostics outside the room actor. */
object SynchronizationEventFactory {
    fun create(
        snapshot: RoomSnapshot,
        localPeerId: PeerId,
        deviceModel: String,
        androidVersion: Int,
        sampleCoordinatorNs: Long,
        samplePositionMs: Long,
        sampleAtLocalNs: Long,
        observedAtLocalNs: Long,
        outputRoute: AudioOutputRoute,
        buffering: Boolean,
        canonicalPositionMs: Long?,
        clockEstimate: ClockEstimate,
        decision: PlaybackSyncDecision,
    ): SynchronizationEvent =
        SynchronizationEvent(
            timestampLocalNs = sampleAtLocalNs,
            timestampCoordinatorNs = sampleCoordinatorNs,
            deviceId = localPeerId.value.take(12),
            deviceModel = deviceModel.take(80),
            androidVersion = androidVersion,
            outputRoute = outputRoute.name,
            roomIdHash = snapshot.roomId.hashCode().toUInt().toString(16),
            coordinatorTerm = snapshot.term.number,
            queueItemId = snapshot.playback.queueItemId?.value,
            canonicalPositionMs = canonicalPositionMs,
            sampledPlayerPositionMs = samplePositionMs,
            sampleAgeMs = ((observedAtLocalNs - sampleAtLocalNs).coerceAtLeast(0L) / 1_000_000L),
            rawDriftMs = decision.rawDriftMs,
            filteredDriftMs = decision.filteredDriftMs,
            selectedSpeed = decision.selectedSpeed,
            learnedBaselineSpeed = decision.baselineSpeed,
            clockOffsetNs = clockEstimate.offsetNs,
            clockRate = clockEstimate.rate,
            clockRttMs = clockEstimate.rttNs.takeIf { it != Long.MAX_VALUE }?.div(1_000_000.0),
            clockUncertaintyMs =
                clockEstimate.uncertaintyNs.takeIf { it != Long.MAX_VALUE }?.div(1_000_000.0),
            clockState = clockEstimate.state.name,
            playbackSyncState = decision.state.name,
            action =
                when (decision.action) {
                    is SyncAction.SetSpeed -> "SET_SPEED"
                    is SyncAction.Seek -> "SEEK"
                    is SyncAction.Hold -> "HOLD"
                },
            actionReason = decision.reason,
            hardSeekCount = decision.hardSeekCount,
            buffering = buffering,
        )
}
