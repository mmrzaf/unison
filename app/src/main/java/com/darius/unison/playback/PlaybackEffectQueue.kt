package com.darius.unison.playback

import com.darius.unison.model.RoomSnapshot
import com.darius.unison.protocol.ProtocolBody
import com.darius.unison.util.MonotonicClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Ordered player-effect queue. Room state (media readiness, the timeline window, transfer demands)
 * is prepared by the room actor; this queue only turns already-decided mutations into Media3 work.
 *
 * Exact transport operations remain ordered. Replaceable reconciliation work is represented by one
 * queued token and executes the repeat-mode maintenance. Submission never suspends and never drops
 * work after a canonical commit: the queue is unbounded because its only producer is the room
 * actor, whose exact commands are bounded by user and coordinator commands.
 */
class PlaybackEffectQueue(
    scope: CoroutineScope,
    private val clock: MonotonicClock,
    private val applyExact: suspend (ProtocolBody, RoomSnapshot, ExactContext) -> Unit,
    private val reconcileLatest: suspend (PlaybackReconciliation) -> Unit,
    private val onFailure: (ProtocolBody?, Throwable, Timing) -> Unit,
    private val onTiming: (Timing) -> Unit = {},
    private val preparedQueueItemIds: () -> Set<com.darius.unison.model.QueueItemId> = {
        emptySet()
    },
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

    enum class WorkKind {
        EXACT,
        RECONCILIATION,
    }

    data class Timing(
        val kind: WorkKind,
        val mutationType: String?,
        val submissionToStartNs: Long,
        val applyDurationNs: Long,
    )

    /** Everything the player needs to know, decided by the room actor. */
    data class ExactContext(
        val executable: Boolean,
        val coordinator: Boolean,
    )

    data class PlaybackReconciliation(
        val snapshot: RoomSnapshot,
        val key: PlaybackReconciliationKey,
        val triggers: Set<Trigger>,
    )

    enum class Trigger {
        QUEUE_CHANGED,
        PREPARATION_CHANGED,
        PLAYBACK_MODE_CHANGED,
    }

    private sealed interface Work {
        val submittedNs: Long

        data class Exact(
            val body: ProtocolBody,
            val snapshot: RoomSnapshot,
            val context: ExactContext,
            override val submittedNs: Long,
        ) : Work

        data class Reconcile(
            val batchId: Long,
            override val submittedNs: Long,
        ) : Work
    }

    private data class PendingReconciliation(
        val snapshot: RoomSnapshot,
        val key: PlaybackReconciliationKey,
        val triggers: Set<Trigger>,
    )

    private val stateLock = Any()
    private val work = Channel<Work>(Channel.UNLIMITED)
    private val pendingReconciliations = mutableMapOf<Long, PendingReconciliation>()
    private var openReconciliationBatchId: Long? = null
    private var nextReconciliationBatchId = 0L
    private var lastAppliedKey: PlaybackReconciliationKey? = null
    private var exactSubmitted = 0L
    private var exactApplied = 0L
    private var reconciliationSubmitted = 0L
    private var reconciliationCollapsed = 0L
    private var reconciliationApplied = 0L
    private var reconciliationSkipped = 0L
    private var failures = 0L
    private val worker: Job = scope.launch {
        for (item in work) {
            val startedNs = clock.nowNs()
            val submissionToStartNs = (startedNs - item.submittedNs).coerceAtLeast(0L)
            try {
                when (item) {
                    is Work.Exact -> {
                        applyExact(item.body, item.snapshot, item.context)
                        synchronized(stateLock) { exactApplied++ }
                    }
                    is Work.Reconcile -> applyPendingReconciliation(item.batchId)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                synchronized(stateLock) { failures++ }
                val failureTiming =
                    Timing(
                        kind = if (item is Work.Exact) WorkKind.EXACT else WorkKind.RECONCILIATION,
                        mutationType = (item as? Work.Exact)?.body?.let { it::class.simpleName },
                        submissionToStartNs = submissionToStartNs,
                        applyDurationNs = (clock.nowNs() - startedNs).coerceAtLeast(0L),
                    )
                onFailure((item as? Work.Exact)?.body, error, failureTiming)
            } finally {
                val applyDurationNs = (clock.nowNs() - startedNs).coerceAtLeast(0L)
                runCatching {
                    onTiming(
                        Timing(
                            kind =
                                if (item is Work.Exact) WorkKind.EXACT else WorkKind.RECONCILIATION,
                            mutationType =
                                (item as? Work.Exact)?.body?.let { it::class.simpleName },
                            submissionToStartNs = submissionToStartNs,
                            applyDurationNs = applyDurationNs,
                        )
                    )
                }
            }
        }
    }

    fun submit(
        body: ProtocolBody,
        snapshot: RoomSnapshot,
        context: ExactContext,
    ) {
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
                work.trySend(Work.Exact(body, snapshot, context, clock.nowNs()))
            }
            is Classification.Reconcile -> requestReconciliation(snapshot, classification.trigger)
        }
    }

    fun reconcile(snapshot: RoomSnapshot, trigger: Trigger) {
        requestReconciliation(snapshot, trigger)
    }

    private fun requestReconciliation(snapshot: RoomSnapshot, trigger: Trigger) {
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
                            key = PlaybackReconciliationKey.from(snapshot, preparedQueueItemIds()),
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
                            key = PlaybackReconciliationKey.from(snapshot, preparedQueueItemIds()),
                            triggers = setOf(trigger),
                        )
                    true
                }
            }
        if (shouldQueue) work.trySend(Work.Reconcile(batchId, clock.nowNs()))
    }

    private suspend fun applyPendingReconciliation(batchId: Long) {
        val pending =
            synchronized(stateLock) {
                val value = pendingReconciliations.remove(batchId)
                if (openReconciliationBatchId == batchId) openReconciliationBatchId = null
                value
            } ?: return

        if (lastAppliedKey == pending.key) {
            synchronized(stateLock) { reconciliationSkipped++ }
            return
        }
        reconcileLatest(
            PlaybackReconciliation(
                snapshot = pending.snapshot,
                key = pending.key,
                triggers = pending.triggers,
            )
        )
        lastAppliedKey = pending.key
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

            is ProtocolBody.QueueShuffled -> Classification.Reconcile(Trigger.QUEUE_CHANGED)
            is ProtocolBody.RepeatModeChanged ->
                Classification.Reconcile(Trigger.PLAYBACK_MODE_CHANGED)

            is ProtocolBody.QueueItemsRemoved,
            ProtocolBody.QueueCleared,
            is ProtocolBody.PlayScheduled,
            is ProtocolBody.PauseScheduled,
            is ProtocolBody.SeekScheduled,
            is ProtocolBody.CurrentItemChanged -> Classification.Exact

            else -> Classification.Ignore
        }
}
