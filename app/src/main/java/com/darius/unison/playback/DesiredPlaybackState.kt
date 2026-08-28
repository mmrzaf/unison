package com.darius.unison.playback

import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomSnapshot

/**
 * Reconstructible playback intent derived from canonical playback plus the current runtime
 * readiness projection. [contentRevision] excludes membership and telemetry, so unrelated room
 * mutations do not force Media3 timeline work.
 */
data class DesiredPlaybackState(
    val canonicalSequence: Long,
    val queueRevision: Long,
    val playbackRevision: Long,
    val contentRevision: Long,
    val queueItemIds: List<QueueItemId>,
    val preparedQueueItemIds: Set<QueueItemId>,
    val currentQueueItemId: QueueItemId?,
    val positionAtTimestampMs: Long,
    val coordinatorTimestampNs: Long,
    val playWhenReady: Boolean,
    val repeatMode: RepeatMode,
    val preloadCount: Int,
    val waitAtTrackBoundary: Boolean,
) {
    companion object {
        fun from(snapshot: RoomSnapshot): DesiredPlaybackState = from(snapshot, emptySet())

        fun from(
            snapshot: RoomSnapshot,
            preparedQueueItemIds: Set<QueueItemId>,
        ): DesiredPlaybackState {
            val queueIds = snapshot.queue.map { it.queueItemId }
            val preparedIds = preparedQueueItemIds.toSet()
            return DesiredPlaybackState(
                canonicalSequence = snapshot.sequence,
                queueRevision = snapshot.queueRevision,
                playbackRevision = snapshot.playback.revision,
                contentRevision = stableRevision(snapshot, queueIds, preparedIds),
                queueItemIds = queueIds,
                preparedQueueItemIds = preparedIds,
                currentQueueItemId = snapshot.playback.queueItemId,
                positionAtTimestampMs = snapshot.playback.positionAtTimestampMs,
                coordinatorTimestampNs = snapshot.playback.coordinatorTimestampNs,
                playWhenReady = snapshot.playback.isPlaying,
                repeatMode = snapshot.repeatMode,
                preloadCount = snapshot.options.preloadCount,
                waitAtTrackBoundary = snapshot.options.waitAtTrackBoundary,
            )
        }

        private fun stableRevision(
            snapshot: RoomSnapshot,
            queueIds: List<QueueItemId>,
            preparedIds: Set<QueueItemId>,
        ): Long {
            var hash = FNV_OFFSET_BASIS
            fun mix(value: Long) {
                hash = (hash xor value) * FNV_PRIME
            }
            fun mix(value: String) = value.forEach { mix(it.code.toLong()) }

            mix(snapshot.queueRevision)
            mix(snapshot.playback.revision)
            queueIds.forEach { mix(it.value) }
            preparedIds.map { it.value }.sorted().forEach(::mix)
            mix(snapshot.playback.queueItemId?.value ?: "none")
            mix(snapshot.playback.positionAtTimestampMs)
            mix(snapshot.playback.coordinatorTimestampNs)
            mix(if (snapshot.playback.isPlaying) 1L else 0L)
            mix(snapshot.repeatMode.ordinal.toLong())
            mix(snapshot.options.preloadCount.toLong())
            mix(if (snapshot.options.waitAtTrackBoundary) 1L else 0L)
            return hash
        }

        private const val FNV_OFFSET_BASIS = -3750763034362895579L
        private const val FNV_PRIME = 1099511628211L
    }
}
