package com.darius.unison.room

import com.darius.unison.model.CanonicalPlaybackState
import com.darius.unison.model.MemberSnapshot
import com.darius.unison.model.PeerId
import com.darius.unison.model.QueueItem
import com.darius.unison.model.QueueItemId
import com.darius.unison.model.RepeatMode
import com.darius.unison.model.RoomOptions
import com.darius.unison.model.RoomSnapshot
import com.darius.unison.model.TrackDescriptor
import com.darius.unison.model.UserCommand
import com.darius.unison.protocol.ProtocolBody

/**
 * Pure room-state transitions. Network and player side effects are deliberately outside this class.
 */
object RoomReducer {
    const val DEFAULT_COMMAND_LEAD_NS = 1_200_000_000L
    const val MAX_QUEUE_ITEMS = 1_000
    const val MAX_TRACKS_PER_COMMAND = MAX_QUEUE_ITEMS

    sealed interface Decision {
        data class Accepted(val mutations: List<Mutation>) : Decision

        data class Rejected(val reason: String) : Decision
    }

    data class Mutation(
        val sequence: Long,
        val body: ProtocolBody,
        val snapshot: RoomSnapshot,
    )

    fun decide(
        snapshot: RoomSnapshot,
        command: UserCommand,
        coordinatorNowNs: Long,
        leadNs: Long = DEFAULT_COMMAND_LEAD_NS,
    ): Decision {
        if (command.commandId.length !in 1..MAX_COMMAND_ID_LENGTH) {
            return Decision.Rejected("Invalid command")
        }
        val memberExists = snapshot.members.any { it.peerId == command.requestedBy && it.connected }
        if (!memberExists) return Decision.Rejected("You are no longer connected to this room")
        return when (command) {
            is UserCommand.Play -> play(snapshot, coordinatorNowNs, leadNs, command.commandId)
            is UserCommand.Pause -> pause(snapshot, coordinatorNowNs, leadNs, command.commandId)
            is UserCommand.Seek ->
                seek(snapshot, command.positionMs, coordinatorNowNs, leadNs, command.commandId)
            is UserCommand.SkipNext ->
                changeItem(snapshot, +1, coordinatorNowNs, leadNs, command.commandId)
            is UserCommand.SkipPrevious -> {
                val currentPosition = snapshot.playback.projectedPositionMs(coordinatorNowNs)
                if (currentPosition > 4_000)
                    seek(snapshot, 0, coordinatorNowNs, leadNs, command.commandId)
                else changeItem(snapshot, -1, coordinatorNowNs, leadNs, command.commandId)
            }

            is UserCommand.PlayQueueItem ->
                playQueueItem(
                    snapshot,
                    command.queueItemId,
                    command.resumePlayback,
                    coordinatorNowNs,
                    leadNs,
                    command.commandId,
                )

            is UserCommand.QueueAdd ->
                addTracks(
                    snapshot,
                    command.tracks,
                    command.requestedBy,
                    command.insertAfterCurrent,
                )

            is UserCommand.QueueRemove ->
                removeQueueItem(snapshot, command.queueItemId, coordinatorNowNs, leadNs)
            is UserCommand.QueueMove ->
                moveQueueItem(snapshot, command.queueItemId, command.newIndex)
            is UserCommand.QueueMoveAfterCurrent -> moveAfterCurrent(snapshot, command.queueItemId)
            is UserCommand.QueueClearPlayed -> clearPlayed(snapshot)
            is UserCommand.QueueClear -> clearQueue(snapshot)
            is UserCommand.QueueShuffle -> shuffleQueue(snapshot, command.shuffleSeed)
            is UserCommand.RepeatModeChange -> changeRepeatMode(snapshot, command.repeatMode)
            is UserCommand.OptionsChange -> changeOptions(snapshot, command.options)
        }
    }

