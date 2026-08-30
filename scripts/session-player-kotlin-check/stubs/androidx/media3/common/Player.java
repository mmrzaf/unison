package androidx.media3.common;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public interface Player {
  int COMMAND_PLAY_PAUSE = 1;
  int COMMAND_STOP = 2;
  int COMMAND_SEEK_TO_DEFAULT_POSITION = 3;
  int COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM = 4;
  int COMMAND_SEEK_TO_MEDIA_ITEM = 5;
  int COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM = 6;
  int COMMAND_SEEK_TO_PREVIOUS = 7;
  int COMMAND_SEEK_TO_NEXT_MEDIA_ITEM = 8;
  int COMMAND_SEEK_TO_NEXT = 9;
  int COMMAND_SEEK_BACK = 10;
  int COMMAND_SEEK_FORWARD = 11;

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

  final class Commands {
    private final Set<Integer> commands;

    private Commands(Set<Integer> commands) {
      this.commands = Collections.unmodifiableSet(new LinkedHashSet<>(commands));
    }

    public boolean contains(int command) {
      return commands.contains(command);
    }

    public static final class Builder {
      private final Set<Integer> commands = new LinkedHashSet<>();

      public Builder addAllReadOnlyCommands() {
        return this;
      }

      public Builder add(int command) {
        commands.add(command);
        return this;
      }

      public Commands build() {
        return new Commands(commands);
      }
    }
  }
}
