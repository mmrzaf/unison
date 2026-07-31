package com.darius.unison.playback

import com.darius.unison.model.RoomSnapshot
import com.darius.unison.protocol.ProtocolBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

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

        data object ReconcileLatest : Work
    }

    private data class PendingReconciliation(
        val snapshot: RoomSnapshot,
        val desired: DesiredPlaybackState,
        val triggers: Set<Trigger>,
    )

    private val stateLock = Any()
    private val work = Channel<Work>(capacity)
    private var pendingReconciliation: PendingReconciliation? = null
    private var reconciliationQueued = false
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
                    Work.ReconcileLatest -> applyPendingReconciliation()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(stateLock) { failures++ }
                onFailure((item as? Work.Exact)?.body, error)
            }
        }
    }

    suspend fun submit(body: ProtocolBody, snapshot: RoomSnapshot) {
        when (val classification = classify(body)) {
            Classification.Ignore -> Unit
            Classification.Exact -> {
                synchronized(stateLock) { exactSubmitted++ }
                work.send(Work.Exact(body, snapshot))
            }
            is Classification.Reconcile -> requestReconciliation(snapshot, classification.trigger)
        }
    }

    suspend fun reconcile(snapshot: RoomSnapshot, trigger: Trigger) {
        requestReconciliation(snapshot, trigger)
    }

    private suspend fun requestReconciliation(snapshot: RoomSnapshot, trigger: Trigger) {
        val shouldQueue =
            synchronized(stateLock) {
                reconciliationSubmitted++
                val previous = pendingReconciliation
                pendingReconciliation =
                    PendingReconciliation(
                        snapshot = snapshot,
                        desired = DesiredPlaybackState.from(snapshot),
                        triggers = previous?.triggers.orEmpty() + trigger,
                    )
                if (reconciliationQueued) {
                    reconciliationCollapsed++
                    false
                } else {
                    reconciliationQueued = true
                    true
                }
            }
        if (shouldQueue) work.send(Work.ReconcileLatest)
    }

    private suspend fun applyPendingReconciliation() {
        val pending =
            synchronized(stateLock) {
                val value = pendingReconciliation
                pendingReconciliation = null
                reconciliationQueued = false
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
            pendingReconciliation = null
            reconciliationQueued = false
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

            is ProtocolBody.QueuePreparedSetChanged ->
                Classification.Reconcile(Trigger.PREPARATION_CHANGED)
            is ProtocolBody.RoomOptionsChanged -> Classification.Reconcile(Trigger.OPTIONS_CHANGED)
            is ProtocolBody.PlaybackModeChanged ->
                Classification.Reconcile(Trigger.PLAYBACK_MODE_CHANGED)

            is ProtocolBody.QueueItemsRemoved,
            ProtocolBody.QueueCleared,
            is ProtocolBody.QueueItemPreparationRequested,
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