    fun applyCanonical(snapshot: RoomSnapshot, sequence: Long, body: ProtocolBody): RoomSnapshot {
        if (sequence <= snapshot.sequence) return snapshot
        val updated =
            when (body) {
                is ProtocolBody.QueueItemsAdded -> {
                    if (body.items.isEmpty()) return snapshot.copy(sequence = sequence)
                    val insertionIndex =
                        body.index?.coerceIn(0, snapshot.queue.size) ?: snapshot.queue.size
                    val updatedQueue =
                        snapshot.queue.toMutableList().apply { addAll(insertionIndex, body.items) }
                    snapshot.copy(
                        queue = updatedQueue,
                        queueRevision = sequence,
                        playback =
                            if (snapshot.playback.queueItemId == null) {
                                snapshot.playback.copy(
                                    queueItemId = body.items.first().queueItemId,
                                    revision = sequence,
                                )
                            } else {
                                snapshot.playback
                            },
                    )
                }

                is ProtocolBody.QueueItemsRemoved -> {
                    val removed = body.queueItemIds.toHashSet()
                    snapshot.copy(
                        queue = snapshot.queue.filterNot { it.queueItemId in removed },
                        queueRevision = sequence,
                        preparedQueueItemIds = snapshot.preparedQueueItemIds - removed,
                        // When the audible item is removed, the following CurrentItemChanged
                        // mutation owns
                        // the scheduled transition. Keeping the old canonical playback reference
                        // for this
                        // one intermediate sequence prevents periodic state sync from switching
                        // early.
                        playback = snapshot.playback,
                    )
                }

                is ProtocolBody.QueueItemMoved -> {
                    val list = snapshot.queue.toMutableList()
                    val old = list.indexOfFirst { it.queueItemId == body.queueItemId }
                    if (old >= 0) {
                        val item = list.removeAt(old)
                        list.add(body.newIndex.coerceIn(0, list.size), item)
                    }
                    snapshot.copy(queue = list, queueRevision = sequence)
                }

                ProtocolBody.QueueCleared ->
                    snapshot.copy(
                        queue = emptyList(),
                        preparedQueueItemIds = emptySet(),
                        repeatMode = RepeatMode.OFF,
                        playback = CanonicalPlaybackState(revision = sequence),
                        queueRevision = sequence,
                    )

                is ProtocolBody.QueuePreparedSetChanged ->
                    snapshot.copy(
                        preparedQueueItemIds =
                            body.preparedQueueItemIds.intersect(
                                snapshot.queue.mapTo(hashSetOf()) { it.queueItemId }
                            )
                    )

                is ProtocolBody.RoomOptionsChanged ->
                    snapshot.copy(options = body.options.normalized())
                is ProtocolBody.QueueShuffled -> {
                    val byId = snapshot.queue.associateBy { it.queueItemId }
                    val requested = body.orderedQueueItemIds.toHashSet()
                    val ordered =
                        body.orderedQueueItemIds.mapNotNull(byId::get) +
                            snapshot.queue.filter { it.queueItemId !in requested }
                    snapshot.copy(queue = ordered, queueRevision = sequence)
                }

                is ProtocolBody.RepeatModeChanged -> snapshot.copy(repeatMode = body.repeatMode)

                is ProtocolBody.PlayScheduled ->
                    snapshot.copy(
                        playback =
                            CanonicalPlaybackState(
                                queueItemId = body.queueItemId,
                                positionAtTimestampMs = body.positionMs.coerceAtLeast(0),
                                coordinatorTimestampNs = body.executeAtCoordinatorNs,
                                isPlaying = true,
                                revision = sequence,
                            )
                    )

                is ProtocolBody.PauseScheduled ->
                    snapshot.copy(
                        playback =
                            CanonicalPlaybackState(
                                queueItemId = body.queueItemId,
                                positionAtTimestampMs = body.positionMs.coerceAtLeast(0),
                                coordinatorTimestampNs = body.executeAtCoordinatorNs,
                                isPlaying = false,
                                playbackSpeed = 1f,
                                revision = sequence,
                            )
                    )

                is ProtocolBody.SeekScheduled ->
                    snapshot.copy(
                        playback =
                            CanonicalPlaybackState(
                                queueItemId = body.queueItemId,
                                positionAtTimestampMs = body.positionMs.coerceAtLeast(0),
                                coordinatorTimestampNs = body.executeAtCoordinatorNs,
                                isPlaying = body.resumePlayback,
                                revision = sequence,
                            )
                    )

                is ProtocolBody.CurrentItemChanged ->
                    snapshot.copy(
                        playback =
                            CanonicalPlaybackState(
                                queueItemId = body.queueItemId,
                                positionAtTimestampMs = body.positionMs.coerceAtLeast(0),
                                coordinatorTimestampNs = body.executeAtCoordinatorNs,
                                isPlaying = body.resumePlayback,
                                revision = sequence,
                            )
                    )

                is ProtocolBody.PeerJoined ->
                    snapshot.copy(members = upsertMember(snapshot.members, body.member))
                is ProtocolBody.PeerUpdated ->
                    snapshot.copy(members = upsertMember(snapshot.members, body.member))
                is ProtocolBody.PeerLeft ->
                    snapshot.copy(
                        members =
                            snapshot.members.map {
                                if (it.peerId == body.peerId) it.copy(connected = false) else it
                            }
                    )

                else -> snapshot
            }
        return updated.copy(sequence = sequence)
    }

