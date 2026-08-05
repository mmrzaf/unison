package com.darius.unison.ui

import com.darius.unison.library.PlaylistDetail
import com.darius.unison.model.TrackId

/** Builds one deterministic queue mutation from All Music, playlists, and direct song selections. */
internal object QueueAddSelectionPolicy {
    fun merge(
        allMusicTracks: Collection<TrackId>,
        playlists: List<PlaylistDetail>,
        directTracks: Collection<TrackId>,
    ): List<TrackId> {
        val ordered = linkedSetOf<TrackId>()
        ordered += allMusicTracks
        playlists.forEach { playlist -> playlist.tracks.forEach { ordered += it.trackId } }
        ordered += directTracks
        return ordered.toList()
    }
}
