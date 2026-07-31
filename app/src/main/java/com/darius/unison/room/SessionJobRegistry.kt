package com.darius.unison.room

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns all work tied to one room generation. Advancing the generation makes every callback from the
 * previous room stale before cancellation begins, then joins tracked jobs within a deadline.
 */
internal class SessionJobRegistry(private val scope: CoroutineScope) {
    private val generationCounter = AtomicLong(0L)
    private val jobs = ConcurrentHashMap.newKeySet<Job>()

    val generation: Long
        get() = generationCounter.get()

    val activeJobCount: Int
        get() = jobs.count { !it.isCompleted }

    fun isCurrent(candidate: Long): Boolean = candidate == generation

    fun launch(block: suspend CoroutineScope.(generation: Long) -> Unit): Job {
        val capturedGeneration = generation
        lateinit var job: Job
        job = scope.launch {
            if (isCurrent(capturedGeneration)) block(capturedGeneration)
        }
        jobs.add(job)
        job.invokeOnCompletion { jobs.remove(job) }
        return job
    }

    suspend fun advanceAndCancel(timeoutMs: Long): Long {
        val nextGeneration = generationCounter.incrementAndGet()
        val current = currentCoroutineContext()[Job]
        val closing = jobs.toList().filterNot { it === current }
        closing.forEach(Job::cancel)
        withTimeoutOrNull(timeoutMs) {
            closing.forEach { it.join() }
        }
        jobs.removeIf(Job::isCompleted)
        return nextGeneration
    }

    fun advanceAndCancelNow(): Long {
        val nextGeneration = generationCounter.incrementAndGet()
        jobs.toList().forEach(Job::cancel)
        jobs.removeIf(Job::isCompleted)
        return nextGeneration
    }
}
