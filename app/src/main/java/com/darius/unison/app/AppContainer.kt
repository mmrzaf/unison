package com.darius.unison.app

import android.content.Context
import com.darius.unison.library.ImportManager
import com.darius.unison.library.PlaylistRepository
import com.darius.unison.library.TrackRepository
import com.darius.unison.storage.ArtworkStore
import com.darius.unison.storage.ManagedFileStore
import com.darius.unison.storage.UnisonDatabase
import com.darius.unison.util.DiagnosticLog

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database = UnisonDatabase.create(appContext)
    val fileStore = ManagedFileStore(appContext)
    val artworkStore = ArtworkStore(appContext.cacheDir)
    val settings = UnisonSettings(appContext)
    val roomStore = RoomStore()
    val roomCommandBus = RoomCommandBus()
    val diagnostics = DiagnosticLog(appContext)
    val trackRepository = TrackRepository(appContext, database, fileStore)
    val playlistRepository = PlaylistRepository(database, trackRepository)
    val importManager = ImportManager(appContext, trackRepository, playlistRepository)
}