    private fun play(snapshot: RoomSnapshot, now: Long, lead: Long, commandId: String): Decision {
        val queueItem =
            snapshot.playback.queueItemId?.let { id ->
                snapshot.queue.firstOrNull { it.queueItemId == id }
            }
                ?: snapshot.queue.firstOrNull()
                ?: return Decision.Rejected("Add music before playing")
        if (
            snapshot.options.waitAtTrackBoundary &&
                queueItem.queueItemId !in snapshot.preparedQueueItemIds
        ) {
            return Decision.Rejected("Getting this song ready")
        }
        val position =
            if (snapshot.playback.queueItemId == queueItem.queueItemId) {
                snapshot.playback.projectedPositionMs(now)
            } else 0
        val executeAt = now + lead
        return mutation(
            snapshot,
            ProtocolBody.PlayScheduled(queueItem.queueItemId, position, executeAt, commandId),
        )
    }

    private fun pause(snapshot: RoomSnapshot, now: Long, lead: Long, commandId: String): Decision {
        if (snapshot.playback.queueItemId == null) return Decision.Rejected("Nothing is playing")
        val executeAt = now + lead
        val positionAtExecution = snapshot.playback.projectedPositionMs(executeAt)
        return mutation(
            snapshot,
            ProtocolBody.PauseScheduled(
                snapshot.playback.queueItemId,
                positionAtExecution,
                executeAt,
                commandId,
            ),
        )
    }

    private fun seek(
        snapshot: RoomSnapshot,
        requestedPosition: Long,
        now: Long,
        lead: Long,
        commandId: String,
    ): Decision {
        val itemId =
            snapshot.playback.queueItemId
                ?: snapshot.queue.firstOrNull()?.queueItemId
                ?: return Decision.Rejected("The queue is empty")
        val item =
            snapshot.queue.firstOrNull { it.queueItemId == itemId }
                ?: return Decision.Rejected("This song is unavailable")
        val max = item.track.durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        val position = requestedPosition.coerceIn(0, max)
        return mutation(
            snapshot,
            ProtocolBody.SeekScheduled(
                itemId,
                position,
                snapshot.playback.isPlaying,
                now + lead,
                commandId,
            ),
        )
    }

    private fun changeItem(
        snapshot: RoomSnapshot,
        delta: Int,
        now: Long,
        lead: Long,
        commandId: String,
    ): Decision {
        if (snapshot.queue.isEmpty()) return Decision.Rejected("The queue is empty")
        val currentIndex =
            snapshot.queue
                .indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
                .let { if (it < 0) 0 else it }
        val newIndex = (currentIndex + delta).coerceIn(0, snapshot.queue.lastIndex)
        if (newIndex == currentIndex && snapshot.playback.queueItemId != null) {
            return if (delta > 0) Decision.Rejected("Already at the end of the queue")
            else seek(snapshot, 0, now, lead, commandId)
        }
        val target = snapshot.queue[newIndex]
        if (
            snapshot.options.waitAtTrackBoundary &&
                target.queueItemId !in snapshot.preparedQueueItemIds
        ) {
            return Decision.Rejected("The next song is not ready yet")
        }
        return mutation(
            snapshot,
            ProtocolBody.CurrentItemChanged(
                target.queueItemId,
                0,
                now + lead,
                snapshot.playback.isPlaying,
                commandId,
            ),
        )
    }

    private fun playQueueItem(
        snapshot: RoomSnapshot,
        id: QueueItemId,
        resumePlayback: Boolean,
        now: Long,
        lead: Long,
        commandId: String,
    ): Decision {
        val target =
            snapshot.queue.firstOrNull { it.queueItemId == id }
                ?: return Decision.Rejected("That song is no longer in the queue")
        val ready = target.queueItemId in snapshot.preparedQueueItemIds
        if (snapshot.options.waitAtTrackBoundary && !ready) {
            return Decision.Rejected("The selected song is still preparing")
        }
        return mutation(
            snapshot,
            ProtocolBody.CurrentItemChanged(
                queueItemId = target.queueItemId,
                positionMs = 0,
                executeAtCoordinatorNs = now + lead,
                resumePlayback = resumePlayback,
                commandId = commandId,
            ),
        )
    }

