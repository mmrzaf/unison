package com.darius.unison.protocol

import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerEndpoint
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RoomSnapshot

/**
 * Validates an untrusted or locally-produced room snapshot before it is installed or transmitted.
 *
 * Structural validation is deliberately independent from kotlinx.serialization so it can be tested
 * in the deterministic core harness. Callers that have encoded the snapshot should supply the exact
 * UTF-8 payload size through [SnapshotValidationContext.encodedSizeBytes].
 */
class RoomSnapshotValidator(
    private val maxMembers: Int = DEFAULT_MAX_MEMBERS,
    private val maxQueueItems: Int = DEFAULT_MAX_QUEUE_ITEMS,
    private val maxEncodedSizeBytes: Int = MAX_SNAPSHOT_PAYLOAD_BYTES,
) {
    fun validate(
        snapshot: RoomSnapshot,
        context: SnapshotValidationContext = SnapshotValidationContext(),
    ): SnapshotValidationResult {
        val issues = mutableListOf<SnapshotValidationIssue>()

        fun reject(code: String, message: String) {
            issues += SnapshotValidationIssue(code, message)
        }

        if (!ROOM_ID_PATTERN.matches(snapshot.roomId)) reject("room_id", "Invalid room identifier")
        if (!snapshot.roomName.isBoundedText(1, MAX_ROOM_NAME_LENGTH)) {
            reject("room_name", "Invalid room name")
        }
        if (snapshot.term.number < 1) reject("term", "Coordinator term must be positive")
        if (!snapshot.term.coordinatorPeerId.isValidPeerId())
            reject("coordinator", "Invalid coordinator identity")
        if (snapshot.sequence < 0) reject("sequence", "Sequence must not be negative")
        if (snapshot.queueRevision !in 0..snapshot.sequence) {
            reject("queue_revision", "Queue revision is outside the canonical sequence")
        }
        context.expectedRoomId?.let {
            if (snapshot.roomId != it) reject("wrong_room", "Snapshot belongs to another room")
        }
        context.expectedCoordinatorPeerId?.let {
            if (snapshot.term.coordinatorPeerId != it) {
                reject(
                    "wrong_coordinator",
                    "Snapshot coordinator does not match the authenticated peer",
                )
            }
        }
        context.minimumTerm?.let {
            if (snapshot.term.number < it)
                reject("old_term", "Snapshot term is older than the accepted term")
        }
        context.minimumSequence?.let {
            if (snapshot.term.number == context.minimumTerm && snapshot.sequence < it) {
                reject("old_sequence", "Snapshot sequence is older than the accepted sequence")
            }
        }
        context.encodedSizeBytes?.let { encodedSize ->
            val budget = context.maxEncodedSizeBytes.coerceAtMost(maxEncodedSizeBytes)
            if (encodedSize !in 1..budget) {
                reject("payload_budget", "Snapshot exceeds the encoded payload budget")
            }
        }

        if (snapshot.members.isEmpty() || snapshot.members.size > maxMembers) {
            reject("member_count", "Invalid member count")
        }
        val memberIds = snapshot.members.map { it.peerId }
        if (memberIds.distinct().size != memberIds.size)
            reject("duplicate_member", "Duplicate member identity")
        if (snapshot.term.coordinatorPeerId !in memberIds) {
            reject("missing_coordinator", "Coordinator is not present in the member list")
        }
        snapshot.members.forEachIndexed { index, member -> validateMember(member, index, ::reject) }

        if (snapshot.queue.size > maxQueueItems) reject("queue_count", "Queue is too large")
        val queueIds = snapshot.queue.map { it.queueItemId }
        if (queueIds.distinct().size != queueIds.size)
            reject("duplicate_queue_item", "Duplicate queue item identity")
        val queueIdSet = queueIds.toSet()
        val memberIdSet = memberIds.toSet()
        snapshot.queue.forEachIndexed { index, item ->
            if (!item.queueItemId.isValidQueueItemId())
                reject("queue_item_id", "Invalid queue item at index $index")
            if (!item.addedByPeerId.isValidPeerId() || item.addedByPeerId !in memberIdSet) {
                reject("queue_added_by", "Queue item references an unknown member")
            }
            if (item.addedAtSequence !in 0..snapshot.sequence) {
                reject("queue_sequence", "Queue item has an invalid creation sequence")
            }
            val track = item.track
            if (!TRACK_ID_PATTERN.matches(track.trackId.value))
                reject("track_id", "Invalid track hash")
            if (track.sizeBytes !in 1..MAX_TRACK_BYTES) reject("track_size", "Invalid track size")
            if (track.durationMs !in 0..MAX_TRACK_DURATION_MS)
                reject("track_duration", "Invalid track duration")
            if (!track.mimeType.isBoundedNullableText(MAX_MIME_LENGTH))
                reject("mime", "Invalid MIME type")
            if (!track.title.isBoundedNullableText(MAX_METADATA_LENGTH))
                reject("title", "Invalid track title")
            if (!track.artist.isBoundedNullableText(MAX_METADATA_LENGTH))
                reject("artist", "Invalid track artist")
            if (!track.album.isBoundedNullableText(MAX_METADATA_LENGTH))
                reject("album", "Invalid track album")
            if (!track.originalFileName.isBoundedNullableText(MAX_FILENAME_LENGTH)) {
                reject("filename", "Invalid track filename")
            }
        }

        if (!queueIdSet.containsAll(snapshot.preparedQueueItemIds)) {
            reject("prepared_items", "Prepared-item set contains an item outside the queue")
        }
        val playbackItem = snapshot.playback.queueItemId
        if (playbackItem != null && playbackItem !in queueIdSet) {
            reject("playback_item", "Playback item is not present in the queue")
        }
        if (snapshot.playback.revision !in 0..snapshot.sequence) {
            reject("playback_revision", "Playback revision is outside the canonical sequence")
        }
        if (snapshot.playback.positionAtTimestampMs < 0)
            reject("playback_position", "Playback position is negative")
        if (snapshot.playback.coordinatorTimestampNs < 0)
            reject("playback_timestamp", "Playback timestamp is negative")
        if (
            !snapshot.playback.playbackSpeed.isFinite() ||
                snapshot.playback.playbackSpeed !in 0.5f..2.0f
        ) {
            reject("playback_speed", "Playback speed is outside the protocol bounds")
        }

        if (snapshot.options.preloadCount !in 1..3) reject("preload", "Invalid preload count")


        return if (issues.isEmpty()) SnapshotValidationResult.Valid
        else SnapshotValidationResult.Invalid(issues)
    }

    private fun validateMember(
        member: MemberSnapshot,
        index: Int,
        reject: (String, String) -> Unit,
    ) {
        if (!member.peerId.isValidPeerId())
            reject("member_id", "Invalid member identity at index $index")
        if (!member.displayName.isBoundedText(1, MAX_DISPLAY_NAME_LENGTH)) {
            reject("member_name", "Invalid member display name at index $index")
        }
        member.endpoint?.let { endpoint ->
            validateEndpoint(endpoint, member.peerId, reject)
        }
    }

    private fun validateEndpoint(
        endpoint: PeerEndpoint,
        expectedPeerId: PeerId,
        reject: (String, String) -> Unit,
    ) {
        if (endpoint.peerId != expectedPeerId)
            reject("endpoint_peer", "Endpoint identity does not match its member")
        if (!endpoint.displayName.isBoundedText(1, MAX_DISPLAY_NAME_LENGTH)) {
            reject("endpoint_name", "Invalid endpoint display name")
        }
        if (!endpoint.hostAddress.isBoundedText(1, MAX_HOST_LENGTH))
            reject("endpoint_host", "Invalid endpoint host")
        if (endpoint.port !in 1..65535) reject("endpoint_port", "Invalid endpoint port")
        if (!endpoint.appVersion.isBoundedText(1, MAX_APP_VERSION_LENGTH)) {
            reject("endpoint_version", "Invalid endpoint application version")
        }
        if (endpoint.lastSeenElapsedMs < 0)
            reject("endpoint_last_seen", "Invalid endpoint timestamp")
    }

    companion object {
        /**
         * Leaves room for the envelope, discriminator fields, peer directory, and encrypted frame.
         */
        const val MAX_SNAPSHOT_PAYLOAD_BYTES: Int = 3 * 1024 * 1024
        const val DEFAULT_MAX_MEMBERS = 16
        const val DEFAULT_MAX_QUEUE_ITEMS = 1_000

        private const val MAX_ROOM_NAME_LENGTH = 80
        private const val MAX_DISPLAY_NAME_LENGTH = 80
        private const val MAX_HOST_LENGTH = 255
        private const val MAX_APP_VERSION_LENGTH = 64
        private const val MAX_METADATA_LENGTH = 256
        private const val MAX_FILENAME_LENGTH = 512
        private const val MAX_MIME_LENGTH = 128
        private const val MAX_TRACK_BYTES = 1L shl 30
        private const val MAX_TRACK_DURATION_MS = 30L * 24 * 60 * 60 * 1000

        private val ROOM_ID_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")
        private val PEER_ID_PATTERN = Regex("[A-Za-z0-9._:-]{16,128}")
        private val QUEUE_ITEM_ID_PATTERN = Regex("[A-Za-z0-9._:-]{16,128}")
        private val TRACK_ID_PATTERN = Regex("[0-9a-f]{64}")

        private fun PeerId.isValidPeerId(): Boolean = PEER_ID_PATTERN.matches(value)

        private fun QueueItemId.isValidQueueItemId(): Boolean = QUEUE_ITEM_ID_PATTERN.matches(value)

        private fun String.isBoundedText(min: Int, max: Int): Boolean =
            length in min..max && none { it.isISOControl() }

        private fun String?.isBoundedNullableText(max: Int): Boolean =
            this == null || (length <= max && none { it.isISOControl() })
    }
}

data class SnapshotValidationContext(
    val expectedRoomId: String? = null,
    val expectedCoordinatorPeerId: PeerId? = null,
    val minimumTerm: Long? = null,
    val minimumSequence: Long? = null,
    val encodedSizeBytes: Int? = null,
    val maxEncodedSizeBytes: Int = RoomSnapshotValidator.MAX_SNAPSHOT_PAYLOAD_BYTES,
)

data class SnapshotValidationIssue(
    val code: String,
    val message: String,
)

sealed interface SnapshotValidationResult {
    data object Valid : SnapshotValidationResult

    data class Invalid(val issues: List<SnapshotValidationIssue>) : SnapshotValidationResult {
        val summary: String
            get() = issues.joinToString("; ") { it.message }
    }
}
