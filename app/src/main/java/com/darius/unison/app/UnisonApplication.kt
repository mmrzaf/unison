package com.darius.unison.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.darius.unison.storage.CacheCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class UnisonApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        appScope.launch {
            val identity = container.settings.ensureIdentity()
            container.roomStore.update { it.copy(localIdentity = identity) }
        }
        runCatching {
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "temporary-track-cleanup",
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CacheCleanupWorker>(12, TimeUnit.HOURS).build(),
            )
        }.onFailure { error ->
            container.diagnostics.w("UnisonApplication", "Could not schedule local cleanup", error)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            container.artworkStore.clearMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        container.artworkStore.clearMemory()
    }
}

val Context.unisonContainer: AppContainer
    get() = (applicationContext as UnisonApplication).container