    private fun addTracks(
        snapshot: RoomSnapshot,
        tracks: List<TrackDescriptor>,
        peerId: PeerId,
        insertAfterCurrent: Boolean,
    ): Decision {
        if (tracks.isEmpty()) return Decision.Rejected("No playable songs were selected")
        if (tracks.size > MAX_TRACKS_PER_COMMAND)
            return Decision.Rejected("Add fewer songs at a time")
        if (tracks.any { !it.isValidForRoom() }) return Decision.Rejected("Invalid song metadata")
        val remainingCapacity = (MAX_QUEUE_ITEMS - snapshot.queue.size).coerceAtLeast(0)
        if (remainingCapacity == 0) return Decision.Rejected("The room queue is full")
        val insertionIndex =
            if (insertAfterCurrent) {
                snapshot.queue
                    .indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
                    .let { if (it < 0) 0 else it + 1 }
            } else snapshot.queue.size
        val sequence = snapshot.sequence + 1
        val items =
            tracks.take(remainingCapacity).map { track ->
                QueueItem.create(track, peerId, sequence)
            }
        if (items.isEmpty()) return Decision.Rejected("No songs were added")
        val body = ProtocolBody.QueueItemsAdded(items, insertionIndex.takeIf { insertAfterCurrent })
        val updated = applyCanonical(snapshot, sequence, body)
        return Decision.Accepted(listOf(Mutation(sequence, body, updated)))
    }

    private fun removeQueueItem(
        snapshot: RoomSnapshot,
        id: QueueItemId,
        now: Long,
        lead: Long,
    ): Decision {
        val removedIndex = snapshot.queue.indexOfFirst { it.queueItemId == id }
        if (removedIndex < 0) return Decision.Rejected("That song is no longer in the queue")
        val remainingQueue = snapshot.queue.filterNot { it.queueItemId == id }
        val replacement =
            if (remainingQueue.isEmpty()) null
            else {
                remainingQueue[removedIndex.coerceIn(0, remainingQueue.lastIndex)]
            }
        val sequence = snapshot.sequence + 1
        val body = ProtocolBody.QueueItemsRemoved(listOf(id))
        var working = applyCanonical(snapshot, sequence, body)
        val mutations = mutableListOf(Mutation(sequence, body, working))
        if (snapshot.playback.queueItemId == id) {
            val nextBody =
                ProtocolBody.CurrentItemChanged(
                    queueItemId = replacement?.queueItemId,
                    positionMs = 0,
                    executeAtCoordinatorNs = now + lead,
                    resumePlayback = replacement != null && snapshot.playback.isPlaying,
                )
            val nextSequence = working.sequence + 1
            working = applyCanonical(working, nextSequence, nextBody)
            mutations += Mutation(nextSequence, nextBody, working)
        }
        return Decision.Accepted(mutations)
    }

    private fun moveQueueItem(
        snapshot: RoomSnapshot,
        id: QueueItemId,
        requestedIndex: Int,
    ): Decision {
        if (snapshot.queue.none { it.queueItemId == id })
            return Decision.Rejected("That song is no longer in the queue")
        return mutation(
            snapshot,
            ProtocolBody.QueueItemMoved(id, requestedIndex.coerceIn(0, snapshot.queue.lastIndex)),
        )
    }

    private fun moveAfterCurrent(snapshot: RoomSnapshot, id: QueueItemId): Decision {
        val oldIndex = snapshot.queue.indexOfFirst { it.queueItemId == id }
        if (oldIndex < 0) return Decision.Rejected("That song is no longer in the queue")
        val currentId = snapshot.playback.queueItemId
        if (currentId == id) return Decision.Rejected("That song is already playing")
        val currentIndex = snapshot.queue.indexOfFirst { it.queueItemId == currentId }
        val targetIndex =
            if (currentIndex < 0) {
                    0
                } else {
                    val currentAfterRemoval = currentIndex - if (oldIndex < currentIndex) 1 else 0
                    currentAfterRemoval + 1
                }
                .coerceIn(0, snapshot.queue.lastIndex)
        if (oldIndex == targetIndex) return Decision.Rejected("That song is already next")
        return mutation(snapshot, ProtocolBody.QueueItemMoved(id, targetIndex))
    }

