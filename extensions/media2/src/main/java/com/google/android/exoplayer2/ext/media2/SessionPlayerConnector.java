/*
 * Copyright 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.ext.media2;

import static com.google.android.exoplayer2.util.Util.postOrRun;

import android.os.Handler;
import androidx.annotation.FloatRange;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.media.AudioAttributesCompat;
import androidx.media2.common.CallbackMediaItem;
import androidx.media2.common.FileMediaItem;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import com.google.android.exoplayer2.ControlDispatcher;
import com.google.android.exoplayer2.DefaultControlDispatcher;
import com.google.android.exoplayer2.ExoPlayerLibraryInfo;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.Util;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/**
 * An implementation of {@link SessionPlayer} that wraps a given ExoPlayer {@link Player} instance.
 *
 * <p>Internally this implementation posts operations to and receives callbacks on the thread
 * associated with {@link Player#getApplicationLooper()}, so it is important not to block this
 * thread. In particular, when awaiting the result of an asynchronous session player operation, apps
 * should generally use {@link ListenableFuture#addListener(Runnable, Executor)} to be notified of
 * completion, rather than calling the blocking {@link ListenableFuture#get()} method.
 */
public final class SessionPlayerConnector extends SessionPlayer {

  static {
    ExoPlayerLibraryInfo.registerModule("goog.exo.media2");
  }

  private static final String TAG = "SessionPlayerConnector";
  private static final boolean DEBUG = false;

  private static final int END_OF_PLAYLIST = -1;
  private final Object stateLock = new Object();

  private final Handler taskHandler;
  private final Executor taskHandlerExecutor;
  private final PlayerWrapper player;
  private final PlayerCommandQueue playerCommandQueue;

  @GuardedBy("stateLock")
  private final Map<MediaItem, Integer> mediaItemToBuffState = new HashMap<>();

  @GuardedBy("stateLock")
  /* @PlayerState */
  private int state;

  @GuardedBy("stateLock")
  private boolean closed;

  // Should be only accessed on the executor, which is currently single-threaded.
  @Nullable private MediaItem currentMediaItem;

  /**
   * Creates an instance using {@link DefaultMediaItemConverter} to convert between ExoPlayer and
   * media2 MediaItems and {@link DefaultControlDispatcher} to dispatch player commands.
   *
   * @param player The player to wrap.
   */
  public SessionPlayerConnector(Player player) {
    this(player, new DefaultMediaItemConverter());
  }

  /**
   * Creates an instance using the provided {@link ControlDispatcher} to dispatch player commands.
   *
   * @param player The player to wrap.
   * @param mediaItemConverter The {@link MediaItemConverter}.
   */
  public SessionPlayerConnector(Player player, MediaItemConverter mediaItemConverter) {
    Assertions.checkNotNull(player);
    Assertions.checkNotNull(mediaItemConverter);

    state = PLAYER_STATE_IDLE;
    taskHandler = new Handler(player.getApplicationLooper());
    taskHandlerExecutor = (runnable) -> postOrRun(taskHandler, runnable);

    this.player = new PlayerWrapper(new ExoPlayerWrapperListener(), player, mediaItemConverter);
    playerCommandQueue = new PlayerCommandQueue(this.player, taskHandler);
  }

  /**
   * Sets the {@link ControlDispatcher}.
   *
   * @param controlDispatcher The {@link ControlDispatcher}.
   */
  public void setControlDispatcher(ControlDispatcher controlDispatcher) {
    player.setControlDispatcher(controlDispatcher);
  }

