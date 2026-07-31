package androidx.media3.exoplayer

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player

open class ExoPlayer : Player {
    class Builder(context: Context) {
        fun setAudioAttributes(attributes: AudioAttributes, handleAudioFocus: Boolean): Builder = this
        fun setHandleAudioBecomingNoisy(value: Boolean): Builder = this
        fun setWakeMode(value: Int): Builder = this
        fun build(): ExoPlayer = ExoPlayer()
    }

    private val items = mutableListOf<MediaItem>()
    private val listeners = mutableListOf<Player.Listener>()
    override var currentMediaItemIndex: Int = 0
    override val nextMediaItemIndex: Int get() = (currentMediaItemIndex + 1).takeIf { it < items.size } ?: -1
    override val previousMediaItemIndex: Int get() = (currentMediaItemIndex - 1).takeIf { it >= 0 } ?: -1
    override val currentMediaItem: MediaItem? get() = items.getOrNull(currentMediaItemIndex)
    override var currentPosition: Long = 0L
    override var duration: Long = 0L
    override val seekBackIncrement: Long = 5_000L
    override val seekForwardIncrement: Long = 5_000L
    override val maxSeekToPreviousPosition: Long = 3_000L
    override val mediaMetadata: MediaMetadata get() = currentMediaItem?.mediaMetadata ?: MediaMetadata()
    override var playbackState: Int = Player.STATE_IDLE
    override var isPlaying: Boolean = false
    override var playWhenReady: Boolean = false
    override var repeatMode: Int = Player.REPEAT_MODE_OFF
    override var playbackParameters: PlaybackParameters = PlaybackParameters()
    override var playerError: PlaybackException? = null
    override val mediaItemCount: Int get() = items.size

    fun addListener(listener: Player.Listener) { listeners += listener }
    fun removeListener(listener: Player.Listener) { listeners -= listener }
    fun release() = Unit
    fun prepare() { playbackState = Player.STATE_READY }
    fun clearMediaItems() { items.clear(); currentMediaItemIndex = 0 }
    fun getMediaItemAt(index: Int): MediaItem = items[index]
    fun removeMediaItem(index: Int) { items.removeAt(index) }
    fun moveMediaItem(fromIndex: Int, toIndex: Int) { items.add(toIndex, items.removeAt(fromIndex)) }
    fun addMediaItem(index: Int, item: MediaItem) { items.add(index, item) }
    fun setMediaItems(values: List<MediaItem>, startIndex: Int, startPositionMs: Long) {
        items.clear(); items.addAll(values); currentMediaItemIndex = startIndex; currentPosition = startPositionMs
    }
    fun setPlaybackSpeed(speed: Float) { playbackParameters = PlaybackParameters(speed) }

    override fun play() { playWhenReady = true; isPlaying = true }
    override fun pause() { playWhenReady = false; isPlaying = false }
    override fun stop() { playbackState = Player.STATE_IDLE; isPlaying = false }
    override fun seekTo(positionMs: Long) { currentPosition = positionMs }
    override fun seekTo(mediaItemIndex: Int, positionMs: Long) { currentMediaItemIndex = mediaItemIndex; currentPosition = positionMs }
    override fun seekToDefaultPosition() = seekTo(0L)
    override fun seekToDefaultPosition(mediaItemIndex: Int) = seekTo(mediaItemIndex, 0L)
    override fun seekBack() = seekTo((currentPosition - seekBackIncrement).coerceAtLeast(0L))
    override fun seekForward() = seekTo(currentPosition + seekForwardIncrement)
    override fun seekToNext() = Unit
    override fun seekToNextMediaItem() = Unit
    override fun seekToPrevious() = Unit
    override fun seekToPreviousMediaItem() = Unit
}
