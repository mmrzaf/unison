package com.darius.unison.network

import com.darius.unison.protocol.Envelope
import com.darius.unison.protocol.ProtocolBody

/** Traffic classes use independent bounded queues so telemetry and transfers cannot delay state. */
enum class ControlTrafficClass {
    GUARANTEED,
    CLOCK,
    PLAYBACK_REFERENCE,
    TELEMETRY,
    TRANSFER,
}

object ControlTrafficClassifier {
    fun classify(envelope: Envelope): ControlTrafficClass {
        if (envelope.sequence != null) return ControlTrafficClass.GUARANTEED
        return when (envelope.body) {
            is ProtocolBody.ClockPing,
            is ProtocolBody.ClockPong,
            is ProtocolBody.ClockReady,
            is ProtocolBody.Heartbeat,
            is ProtocolBody.AckSequence -> ControlTrafficClass.CLOCK

            is ProtocolBody.PlaybackStateSync ->
                if (envelope.body.recovery) ControlTrafficClass.GUARANTEED
                else ControlTrafficClass.PLAYBACK_REFERENCE

            is ProtocolBody.PlaybackStatusReport -> ControlTrafficClass.TELEMETRY

            is ProtocolBody.TrackDescriptorMessage,
            is ProtocolBody.TrackHave,
            is ProtocolBody.TrackNeed,
            is ProtocolBody.TrackSourceAssigned,
            is ProtocolBody.TrackSourceAuthorized,
            is ProtocolBody.TrackReady,
            is ProtocolBody.TrackFailed,
            is ProtocolBody.TransferCancelled -> ControlTrafficClass.TRANSFER

            else -> ControlTrafficClass.GUARANTEED
        }
    }
}
