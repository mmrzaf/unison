package com.darius.unison.playback

import com.darius.unison.model.RoomSnapshot
import com.darius.unison.protocol.ProtocolBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single serialized ownership boundary between canonical room mutations and Media3 work.
 *
 * Exact transport operations remain ordered. Replaceable timeline/preparation work is represented
 * by one queued reconciliation token and always reads the newest snapshot submitted before that
 * token executes. Submission uses backpressure rather than dropping work after canonical commit.
 */
class CanonicalPlaybackDispatcher(
    scope: CoroutineScope,
    private val applyExact: suspend (ProtocolBody, RoomSnapshot) -> Unit,
    private val reconcileLatest: suspend (PlaybackReconciliation) -> Unit,
    private val onFailure: (ProtocolBody?, Throwable) -> Unit,
    private val preparedQueueItemIds: () -> Set<com.darius.unison.model.QueueItemId> = { emptySet() },
    capacity: Int = DEFAULT_CAPACITY,
) : AutoCloseable {

    data class Metrics(
        val exactSubmitted: Long,
        val exactApplied: Long,
        val reconciliationSubmitted: Long,
        val reconciliationCollapsed: Long,
        val reconciliationApplied: Long,
        val reconciliationSkipped: Long,
        val failures: Long,
    )

    data class PlaybackReconciliation(
        val snapshot: RoomSnapshot,
        val desired: DesiredPlaybackState,
        val triggers: Set<Trigger>,
    )

    enum class Trigger {
        QUEUE_CHANGED,
        PREPARATION_CHANGED,
        OPTIONS_CHANGED,
        PLAYBACK_MODE_CHANGED,
    }

    private sealed interface Work {
        data class Exact(val body: ProtocolBody, val snapshot: RoomSnapshot) : Work

        data class Reconcile(val batchId: Long) : Work
    }

    private data class PendingReconciliation(
        val snapshot: RoomSnapshot,
        val desired: DesiredPlaybackState,
        val triggers: Set<Trigger>,
    )

    private val stateLock = Any()
    private val submissionMutex = Mutex()
    private val work = Channel<Work>(capacity)
    private val pendingReconciliations = mutableMapOf<Long, PendingReconciliation>()
    private var openReconciliationBatchId: Long? = null
    private var nextReconciliationBatchId = 0L
    private var lastAppliedContentRevision: Long? = null
    private var exactSubmitted = 0L
    private var exactApplied = 0L
    private var reconciliationSubmitted = 0L
    private var reconciliationCollapsed = 0L
    private var reconciliationApplied = 0L
    private var reconciliationSkipped = 0L
    private var failures = 0L
    private val worker: Job = scope.launch {
        for (item in work) {
            try {
                when (item) {
                    is Work.Exact -> {
                        applyExact(item.body, item.snapshot)
                        synchronized(stateLock) { exactApplied++ }
                    }
                    is Work.Reconcile -> applyPendingReconciliation(item.batchId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(stateLock) { failures++ }
                onFailure((item as? Work.Exact)?.body, error)
            }
        }
    }

    suspend fun submit(body: ProtocolBody, snapshot: RoomSnapshot) =
        submissionMutex.withLock {
            when (val classification = classify(body)) {
                Classification.Ignore -> Unit
                Classification.Exact -> {
                    synchronized(stateLock) {
                        exactSubmitted++
                        // An exact transport command is an ordering barrier. Reconciliation work
                        // submitted after this point must use a new batch and may not collapse into
                        // a token already queued ahead of the exact command.
                        openReconciliationBatchId = null
                    }
                    work.send(Work.Exact(body, snapshot))
                }
                is Classification.Reconcile ->
                    requestReconciliationLocked(snapshot, classification.trigger)
            }
        }

    suspend fun reconcile(snapshot: RoomSnapshot, trigger: Trigger) =
        submissionMutex.withLock { requestReconciliationLocked(snapshot, trigger) }

    private suspend fun requestReconciliationLocked(snapshot: RoomSnapshot, trigger: Trigger) {
        var batchId = 0L
        val shouldQueue =
            synchronized(stateLock) {
                reconciliationSubmitted++
                val existingBatchId = openReconciliationBatchId
                if (existingBatchId != null) {
                    val previous = pendingReconciliations.getValue(existingBatchId)
                    pendingReconciliations[existingBatchId] =
                        PendingReconciliation(
                            snapshot = snapshot,
                            desired = DesiredPlaybackState.from(snapshot, preparedQueueItemIds()),
                            triggers = previous.triggers + trigger,
                        )
                    reconciliationCollapsed++
                    false
                } else {
                    batchId = ++nextReconciliationBatchId
                    openReconciliationBatchId = batchId
                    pendingReconciliations[batchId] =
                        PendingReconciliation(
                            snapshot = snapshot,
                            desired = DesiredPlaybackState.from(snapshot, preparedQueueItemIds()),
                            triggers = setOf(trigger),
                        )
                    true
                }
            }
        if (shouldQueue) work.send(Work.Reconcile(batchId))
    }

    private suspend fun applyPendingReconciliation(batchId: Long) {
        val pending =
            synchronized(stateLock) {
                val value = pendingReconciliations.remove(batchId)
                if (openReconciliationBatchId == batchId) openReconciliationBatchId = null
                value
            } ?: return

        if (lastAppliedContentRevision == pending.desired.contentRevision) {
            synchronized(stateLock) { reconciliationSkipped++ }
            return
        }
        reconcileLatest(
            PlaybackReconciliation(
                snapshot = pending.snapshot,
                desired = pending.desired,
                triggers = pending.triggers,
            )
        )
        lastAppliedContentRevision = pending.desired.contentRevision
        synchronized(stateLock) { reconciliationApplied++ }
    }

    fun metrics(): Metrics =
        synchronized(stateLock) {
            Metrics(
                exactSubmitted = exactSubmitted,
                exactApplied = exactApplied,
                reconciliationSubmitted = reconciliationSubmitted,
                reconciliationCollapsed = reconciliationCollapsed,
                reconciliationApplied = reconciliationApplied,
                reconciliationSkipped = reconciliationSkipped,
                failures = failures,
            )
        }

    fun resetMetrics() =
        synchronized(stateLock) {
            exactSubmitted = 0
            exactApplied = 0
            reconciliationSubmitted = 0
            reconciliationCollapsed = 0
            reconciliationApplied = 0
            reconciliationSkipped = 0
            failures = 0
        }

    override fun close() {
        work.close()
        worker.cancel()
        synchronized(stateLock) {
            pendingReconciliations.clear()
            openReconciliationBatchId = null
        }
    }

    private sealed interface Classification {
        data object Ignore : Classification

        data object Exact : Classification

        data class Reconcile(val trigger: Trigger) : Classification
    }

    private fun classify(body: ProtocolBody): Classification =
        when (body) {
            is ProtocolBody.QueueItemsAdded,
            is ProtocolBody.QueueItemMoved -> Classification.Reconcile(Trigger.QUEUE_CHANGED)

            is ProtocolBody.RoomOptionsChanged -> Classification.Reconcile(Trigger.OPTIONS_CHANGED)
            is ProtocolBody.QueueShuffled -> Classification.Reconcile(Trigger.QUEUE_CHANGED)
            is ProtocolBody.RepeatModeChanged -> Classification.Reconcile(Trigger.PLAYBACK_MODE_CHANGED)

            is ProtocolBody.QueueItemsRemoved,
            ProtocolBody.QueueCleared,
            is ProtocolBody.PlayScheduled,
            is ProtocolBody.PauseScheduled,
            is ProtocolBody.SeekScheduled,
            is ProtocolBody.CurrentItemChanged -> Classification.Exact

            else -> Classification.Ignore
        }

    companion object {
        const val DEFAULT_CAPACITY = 64
    }
}