    private fun clearPlayed(snapshot: RoomSnapshot): Decision {
        val currentIndex =
            snapshot.queue.indexOfFirst { it.queueItemId == snapshot.playback.queueItemId }
        if (currentIndex <= 0) return Decision.Rejected("There are no played songs to clear")
        return mutation(
            snapshot,
            ProtocolBody.QueueItemsRemoved(
                snapshot.queue.take(currentIndex).map { it.queueItemId }
            ),
        )
    }

    private fun clearQueue(snapshot: RoomSnapshot): Decision {
        if (snapshot.queue.isEmpty()) return Decision.Rejected("The queue is already empty")
        return mutation(snapshot, ProtocolBody.QueueCleared)
    }

    private fun changeRepeatMode(snapshot: RoomSnapshot, repeatMode: RepeatMode): Decision {
        if (snapshot.repeatMode == repeatMode) return Decision.Rejected("Repeat mode is already set")
        return mutation(snapshot, ProtocolBody.RepeatModeChanged(repeatMode))
    }

    private fun shuffleQueue(snapshot: RoomSnapshot, seed: Long): Decision {
        val orderedIds =
            QueueShufflePolicy.shuffledOrder(snapshot.queue, snapshot.playback.queueItemId, seed)
                ?: return Decision.Rejected("Add at least two upcoming songs before shuffling")
        return mutation(snapshot, ProtocolBody.QueueShuffled(orderedIds))
    }

    private fun changeOptions(snapshot: RoomSnapshot, options: RoomOptions): Decision =
        mutation(snapshot, ProtocolBody.RoomOptionsChanged(options.normalized()))

    private fun mutation(snapshot: RoomSnapshot, body: ProtocolBody): Decision {
        val sequence = snapshot.sequence + 1
        val updated = applyCanonical(snapshot, sequence, body)
        return Decision.Accepted(listOf(Mutation(sequence, body, updated)))
    }

    private fun TrackDescriptor.isValidForRoom(): Boolean =
        TRACK_ID_PATTERN.matches(trackId.value) &&
            sizeBytes in 1..MAX_TRACK_BYTES &&
            durationMs in 0..MAX_TRACK_DURATION_MS &&
            mimeType.isWithin(MAX_MIME_LENGTH) &&
            title.isWithin(MAX_METADATA_LENGTH) &&
            artist.isWithin(MAX_METADATA_LENGTH) &&
            album.isWithin(MAX_METADATA_LENGTH) &&
            originalFileName.isWithin(MAX_FILENAME_LENGTH)

    private fun String?.isWithin(maxLength: Int): Boolean =
        this == null || (length <= maxLength && none { it.isISOControl() })

    private fun upsertMember(
        members: List<MemberSnapshot>,
        member: MemberSnapshot,
    ): List<MemberSnapshot> {
        val index = members.indexOfFirst { it.peerId == member.peerId }
        return if (index < 0) members + member
        else members.toMutableList().apply { set(index, member) }
    }

    private const val MAX_COMMAND_ID_LENGTH = 128
    private const val MAX_TRACK_BYTES = 1L shl 30
    private const val MAX_TRACK_DURATION_MS = 30L * 24 * 60 * 60 * 1000
    private const val MAX_METADATA_LENGTH = 256
    private const val MAX_FILENAME_LENGTH = 512
    private const val MAX_MIME_LENGTH = 128
    private val TRACK_ID_PATTERN = Regex("[0-9a-f]{64}")

    private fun UserCommand.affectsPlaybackTimeline(snapshot: RoomSnapshot): Boolean =
        when (this) {
            is UserCommand.Play,
            is UserCommand.Pause,
            is UserCommand.Seek,
            is UserCommand.SkipNext,
            is UserCommand.SkipPrevious,
            is UserCommand.PlayQueueItem -> true

            is UserCommand.QueueRemove -> queueItemId == snapshot.playback.queueItemId
            is UserCommand.QueueAdd,
            is UserCommand.QueueMove,
            is UserCommand.QueueMoveAfterCurrent,
            is UserCommand.QueueClearPlayed,
            is UserCommand.QueueClear,
            is UserCommand.QueueShuffle,
            is UserCommand.RepeatModeChange,
            is UserCommand.OptionsChange -> false
        }

    private fun RoomOptions.normalized() =
        copy(preloadCount = preloadCount.coerceIn(1, TrackPrefetchPolicy.DEFAULT_UPCOMING_COUNT))
}
