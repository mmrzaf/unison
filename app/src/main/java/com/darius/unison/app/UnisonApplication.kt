package com.darius.unison.app

import android.app.Application
import android.content.Context
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.darius.unison.storage.CacheCleanupWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class UnisonApplication : Application(), Configuration.Provider {
    // Android creates the application, requests WorkManager configuration, and calls onCreate on
    // the main thread before services or ViewModels can access this graph. NONE avoids a monitor in
    // first composition; onCreate forces initialization before launching any background consumer.
    val container: AppContainer by lazy(LazyThreadSafetyMode.NONE) { AppContainer(this) }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(container.workerFactory).build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val appContainer = container
        appScope.launch {
            appContainer.persistedUriPermissions.releaseAllUnused()
            val identity = appContainer.settings.ensureIdentity()
            appContainer.roomStore.update { it.copy(localIdentity = identity) }
        }
        // WorkManager initialization and cleanup scheduling are maintenance, not launch-critical
        // work. Deferring both prevents first-frame contention on slower devices while still
        // guaranteeing that every normally-lived process registers the periodic task.
        appScope.launch {
            delay(CLEANUP_SCHEDULING_DELAY_MS)
            runCatching {
                    val constraints =
                        Constraints.Builder()
                            .setRequiresBatteryNotLow(true)
                            .setRequiresStorageNotLow(true)
                            .build()
                    val request =
                        PeriodicWorkRequestBuilder<CacheCleanupWorker>(12, TimeUnit.HOURS)
                            .setInitialDelay(CLEANUP_INITIAL_DELAY_HOURS, TimeUnit.HOURS)
                            .setConstraints(constraints)
                            .build()
                    WorkManager.getInstance(this@UnisonApplication)
                        .enqueueUniquePeriodicWork(
                            "temporary-track-cleanup",
                            ExistingPeriodicWorkPolicy.UPDATE,
                            request,
                        )
                }
                .onFailure { error ->
                    appContainer.diagnostics.w(
                        "UnisonApplication",
                        "Could not schedule local cleanup",
                        error,
                    )
                }
        }
    }

    private companion object {
        const val CLEANUP_SCHEDULING_DELAY_MS = 30_000L
        const val CLEANUP_INITIAL_DELAY_HOURS = 1L
    }
}

val Context.unisonContainer: AppContainer
    get() = (applicationContext as UnisonApplication).container
