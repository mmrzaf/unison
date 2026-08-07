package com.darius.unison.app

import android.content.Context
import com.darius.unison.library.ImportManager
import com.darius.unison.library.PersistedUriPermissionManager
import com.darius.unison.library.PlaylistRepository
import com.darius.unison.library.TrackRepository
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.storage.UnisonDatabase
import com.darius.unison.storage.UnisonWorkerFactory
import com.darius.unison.util.DiagnosticLog

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database = UnisonDatabase.create(appContext)
    val fileStore = ManagedFileStore(appContext)
    val settings = UnisonSettings(appContext)
    val roomStore = RoomStore()
    val diagnostics = DiagnosticLog(appContext)
    val workerFactory =
        UnisonWorkerFactory(
            database,
            fileStore,
            roomActive = { roomStore.structure.value.snapshot != null },
            log = diagnostics,
        )
    val roomCommandBus = RoomCommandBus()
    val trackRepository = TrackRepository(appContext, database, fileStore, diagnostics)
    val playlistRepository = PlaylistRepository(database, trackRepository)
    val persistedUriPermissions = PersistedUriPermissionManager(appContext.contentResolver)
    val importManager = ImportManager(appContext, trackRepository, playlistRepository)
}
