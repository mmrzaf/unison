package androidx.media3.common;

public interface Player {
  int getCurrentMediaItemIndex();
  int getNextMediaItemIndex();
  int getPreviousMediaItemIndex();
  long getCurrentPosition();
  long getDuration();
  long getSeekBackIncrement();
  long getSeekForwardIncrement();
  long getMaxSeekToPreviousPosition();
  MediaMetadata getMediaMetadata();

  void play();
  void pause();
  void setPlayWhenReady(boolean playWhenReady);
  void stop();
  void seekTo(long positionMs);
  void seekTo(int mediaItemIndex, long positionMs);
  void seekToDefaultPosition();
  void seekToDefaultPosition(int mediaItemIndex);
  void seekBack();
  void seekForward();
  void seekToNext();
  void seekToNextMediaItem();
  void seekToPrevious();
  void seekToPreviousMediaItem();
}
