package androidx.media3.common;

public class ForwardingPlayer implements Player {
  protected final Player player;
  public ForwardingPlayer(Player player) { this.player = player; }
  public int getCurrentMediaItemIndex() { return player.getCurrentMediaItemIndex(); }
  public int getNextMediaItemIndex() { return player.getNextMediaItemIndex(); }
  public int getPreviousMediaItemIndex() { return player.getPreviousMediaItemIndex(); }
  public long getCurrentPosition() { return player.getCurrentPosition(); }
  public long getDuration() { return player.getDuration(); }
  public long getSeekBackIncrement() { return player.getSeekBackIncrement(); }
  public long getSeekForwardIncrement() { return player.getSeekForwardIncrement(); }
  public long getMaxSeekToPreviousPosition() { return player.getMaxSeekToPreviousPosition(); }
  public MediaMetadata getMediaMetadata() { return player.getMediaMetadata(); }
  public void play() { player.play(); }
  public void pause() { player.pause(); }
  public void setPlayWhenReady(boolean value) { player.setPlayWhenReady(value); }
  public void stop() { player.stop(); }
  public void seekTo(long value) { player.seekTo(value); }
  public void seekTo(int index, long value) { player.seekTo(index, value); }
  public void seekToDefaultPosition() { player.seekToDefaultPosition(); }
  public void seekToDefaultPosition(int index) { player.seekToDefaultPosition(index); }
  public void seekBack() { player.seekBack(); }
  public void seekForward() { player.seekForward(); }
  public void seekToNext() { player.seekToNext(); }
  public void seekToNextMediaItem() { player.seekToNextMediaItem(); }
  public void seekToPrevious() { player.seekToPrevious(); }
  public void seekToPreviousMediaItem() { player.seekToPreviousMediaItem(); }
}
