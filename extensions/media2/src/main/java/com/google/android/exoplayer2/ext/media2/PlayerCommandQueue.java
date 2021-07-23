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
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Handler;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.SessionPlayer;
import androidx.media2.common.SessionPlayer.PlayerResult;
import com.google.android.exoplayer2.util.Log;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Callable;

/** Manages the queue of player actions and handles running them one by one. */
/* package */ class PlayerCommandQueue {

  private static final String TAG = "PlayerCommandQueue";
  private static final boolean DEBUG = false;

  // Redefine command codes rather than using constants from SessionCommand here, because command
  // code for setAudioAttribute() is missing in SessionCommand.
  /** Command code for {@link SessionPlayer#setAudioAttributes}. */
  public static final int COMMAND_CODE_PLAYER_SET_AUDIO_ATTRIBUTES = 0;

  /** Command code for {@link SessionPlayer#play} */
  public static final int COMMAND_CODE_PLAYER_PLAY = 1;

  /** Command code for {@link SessionPlayer#replacePlaylistItem(int, MediaItem)} */
  public static final int COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM = 2;

  /** Command code for {@link SessionPlayer#skipToPreviousPlaylistItem()} */
  public static final int COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM = 3;

  /** Command code for {@link SessionPlayer#skipToNextPlaylistItem()} */
  public static final int COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM = 4;

  /** Command code for {@link SessionPlayer#skipToPlaylistItem(int)} */
  public static final int COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM = 5;

  /** Command code for {@link SessionPlayer#updatePlaylistMetadata(MediaMetadata)} */
  public static final int COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA = 6;

  /** Command code for {@link SessionPlayer#setRepeatMode(int)} */
  public static final int COMMAND_CODE_PLAYER_SET_REPEAT_MODE = 7;

  /** Command code for {@link SessionPlayer#setShuffleMode(int)} */
  public static final int COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE = 8;

  /** Command code for {@link SessionPlayer#setMediaItem(MediaItem)} */
  public static final int COMMAND_CODE_PLAYER_SET_MEDIA_ITEM = 9;

  /** Command code for {@link SessionPlayer#seekTo(long)} */
  public static final int COMMAND_CODE_PLAYER_SEEK_TO = 10;

  /** Command code for {@link SessionPlayer#prepare()} */
  public static final int COMMAND_CODE_PLAYER_PREPARE = 11;

  /** Command code for {@link SessionPlayer#setPlaybackSpeed(float)} */
  public static final int COMMAND_CODE_PLAYER_SET_SPEED = 12;

  /** Command code for {@link SessionPlayer#pause()} */
  public static final int COMMAND_CODE_PLAYER_PAUSE = 13;

  /** Command code for {@link SessionPlayer#setPlaylist(List, MediaMetadata)} */
  public static final int COMMAND_CODE_PLAYER_SET_PLAYLIST = 14;

  /** Command code for {@link SessionPlayer#addPlaylistItem(int, MediaItem)} */
  public static final int COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM = 15;

  /** Command code for {@link SessionPlayer#removePlaylistItem(int)} */
  public static final int COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM = 16;

  /** Command code for {@link SessionPlayer#movePlaylistItem(int, int)} */
  public static final int COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM = 17;

  /** List of session commands whose result would be set after the command is finished. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      value = {
        COMMAND_CODE_PLAYER_SET_AUDIO_ATTRIBUTES,
        COMMAND_CODE_PLAYER_PLAY,
        COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM,
        COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM,
        COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM,
        COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM,
        COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA,
        COMMAND_CODE_PLAYER_SET_REPEAT_MODE,
        COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE,
        COMMAND_CODE_PLAYER_SET_MEDIA_ITEM,
        COMMAND_CODE_PLAYER_SEEK_TO,
        COMMAND_CODE_PLAYER_PREPARE,
        COMMAND_CODE_PLAYER_SET_SPEED,
        COMMAND_CODE_PLAYER_PAUSE,
        COMMAND_CODE_PLAYER_SET_PLAYLIST,
        COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM,
        COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM,
        COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM,
      })
  public @interface CommandCode {}

  /** Command whose result would be set later via listener after the command is finished. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
      value = {COMMAND_CODE_PLAYER_PREPARE, COMMAND_CODE_PLAYER_PLAY, COMMAND_CODE_PLAYER_PAUSE})
  public @interface AsyncCommandCode {}

  // Should be only used on the handler.
  private final PlayerWrapper player;
  private final Handler handler;
  private final Object lock;

  @GuardedBy("lock")
  private final Deque<PlayerCommand> pendingPlayerCommandQueue;

  // Should be only used on the handler.
  @Nullable private AsyncPlayerCommandResult pendingAsyncPlayerCommandResult;

  public PlayerCommandQueue(PlayerWrapper player, Handler handler) {
    this.player = player;
    this.handler = handler;
    lock = new Object();
    pendingPlayerCommandQueue = new ArrayDeque<>();
  }

  public void reset() {
    handler.removeCallbacksAndMessages(/* token= */ null);
    List<PlayerCommand> queue;
    synchronized (lock) {
      queue = new ArrayList<>(pendingPlayerCommandQueue);
      pendingPlayerCommandQueue.clear();
    }
    for (PlayerCommand playerCommand : queue) {
      playerCommand.result.set(
          new PlayerResult(PlayerResult.RESULT_INFO_SKIPPED, /* item= */ null));
    }
  }

  public ListenableFuture<PlayerResult> addCommand(
      @CommandCode int commandCode, Callable<Boolean> command) {
    return addCommand(commandCode, command, /* tag= */ null);
  }

  public ListenableFuture<PlayerResult> addCommand(
      @CommandCode int commandCode, Callable<Boolean> command, @Nullable Object tag) {
    SettableFuture<PlayerResult> result = SettableFuture.create();
    synchronized (lock) {
      PlayerCommand playerCommand = new PlayerCommand(commandCode, command, result, tag);
      result.addListener(
          () -> {
            if (result.isCancelled()) {
              boolean isCommandPending;
              synchronized (lock) {
                isCommandPending = pendingPlayerCommandQueue.remove(playerCommand);
              }
              if (isCommandPending) {
                result.set(
                    new PlayerResult(
                        PlayerResult.RESULT_INFO_SKIPPED, player.getCurrentMediaItem()));
                if (DEBUG) {
                  Log.d(TAG, "canceled " + playerCommand);
                }
              }
              if (pendingAsyncPlayerCommandResult != null
                  && pendingAsyncPlayerCommandResult.result == result) {
                pendingAsyncPlayerCommandResult = null;
              }
            }
            processPendingCommandOnHandler();
          },
          (runnable) -> postOrRun(handler, runnable));
      if (DEBUG) {
        Log.d(TAG, "adding " + playerCommand);
      }
      pendingPlayerCommandQueue.add(playerCommand);
    }
    processPendingCommand();
    return result;
  }

  public void notifyCommandError() {
    postOrRun(
        handler,
        () -> {
          @Nullable AsyncPlayerCommandResult pendingResult = pendingAsyncPlayerCommandResult;
          if (pendingResult == null) {
            if (DEBUG) {
              Log.d(TAG, "Ignoring notifyCommandError(). No pending async command.");
            }
            return;
          }
          pendingResult.result.set(
              new PlayerResult(PlayerResult.RESULT_ERROR_UNKNOWN, player.getCurrentMediaItem()));
          pendingAsyncPlayerCommandResult = null;
          if (DEBUG) {
            Log.d(TAG, "error on " + pendingResult);
          }
          processPendingCommandOnHandler();
        });
  }

  public void notifyCommandCompleted(@AsyncCommandCode int completedCommandCode) {
    if (DEBUG) {
      Log.d(TAG, "notifyCommandCompleted, completedCommandCode=" + completedCommandCode);
    }
    postOrRun(
        handler,
        () -> {
          @Nullable AsyncPlayerCommandResult pendingResult = pendingAsyncPlayerCommandResult;
          if (pendingResult == null || pendingResult.commandCode != completedCommandCode) {
            if (DEBUG) {
              Log.d(
                  TAG,
                  "Unexpected Listener is notified from the Player. Player may be used"
                      + " directly rather than "
                      + toLogFriendlyString(completedCommandCode));
            }
            return;
          }
          pendingResult.result.set(
              new PlayerResult(PlayerResult.RESULT_SUCCESS, player.getCurrentMediaItem()));
          pendingAsyncPlayerCommandResult = null;
          if (DEBUG) {
            Log.d(TAG, "completed " + pendingResult);
          }
          processPendingCommandOnHandler();
        });
  }

  private void processPendingCommand() {
    postOrRun(handler, this::processPendingCommandOnHandler);
  }

  private void processPendingCommandOnHandler() {
    while (pendingAsyncPlayerCommandResult == null) {
      @Nullable PlayerCommand playerCommand;
      synchronized (lock) {
        playerCommand = pendingPlayerCommandQueue.poll();
      }
      if (playerCommand == null) {
        return;
      }

      int commandCode = playerCommand.commandCode;
      // Check if it's @AsyncCommandCode
      boolean asyncCommand = isAsyncCommand(playerCommand.commandCode);

      // Continuous COMMAND_CODE_PLAYER_SEEK_TO can be skipped.
      if (commandCode == COMMAND_CODE_PLAYER_SEEK_TO) {
        @Nullable List<PlayerCommand> skippingCommands = null;
        while (true) {
          synchronized (lock) {
            @Nullable PlayerCommand pendingCommand = pendingPlayerCommandQueue.peek();
            if (pendingCommand == null || pendingCommand.commandCode != commandCode) {
              break;
            }
            pendingPlayerCommandQueue.poll();
            if (skippingCommands == null) {
              skippingCommands = new ArrayList<>();
            }
            skippingCommands.add(playerCommand);
            playerCommand = pendingCommand;
          }
        }
        if (skippingCommands != null) {
          for (PlayerCommand skippingCommand : skippingCommands) {
            skippingCommand.result.set(
                new PlayerResult(PlayerResult.RESULT_INFO_SKIPPED, player.getCurrentMediaItem()));
            if (DEBUG) {
              Log.d(TAG, "skipping pending command, " + skippingCommand);
            }
          }
        }
      }

      if (asyncCommand) {
        // Result would come later, via #notifyCommandCompleted().
        // Set pending player result first because it may be notified while the command is running.
        pendingAsyncPlayerCommandResult =
            new AsyncPlayerCommandResult(commandCode, playerCommand.result);
      }

      if (DEBUG) {
        Log.d(TAG, "start processing command, " + playerCommand);
      }

      int resultCode;
      if (player.hasError()) {
        resultCode = PlayerResult.RESULT_ERROR_INVALID_STATE;
      } else {
        try {
          boolean handled = playerCommand.command.call();
          resultCode = handled ? PlayerResult.RESULT_SUCCESS : PlayerResult.RESULT_INFO_SKIPPED;
        } catch (IllegalStateException e) {
          resultCode = PlayerResult.RESULT_ERROR_INVALID_STATE;
        } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
          resultCode = PlayerResult.RESULT_ERROR_BAD_VALUE;
        } catch (SecurityException e) {
          resultCode = PlayerResult.RESULT_ERROR_PERMISSION_DENIED;
        } catch (Exception e) {
          resultCode = PlayerResult.RESULT_ERROR_UNKNOWN;
        }
      }
      if (DEBUG) {
        Log.d(TAG, "command processed, " + playerCommand);
      }

      if (asyncCommand) {
        if (resultCode != PlayerResult.RESULT_SUCCESS
            && pendingAsyncPlayerCommandResult != null
            && playerCommand.result == pendingAsyncPlayerCommandResult.result) {
          pendingAsyncPlayerCommandResult = null;
          playerCommand.result.set(new PlayerResult(resultCode, player.getCurrentMediaItem()));
        }
      } else {
        playerCommand.result.set(new PlayerResult(resultCode, player.getCurrentMediaItem()));
      }
    }
  }

  private static String toLogFriendlyString(@AsyncCommandCode int commandCode) {
    switch (commandCode) {
      case COMMAND_CODE_PLAYER_PLAY:
        return "SessionPlayerConnector#play()";
      case COMMAND_CODE_PLAYER_PAUSE:
        return "SessionPlayerConnector#pause()";
      case COMMAND_CODE_PLAYER_PREPARE:
        return "SessionPlayerConnector#prepare()";
      default:
        // Never happens.
        throw new IllegalStateException();
    }
  }

  private static boolean isAsyncCommand(@CommandCode int commandCode) {
    switch (commandCode) {
      case COMMAND_CODE_PLAYER_PLAY:
      case COMMAND_CODE_PLAYER_PAUSE:
      case COMMAND_CODE_PLAYER_PREPARE:
        return true;
      case COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM:
      case COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM:
      case COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM:
      case COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM:
      case COMMAND_CODE_PLAYER_SEEK_TO:
      case COMMAND_CODE_PLAYER_SET_AUDIO_ATTRIBUTES:
      case COMMAND_CODE_PLAYER_SET_MEDIA_ITEM:
      case COMMAND_CODE_PLAYER_SET_PLAYLIST:
      case COMMAND_CODE_PLAYER_SET_REPEAT_MODE:
      case COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE:
      case COMMAND_CODE_PLAYER_SET_SPEED:
      case COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM:
      case COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM:
      case COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM:
      case COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA:
      default:
        return false;
    }
  }

  private static final class AsyncPlayerCommandResult {
    @AsyncCommandCode public final int commandCode;
    public final SettableFuture<PlayerResult> result;

    public AsyncPlayerCommandResult(
        @AsyncCommandCode int commandCode, SettableFuture<PlayerResult> result) {
      this.commandCode = commandCode;
      this.result = result;
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder =
          new StringBuilder("AsyncPlayerCommandResult {commandCode=")
              .append(commandCode)
              .append(", result=")
              .append(result.hashCode());
      if (result.isDone()) {
        try {
          int resultCode = result.get(/* timeout= */ 0, MILLISECONDS).getResultCode();
          stringBuilder.append(", resultCode=").append(resultCode);
        } catch (Exception e) {
          // pass-through.
        }
      }
      stringBuilder.append("}");
      return stringBuilder.toString();
    }
  }

  private static final class PlayerCommand {
    public final int commandCode;
    public final Callable<Boolean> command;
    // Result shouldn't be set with lock held, because it may trigger listener set by developers.
    public final SettableFuture<PlayerResult> result;
    @Nullable private final Object tag;

    public PlayerCommand(
        int commandCode,
        Callable<Boolean> command,
        SettableFuture<PlayerResult> result,
        @Nullable Object tag) {
      this.commandCode = commandCode;
      this.command = command;
      this.result = result;
      this.tag = tag;
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder =
          new StringBuilder("PlayerCommand {commandCode=")
              .append(commandCode)
              .append(", result=")
              .append(result.hashCode());
      if (result.isDone()) {
        try {
          int resultCode = result.get(/* timeout= */ 0, MILLISECONDS).getResultCode();
          stringBuilder.append(", resultCode=").append(resultCode);
        } catch (Exception e) {
          // pass-through.
        }
      }
      if (tag != null) {
        stringBuilder.append(", tag=").append(tag);
      }
      stringBuilder.append("}");
      return stringBuilder.toString();
    }
  }
}
