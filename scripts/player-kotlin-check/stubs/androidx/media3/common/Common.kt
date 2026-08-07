package androidx.media3.common

import android.net.Uri

object C {
    const val USAGE_MEDIA: Int = 1
    const val AUDIO_CONTENT_TYPE_MUSIC: Int = 2
    const val WAKE_MODE_LOCAL: Int = 1
    const val TIME_UNSET: Long = -9223372036854775807L
}
class AudioAttributes private constructor() {
    class Builder {
        fun setUsage(value: Int): Builder = this
        fun setContentType(value: Int): Builder = this
        fun build(): AudioAttributes = AudioAttributes()
    }
}

open class PlaybackException(
    val errorCodeName: String = "ERROR",
    val errorCode: Int = 0,
    message: String? = null,
) : RuntimeException(message)

data class PlaybackParameters(val speed: Float = 1f)

open class MediaMetadata(
    val title: CharSequence? = null,
    val artist: CharSequence? = null,
    val albumTitle: CharSequence? = null,
    val displayTitle: CharSequence? = null,
    val subtitle: CharSequence? = null,
) {
    class Builder {
        private var title: CharSequence? = null
        private var artist: CharSequence? = null
        private var albumTitle: CharSequence? = null
        private var displayTitle: CharSequence? = null
        private var subtitle: CharSequence? = null
        fun setTitle(value: CharSequence?): Builder = apply { title = value }
        fun setArtist(value: CharSequence?): Builder = apply { artist = value }
        fun setAlbumTitle(value: CharSequence?): Builder = apply { albumTitle = value }
        fun setDisplayTitle(value: CharSequence?): Builder = apply { displayTitle = value }
        fun setSubtitle(value: CharSequence?): Builder = apply { subtitle = value }
        fun build(): MediaMetadata = MediaMetadata(title, artist, albumTitle, displayTitle, subtitle)
    }
}

open class MediaItem(
    val mediaId: String = "",
    val mediaMetadata: MediaMetadata = MediaMetadata(),
) {
    class Builder {
        private var mediaId: String = ""
        private var metadata: MediaMetadata = MediaMetadata()
        fun setMediaId(value: String): Builder = apply { mediaId = value }
        fun setUri(value: Uri): Builder = this
        fun setMediaMetadata(value: MediaMetadata): Builder = apply { metadata = value }
        fun setMimeType(value: String): Builder = this
        fun build(): MediaItem = MediaItem(mediaId, metadata)
    }
}

interface Player {
    val currentMediaItem: MediaItem?
    val currentMediaItemIndex: Int
    val nextMediaItemIndex: Int
    val previousMediaItemIndex: Int
    val currentPosition: Long
    val duration: Long
    val seekBackIncrement: Long
    val seekForwardIncrement: Long
    val maxSeekToPreviousPosition: Long
    val mediaMetadata: MediaMetadata
    val playbackState: Int
    val isPlaying: Boolean
    var playWhenReady: Boolean
    var repeatMode: Int
    val playbackParameters: PlaybackParameters
    val playerError: PlaybackException?
    val mediaItemCount: Int

    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun seekTo(mediaItemIndex: Int, positionMs: Long)
    fun seekToDefaultPosition()
    fun seekToDefaultPosition(mediaItemIndex: Int)
    fun seekBack()
    fun seekForward()
    fun seekToNext()
    fun seekToNextMediaItem()
    fun seekToPrevious()
    fun seekToPreviousMediaItem()

    interface Listener {
        fun onEvents(player: Player, events: Events) = Unit
        fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = Unit
        fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) = Unit
        fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = Unit
        fun onPlayerError(error: PlaybackException) = Unit
    }
    class Events

    companion object {
        const val STATE_IDLE = 1
        const val STATE_BUFFERING = 2
        const val STATE_READY = 3
        const val STATE_ENDED = 4
        const val REPEAT_MODE_OFF = 0
        const val REPEAT_MODE_ONE = 1
        const val PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST = 1
        const val PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS = 2
        const val PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY = 3
        const val PLAY_WHEN_READY_CHANGE_REASON_REMOTE = 4
        const val PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM = 5
        const val PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG = 6
        const val PLAYBACK_SUPPRESSION_REASON_NONE = 0
        const val PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS = 1
        const val PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT = 3
        const val PLAYBACK_SUPPRESSION_REASON_SCRUBBING = 4
        const val MEDIA_ITEM_TRANSITION_REASON_REPEAT = 0
        const val MEDIA_ITEM_TRANSITION_REASON_AUTO = 1
        const val MEDIA_ITEM_TRANSITION_REASON_SEEK = 2
        const val MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED = 3
    }
}
