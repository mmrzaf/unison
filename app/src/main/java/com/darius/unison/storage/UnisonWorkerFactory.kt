package com.darius.unison.storage

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.darius.unison.util.DiagnosticLog

class UnisonWorkerFactory(
    private val database: UnisonDatabase,
    private val fileStore: ManagedFileStore,
    private val roomActive: () -> Boolean,
    private val log: DiagnosticLog,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? =
        when (workerClassName) {
            CacheCleanupWorker::class.java.name ->
                CacheCleanupWorker(
                    appContext = appContext,
                    params = workerParameters,
                    database = database,
                    store = fileStore,
                    roomActive = roomActive,
                    log = log,
                )

            else -> null
        }
}