  @Override
  public ListenableFuture<PlayerResult> play() {
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_PLAY, /* command= */ player::play);
  }

  @Override
  public ListenableFuture<PlayerResult> pause() {
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_PAUSE, /* command= */ player::pause);
  }

  @Override
  public ListenableFuture<PlayerResult> prepare() {
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_PREPARE, /* command= */ player::prepare);
  }

  @Override
  public ListenableFuture<PlayerResult> seekTo(long position) {
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_SEEK_TO,
        /* command= */ () -> player.seekTo(position),
        /* tag= */ position);
  }

  @Override
  public ListenableFuture<PlayerResult> setPlaybackSpeed(
      @FloatRange(from = 0.0f, to = Float.MAX_VALUE, fromInclusive = false) float playbackSpeed) {
    Assertions.checkArgument(playbackSpeed > 0f);
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_SET_SPEED,
        /* command= */ () -> {
          player.setPlaybackSpeed(playbackSpeed);
          return true;
        });
  }

  @Override
  public ListenableFuture<PlayerResult> setAudioAttributes(AudioAttributesCompat attr) {
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_SET_AUDIO_ATTRIBUTES,
        /* command= */ () -> {
          player.setAudioAttributes(Assertions.checkNotNull(attr));
          return true;
        });
  }

  @Override
  /* @PlayerState */
  public int getPlayerState() {
    synchronized (stateLock) {
      return state;
    }
  }

  @Override
  public long getCurrentPosition() {
    long position =
        runPlayerCallableBlocking(
            /* callable= */ player::getCurrentPosition,
            /* defaultValueWhenException= */ UNKNOWN_TIME);
    return position >= 0 ? position : UNKNOWN_TIME;
  }

  @Override
  public long getDuration() {
    long position =
        runPlayerCallableBlocking(
            /* callable= */ player::getDuration, /* defaultValueWhenException= */ UNKNOWN_TIME);
    return position >= 0 ? position : UNKNOWN_TIME;
  }

  @Override
  public long getBufferedPosition() {
    long position =
        runPlayerCallableBlocking(
            /* callable= */ player::getBufferedPosition,
            /* defaultValueWhenException= */ UNKNOWN_TIME);
    return position >= 0 ? position : UNKNOWN_TIME;
  }

  @Override
  /* @BuffState */
  public int getBufferingState() {
    @Nullable
    MediaItem mediaItem =
        this.<@NullableType MediaItem>runPlayerCallableBlocking(
            /* callable= */ player::getCurrentMediaItem, /* defaultValueWhenException= */ null);
    if (mediaItem == null) {
      return BUFFERING_STATE_UNKNOWN;
    }
    @Nullable Integer buffState;
    synchronized (stateLock) {
      buffState = mediaItemToBuffState.get(mediaItem);
    }
    return buffState == null ? BUFFERING_STATE_UNKNOWN : buffState;
  }

  @Override
  @FloatRange(from = 0.0f, to = Float.MAX_VALUE, fromInclusive = false)
  public float getPlaybackSpeed() {
    return runPlayerCallableBlocking(
        /* callable= */ player::getPlaybackSpeed, /* defaultValueWhenException= */ 1.0f);
  }

  @Override
  @Nullable
  public AudioAttributesCompat getAudioAttributes() {
    return runPlayerCallableBlockingWithNullOnException(/* callable= */ player::getAudioAttributes);
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@link FileMediaItem} and {@link CallbackMediaItem} are not supported.
   */
  @Override
  public ListenableFuture<PlayerResult> setMediaItem(MediaItem item) {
    Assertions.checkNotNull(item);
    Assertions.checkArgument(!(item instanceof FileMediaItem));
    Assertions.checkArgument(!(item instanceof CallbackMediaItem));
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_SET_MEDIA_ITEM, () -> player.setMediaItem(item));
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@link FileMediaItem} and {@link CallbackMediaItem} are not supported.
   */
  @Override
  public ListenableFuture<PlayerResult> setPlaylist(
      final List<MediaItem> playlist, @Nullable MediaMetadata metadata) {
    Assertions.checkNotNull(playlist);
    Assertions.checkArgument(!playlist.isEmpty());
    for (int i = 0; i < playlist.size(); i++) {
      MediaItem item = playlist.get(i);
      Assertions.checkNotNull(item);
      Assertions.checkArgument(!(item instanceof FileMediaItem));
      Assertions.checkArgument(!(item instanceof CallbackMediaItem));
      for (int j = 0; j < i; j++) {
        Assertions.checkArgument(
            item != playlist.get(j),
            "playlist shouldn't contain duplicated item, index=" + i + " vs index=" + j);
      }
    }
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_SET_PLAYLIST,
            /* command= */ () -> player.setPlaylist(playlist, metadata));
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@link FileMediaItem} and {@link CallbackMediaItem} are not supported.
   */
  @Override
  public ListenableFuture<PlayerResult> addPlaylistItem(int index, MediaItem item) {
    Assertions.checkArgument(index >= 0);
    Assertions.checkNotNull(item);
    Assertions.checkArgument(!(item instanceof FileMediaItem));
    Assertions.checkArgument(!(item instanceof CallbackMediaItem));
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM,
            /* command= */ () -> player.addPlaylistItem(index, item));
    return result;
  }

  @Override
  public ListenableFuture<PlayerResult> removePlaylistItem(@IntRange(from = 0) int index) {
    Assertions.checkArgument(index >= 0);
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM,
            /* command= */ () -> player.removePlaylistItem(index));
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>{@link FileMediaItem} and {@link CallbackMediaItem} are not supported.
   */
  @Override
  public ListenableFuture<PlayerResult> replacePlaylistItem(int index, MediaItem item) {
    Assertions.checkArgument(index >= 0);
    Assertions.checkNotNull(item);
    Assertions.checkArgument(!(item instanceof FileMediaItem));
    Assertions.checkArgument(!(item instanceof CallbackMediaItem));
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM,
            /* command= */ () -> player.replacePlaylistItem(index, item));
    return result;
  }

  @Override
  public ListenableFuture<PlayerResult> movePlaylistItem(int fromIndex, int toIndex) {
    Assertions.checkArgument(fromIndex >= 0);
    Assertions.checkArgument(toIndex >= 0);
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM,
            /* command= */ () -> player.movePlaylistItem(fromIndex, toIndex));
    return result;
  }

  @Override
  public ListenableFuture<PlayerResult> skipToPreviousPlaylistItem() {
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM,
            /* command= */ player::skipToPreviousPlaylistItem);
    result.addListener(this::notifySkipToCompletedOnHandler, taskHandlerExecutor);
    return result;
  }

  @Override
  public ListenableFuture<PlayerResult> skipToNextPlaylistItem() {
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM,
            /* command= */ player::skipToNextPlaylistItem);
    result.addListener(this::notifySkipToCompletedOnHandler, taskHandlerExecutor);
    return result;
  }

  @Override
  public ListenableFuture<PlayerResult> skipToPlaylistItem(@IntRange(from = 0) int index) {
    Assertions.checkArgument(index >= 0);
    ListenableFuture<PlayerResult> result =
        playerCommandQueue.addCommand(
            PlayerCommandQueue.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM,
            /* command= */ () -> player.skipToPlaylistItem(index));
    result.addListener(this::notifySkipToCompletedOnHandler, taskHandlerExecutor);
    return result;
  }

  @Override
  public ListenableFuture<PlayerResult> updatePlaylistMetadata(@Nullable MediaMetadata metadata) {
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA,
        /* command= */ () -> {
          boolean handled = player.updatePlaylistMetadata(metadata);
          if (handled) {
            notifySessionPlayerCallback(
                callback ->
                    callback.onPlaylistMetadataChanged(SessionPlayerConnector.this, metadata));
          }
          return handled;
        });
  }

  @Override
  public ListenableFuture<PlayerResult> setRepeatMode(int repeatMode) {
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_SET_REPEAT_MODE,
        /* command= */ () -> player.setRepeatMode(repeatMode));
  }

  @Override
  public ListenableFuture<PlayerResult> setShuffleMode(int shuffleMode) {
    return playerCommandQueue.addCommand(
        PlayerCommandQueue.COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE,
        /* command= */ () -> player.setShuffleMode(shuffleMode));
  }

  @Override
  @Nullable
  public List<MediaItem> getPlaylist() {
    return runPlayerCallableBlockingWithNullOnException(/* callable= */ player::getPlaylist);
  }

  @Override
  @Nullable
  public MediaMetadata getPlaylistMetadata() {
    return runPlayerCallableBlockingWithNullOnException(
        /* callable= */ player::getPlaylistMetadata);
  }

  @Override
  public int getRepeatMode() {
    return runPlayerCallableBlocking(
        /* callable= */ player::getRepeatMode, /* defaultValueWhenException= */ REPEAT_MODE_NONE);
  }

  @Override
  public int getShuffleMode() {
    return runPlayerCallableBlocking(
        /* callable= */ player::getShuffleMode, /* defaultValueWhenException= */ SHUFFLE_MODE_NONE);
  }

  @Override
  @Nullable
  public MediaItem getCurrentMediaItem() {
    return runPlayerCallableBlockingWithNullOnException(
        /* callable= */ player::getCurrentMediaItem);
  }

  @Override
  public int getCurrentMediaItemIndex() {
    return runPlayerCallableBlocking(
        /* callable= */ player::getCurrentMediaItemIndex,
        /* defaultValueWhenException= */ END_OF_PLAYLIST);
  }

  @Override
  public int getPreviousMediaItemIndex() {
    return runPlayerCallableBlocking(
        /* callable= */ player::getPreviousMediaItemIndex,
        /* defaultValueWhenException= */ END_OF_PLAYLIST);
  }

  @Override
  public int getNextMediaItemIndex() {
    return runPlayerCallableBlocking(
        /* callable= */ player::getNextMediaItemIndex,
        /* defaultValueWhenException= */ END_OF_PLAYLIST);
  }

  @Override
  public void close() {
    synchronized (stateLock) {
      if (closed) {
        return;
      }
      closed = true;
    }
    reset();

    this.<Void>runPlayerCallableBlocking(
        /* callable= */ () -> {
          player.close();
          return null;
        });
    super.close();
  }

  // SessionPlayerConnector-specific functions.

  /**
   * Returns whether the current media item is seekable.
   *
   * @return {@code true} if supported. {@code false} otherwise.
   */
  /* package */ boolean isCurrentMediaItemSeekable() {
    return runPlayerCallableBlocking(
        /* callable= */ player::isCurrentMediaItemSeekable, /* defaultValueWhenException= */ false);
  }

  /**
   * Returns whether {@link #skipToPlaylistItem(int)} is supported.
   *
   * @return {@code true} if supported. {@code false} otherwise.
   */
  /* package */ boolean canSkipToPlaylistItem() {
    return runPlayerCallableBlocking(
        /* callable= */ player::canSkipToPlaylistItem, /* defaultValueWhenException= */ false);
  }

  /**
   * Returns whether {@link #skipToPreviousPlaylistItem()} is supported.
   *
   * @return {@code true} if supported. {@code false} otherwise.
   */
  /* package */ boolean canSkipToPreviousPlaylistItem() {
    return runPlayerCallableBlocking(
        /* callable= */ player::canSkipToPreviousPlaylistItem,
        /* defaultValueWhenException= */ false);
  }

  /**
   * Returns whether {@link #skipToNextPlaylistItem()} is supported.
   *
   * @return {@code true} if supported. {@code false} otherwise.
   */
  /* package */ boolean canSkipToNextPlaylistItem() {
    return runPlayerCallableBlocking(
        /* callable= */ player::canSkipToNextPlaylistItem, /* defaultValueWhenException= */ false);
  }

  /**
   * Resets {@link SessionPlayerConnector} to its uninitialized state if not closed. After calling
   * this method, you will have to initialize it again by setting the media item and calling {@link
   * #prepare()}.
   *
   * <p>Note that if the player is closed, there is no way to reuse the instance.
   */
  private void reset() {
    // Cancel the pending commands.
    playerCommandQueue.reset();
    synchronized (stateLock) {
      state = PLAYER_STATE_IDLE;
      mediaItemToBuffState.clear();
    }
    this.<Void>runPlayerCallableBlocking(
        /* callable= */ () -> {
          player.reset();
          return null;
        });
  }

  private void setState(/* @PlayerState */ int state) {
    boolean needToNotify = false;
    synchronized (stateLock) {
      if (this.state != state) {
        this.state = state;
        needToNotify = true;
      }
    }
    if (needToNotify) {
      notifySessionPlayerCallback(
          callback -> callback.onPlayerStateChanged(SessionPlayerConnector.this, state));
    }
  }

  private void setBufferingState(MediaItem item, /* @BuffState */ int state) {
    @Nullable Integer previousState;
    synchronized (stateLock) {
      previousState = mediaItemToBuffState.put(item, state);
    }
    if (previousState == null || previousState != state) {
      notifySessionPlayerCallback(
          callback -> callback.onBufferingStateChanged(SessionPlayerConnector.this, item, state));
    }
  }

  private void notifySessionPlayerCallback(SessionPlayerCallbackNotifier notifier) {
    synchronized (stateLock) {
      if (closed) {
        return;
      }
    }
    List<Pair<SessionPlayer.PlayerCallback, Executor>> callbacks = getCallbacks();
    for (Pair<SessionPlayer.PlayerCallback, Executor> pair : callbacks) {
      SessionPlayer.PlayerCallback callback = Assertions.checkNotNull(pair.first);
      Executor executor = Assertions.checkNotNull(pair.second);
      executor.execute(() -> notifier.callCallback(callback));
    }
  }

  // TODO(internal b/160846312): Remove this suppress warnings and call onCurrentMediaItemChanged
  // with a null item once we depend on media2 1.2.0.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private void handlePlaylistChangedOnHandler() {
    List<MediaItem> currentPlaylist = player.getPlaylist();
    MediaMetadata playlistMetadata = player.getPlaylistMetadata();

    MediaItem currentMediaItem = player.getCurrentMediaItem();
    boolean notifyCurrentMediaItem =
        !Util.areEqual(this.currentMediaItem, currentMediaItem) && currentMediaItem != null;
    this.currentMediaItem = currentMediaItem;

    long currentPosition = getCurrentPosition();
    notifySessionPlayerCallback(
        callback -> {
          callback.onPlaylistChanged(
              SessionPlayerConnector.this, currentPlaylist, playlistMetadata);
          if (notifyCurrentMediaItem) {
            callback.onCurrentMediaItemChanged(SessionPlayerConnector.this, currentMediaItem);
          }
        });
  }

  private void notifySkipToCompletedOnHandler() {
    MediaItem currentMediaItem = Assertions.checkNotNull(player.getCurrentMediaItem());
    if (Util.areEqual(this.currentMediaItem, currentMediaItem)) {
      return;
    }
    this.currentMediaItem = currentMediaItem;
    long currentPosition = getCurrentPosition();
    notifySessionPlayerCallback(
        callback -> {
          callback.onCurrentMediaItemChanged(SessionPlayerConnector.this, currentMediaItem);
        });
  }

  private <T> T runPlayerCallableBlocking(Callable<T> callable) {
    SettableFuture<T> future = SettableFuture.create();
    boolean success =
        postOrRun(
            taskHandler,
            () -> {
              try {
                future.set(callable.call());
              } catch (Throwable e) {
                future.setException(e);
              }
            });
    Assertions.checkState(success);
    boolean wasInterrupted = false;
    try {
      while (true) {
        try {
          return future.get();
        } catch (InterruptedException e) {
          // We always wait for player calls to return.
          wasInterrupted = true;
        } catch (ExecutionException e) {
          if (DEBUG) {
            Log.d(TAG, "Internal player error", e);
          }
          throw new IllegalStateException(e.getCause());
        }
      }
    } finally {
      if (wasInterrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Nullable
  private <T> T runPlayerCallableBlockingWithNullOnException(Callable<@NullableType T> callable) {
    try {
      return runPlayerCallableBlocking(callable);
    } catch (Exception e) {
      return null;
    }
  }

  private <T> T runPlayerCallableBlocking(Callable<T> callable, T defaultValueWhenException) {
    try {
      return runPlayerCallableBlocking(callable);
    } catch (Exception e) {
      return defaultValueWhenException;
    }
  }

  private interface SessionPlayerCallbackNotifier {
    void callCallback(SessionPlayer.PlayerCallback callback);
  }

  private final class ExoPlayerWrapperListener implements PlayerWrapper.Listener {
    @Override
    public void onPlayerStateChanged(int playerState) {
      setState(playerState);
      if (playerState == PLAYER_STATE_PLAYING) {
        playerCommandQueue.notifyCommandCompleted(PlayerCommandQueue.COMMAND_CODE_PLAYER_PLAY);
      } else if (playerState == PLAYER_STATE_PAUSED) {
        playerCommandQueue.notifyCommandCompleted(PlayerCommandQueue.COMMAND_CODE_PLAYER_PAUSE);
      }
    }

    @Override
    public void onPrepared(MediaItem mediaItem, int bufferingPercentage) {
      Assertions.checkNotNull(mediaItem);

      if (bufferingPercentage >= 100) {
        setBufferingState(mediaItem, BUFFERING_STATE_COMPLETE);
      } else {
        setBufferingState(mediaItem, BUFFERING_STATE_BUFFERING_AND_PLAYABLE);
      }
      playerCommandQueue.notifyCommandCompleted(PlayerCommandQueue.COMMAND_CODE_PLAYER_PREPARE);
    }

    @Override
    public void onSeekCompleted() {
      long currentPosition = getCurrentPosition();
      notifySessionPlayerCallback(
          callback -> callback.onSeekCompleted(SessionPlayerConnector.this, currentPosition));
    }

    @Override
    public void onBufferingStarted(MediaItem mediaItem) {
      setBufferingState(mediaItem, BUFFERING_STATE_BUFFERING_AND_STARVED);
    }

    @Override
    public void onBufferingUpdate(MediaItem mediaItem, int bufferingPercentage) {
      if (bufferingPercentage >= 100) {
        setBufferingState(mediaItem, BUFFERING_STATE_COMPLETE);
      }
    }

    @Override
    public void onBufferingEnded(MediaItem mediaItem, int bufferingPercentage) {
      if (bufferingPercentage >= 100) {
        setBufferingState(mediaItem, BUFFERING_STATE_COMPLETE);
      } else {
        setBufferingState(mediaItem, BUFFERING_STATE_BUFFERING_AND_PLAYABLE);
      }
    }

    @Override
    public void onCurrentMediaItemChanged(MediaItem mediaItem) {
      if (Util.areEqual(currentMediaItem, mediaItem)) {
        return;
      }
      currentMediaItem = mediaItem;
      long currentPosition = getCurrentPosition();
      notifySessionPlayerCallback(
          callback -> {
            callback.onCurrentMediaItemChanged(SessionPlayerConnector.this, mediaItem);
          });
    }

    @Override
    public void onPlaybackEnded() {
      notifySessionPlayerCallback(
          callback -> callback.onPlaybackCompleted(SessionPlayerConnector.this));
    }

    @Override
    public void onError(@Nullable MediaItem mediaItem) {
      playerCommandQueue.notifyCommandError();
      if (mediaItem != null) {
        setBufferingState(mediaItem, BUFFERING_STATE_UNKNOWN);
      }
    }

    @Override
    public void onPlaylistChanged() {
      handlePlaylistChangedOnHandler();
    }

    @Override
    public void onShuffleModeChanged(int shuffleMode) {
      notifySessionPlayerCallback(
          callback -> callback.onShuffleModeChanged(SessionPlayerConnector.this, shuffleMode));
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
      notifySessionPlayerCallback(
          callback -> callback.onRepeatModeChanged(SessionPlayerConnector.this, repeatMode));
    }

    @Override
    public void onPlaybackSpeedChanged(float playbackSpeed) {
      notifySessionPlayerCallback(
          callback -> callback.onPlaybackSpeedChanged(SessionPlayerConnector.this, playbackSpeed));
    }

    @Override
    public void onAudioAttributesChanged(AudioAttributesCompat audioAttributes) {
      notifySessionPlayerCallback(
          callback ->
              callback.onAudioAttributesChanged(SessionPlayerConnector.this, audioAttributes));
    }
  }
}
