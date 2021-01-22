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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.Rating;
import androidx.media2.common.SessionPlayer;
import androidx.media2.session.MediaSession;
import androidx.media2.session.SessionCommand;
import androidx.media2.session.SessionCommandGroup;
import androidx.media2.session.SessionResult;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.AllowedCommandProvider;
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.CustomCommandProvider;
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.DisconnectedCallback;
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.MediaItemProvider;
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.PostConnectCallback;
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.RatingCallback;
import com.google.android.exoplayer2.ext.media2.SessionCallbackBuilder.SkipCallback;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/* package */ class SessionCallback extends MediaSession.SessionCallback {
  private static final String TAG = "SessionCallback";

  private final SessionPlayer sessionPlayer;
  private final int fastForwardMs;
  private final int rewindMs;
  private final int seekTimeoutMs;
  private final Set<MediaSession> sessions;
  private final AllowedCommandProvider allowedCommandProvider;
  @Nullable private final RatingCallback ratingCallback;
  @Nullable private final CustomCommandProvider customCommandProvider;
  @Nullable private final MediaItemProvider mediaItemProvider;
  @Nullable private final SkipCallback skipCallback;
  @Nullable private final PostConnectCallback postConnectCallback;
  @Nullable private final DisconnectedCallback disconnectedCallback;
  private boolean loggedUnexpectedSessionPlayerWarning;

  public SessionCallback(
      SessionPlayerConnector sessionPlayerConnector,
      int fastForwardMs,
      int rewindMs,
      int seekTimeoutMs,
      AllowedCommandProvider allowedCommandProvider,
      @Nullable RatingCallback ratingCallback,
      @Nullable CustomCommandProvider customCommandProvider,
      @Nullable MediaItemProvider mediaItemProvider,
      @Nullable SkipCallback skipCallback,
      @Nullable PostConnectCallback postConnectCallback,
      @Nullable DisconnectedCallback disconnectedCallback) {
    this.sessionPlayer = sessionPlayerConnector;
    this.allowedCommandProvider = allowedCommandProvider;
    this.ratingCallback = ratingCallback;
    this.customCommandProvider = customCommandProvider;
    this.mediaItemProvider = mediaItemProvider;
    this.skipCallback = skipCallback;
    this.postConnectCallback = postConnectCallback;
    this.disconnectedCallback = disconnectedCallback;
    this.fastForwardMs = fastForwardMs;
    this.rewindMs = rewindMs;
    this.seekTimeoutMs = seekTimeoutMs;
    this.sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Register PlayerCallback and make it to be called before the ListenableFuture set the result.
    // It help the PlayerCallback to update allowed commands before pended Player APIs are executed.
    sessionPlayerConnector.registerPlayerCallback(Runnable::run, new PlayerCallback());
  }

  @Override
  @Nullable
  public SessionCommandGroup onConnect(
      MediaSession session, MediaSession.ControllerInfo controllerInfo) {
    sessions.add(session);
    if (!allowedCommandProvider.acceptConnection(session, controllerInfo)) {
      return null;
    }
    SessionCommandGroup baseAllowedCommands = buildAllowedCommands(session, controllerInfo);
    return allowedCommandProvider.getAllowedCommands(session, controllerInfo, baseAllowedCommands);
  }

  @Override
  public void onPostConnect(
      @NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller) {
    if (postConnectCallback != null) {
      postConnectCallback.onPostConnect(session, controller);
    }
  }

  @Override
  public void onDisconnected(MediaSession session, MediaSession.ControllerInfo controller) {
    if (session.getConnectedControllers().isEmpty()) {
      sessions.remove(session);
    }
    if (disconnectedCallback != null) {
      disconnectedCallback.onDisconnected(session, controller);
    }
  }

  @Override
  public int onCommandRequest(
      MediaSession session, MediaSession.ControllerInfo controller, SessionCommand command) {
    return allowedCommandProvider.onCommandRequest(session, controller, command);
  }

  @Override
  @Nullable
  public MediaItem onCreateMediaItem(
      MediaSession session, MediaSession.ControllerInfo controller, String mediaId) {
    Assertions.checkNotNull(mediaItemProvider);
    return mediaItemProvider.onCreateMediaItem(session, controller, mediaId);
  }

  @Override
  public int onSetRating(
      MediaSession session, MediaSession.ControllerInfo controller, String mediaId, Rating rating) {
    if (ratingCallback != null) {
      return ratingCallback.onSetRating(session, controller, mediaId, rating);
    }
    return SessionResult.RESULT_ERROR_NOT_SUPPORTED;
  }

  @Override
  public SessionResult onCustomCommand(
      MediaSession session,
      MediaSession.ControllerInfo controller,
      SessionCommand customCommand,
      @Nullable Bundle args) {
    if (customCommandProvider != null) {
      return customCommandProvider.onCustomCommand(session, controller, customCommand, args);
    }
    return new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED, null);
  }

  @Override
  public int onFastForward(MediaSession session, MediaSession.ControllerInfo controller) {
    if (fastForwardMs > 0) {
      return seekToOffset(fastForwardMs);
    }
    return SessionResult.RESULT_ERROR_NOT_SUPPORTED;
  }

  @Override
  public int onRewind(MediaSession session, MediaSession.ControllerInfo controller) {
    if (rewindMs > 0) {
      return seekToOffset(-rewindMs);
    }
    return SessionResult.RESULT_ERROR_NOT_SUPPORTED;
  }

  @Override
  public int onSkipBackward(
      @NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller) {
    if (skipCallback != null) {
      return skipCallback.onSkipBackward(session, controller);
    }
    return SessionResult.RESULT_ERROR_NOT_SUPPORTED;
  }

  @Override
  public int onSkipForward(
      @NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller) {
    if (skipCallback != null) {
      return skipCallback.onSkipForward(session, controller);
    }
    return SessionResult.RESULT_ERROR_NOT_SUPPORTED;
  }

  private int seekToOffset(long offsetMs) {
    long positionMs = sessionPlayer.getCurrentPosition() + offsetMs;
    long durationMs = sessionPlayer.getDuration();
    if (durationMs != C.TIME_UNSET) {
      positionMs = Math.min(positionMs, durationMs);
    }
    positionMs = Math.max(positionMs, 0);

    ListenableFuture<SessionPlayer.PlayerResult> result = sessionPlayer.seekTo(positionMs);
    try {
      if (seekTimeoutMs <= 0) {
        return result.get().getResultCode();
      }
      return result.get(seekTimeoutMs, MILLISECONDS).getResultCode();
    } catch (ExecutionException | InterruptedException | TimeoutException e) {
      Log.w(TAG, "Failed to get the seeking result", e);
      return SessionResult.RESULT_ERROR_UNKNOWN;
    }
  }

  private SessionCommandGroup buildAllowedCommands(
      MediaSession session, MediaSession.ControllerInfo controllerInfo) {
    SessionCommandGroup.Builder build;
    @Nullable
    SessionCommandGroup commands =
        (customCommandProvider != null)
            ? customCommandProvider.getCustomCommands(session, controllerInfo)
            : null;
    if (commands != null) {
      build = new SessionCommandGroup.Builder(commands);
    } else {
      build = new SessionCommandGroup.Builder();
    }

    build.addAllPredefinedCommands(SessionCommand.COMMAND_VERSION_1);
    build.addCommand(new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_MOVE_PLAYLIST_ITEM));
    // TODO(internal b/142848015): Use removeCommand(int) when it's added.
    if (mediaItemProvider == null) {
      build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SET_MEDIA_ITEM));
      build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SET_PLAYLIST));
      build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM));
      build.removeCommand(
          new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM));
    }
    if (ratingCallback == null) {
      build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SET_RATING));
    }
    if (skipCallback == null) {
      build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SKIP_BACKWARD));
      build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_SKIP_FORWARD));
    }

    // Apply player's capability.
    // Check whether the session has unexpectedly changed the player.
    if (session.getPlayer() instanceof SessionPlayerConnector) {
      SessionPlayerConnector sessionPlayerConnector = (SessionPlayerConnector) session.getPlayer();

      // Check whether skipTo* works.
      if (!sessionPlayerConnector.canSkipToPlaylistItem()) {
        build.removeCommand(
            new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM));
      }
      if (!sessionPlayerConnector.canSkipToPreviousPlaylistItem()) {
        build.removeCommand(
            new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM));
      }
      if (!sessionPlayerConnector.canSkipToNextPlaylistItem()) {
        build.removeCommand(
            new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM));
      }

      // Check whether seekTo/rewind/fastForward works.
      if (!sessionPlayerConnector.isCurrentMediaItemSeekable()) {
        build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SEEK_TO));
        build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD));
        build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_REWIND));
      } else {
        if (fastForwardMs <= 0) {
          build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD));
        }
        if (rewindMs <= 0) {
          build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_REWIND));
        }
      }
    } else {
      if (!loggedUnexpectedSessionPlayerWarning) {
        // This can happen if MediaSession#updatePlayer() is called.
        Log.e(TAG, "SessionPlayer isn't a SessionPlayerConnector. Guess the allowed command.");
        loggedUnexpectedSessionPlayerWarning = true;
      }

      if (fastForwardMs <= 0) {
        build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD));
      }
      if (rewindMs <= 0) {
        build.removeCommand(new SessionCommand(SessionCommand.COMMAND_CODE_SESSION_REWIND));
      }
      @Nullable List<MediaItem> playlist = sessionPlayer.getPlaylist();
      if (playlist == null) {
        build.removeCommand(
            new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM));
        build.removeCommand(
            new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM));
        build.removeCommand(
            new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM));
      } else {
        if (playlist.isEmpty()
            && (sessionPlayer.getRepeatMode() == SessionPlayer.REPEAT_MODE_NONE
                || sessionPlayer.getRepeatMode() == SessionPlayer.REPEAT_MODE_ONE)) {
          build.removeCommand(
              new SessionCommand(
                  SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM));
        }
        if (playlist.size() == sessionPlayer.getCurrentMediaItemIndex() + 1
            && (sessionPlayer.getRepeatMode() == SessionPlayer.REPEAT_MODE_NONE
                || sessionPlayer.getRepeatMode() == SessionPlayer.REPEAT_MODE_ONE)) {
          build.removeCommand(
              new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM));
        }
        if (playlist.size() <= 1) {
          build.removeCommand(
              new SessionCommand(SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM));
        }
      }
    }
    return build.build();
  }

  private static boolean isBufferedState(/* @SessionPlayer.BuffState */ int buffState) {
    return buffState == SessionPlayer.BUFFERING_STATE_BUFFERING_AND_PLAYABLE
        || buffState == SessionPlayer.BUFFERING_STATE_COMPLETE;
  }

  private final class PlayerCallback extends SessionPlayer.PlayerCallback {
    private boolean currentMediaItemBuffered;

    @Override
    public void onPlaylistChanged(
        SessionPlayer player, @Nullable List<MediaItem> list, @Nullable MediaMetadata metadata) {
      updateAllowedCommands();
    }

    @Override
    public void onPlayerStateChanged(SessionPlayer player, int playerState) {
      updateAllowedCommands();
    }

    @Override
    public void onRepeatModeChanged(SessionPlayer player, int repeatMode) {
      updateAllowedCommands();
    }

    @Override
    public void onShuffleModeChanged(SessionPlayer player, int shuffleMode) {
      updateAllowedCommands();
    }

    // TODO(internal b/160846312): Remove warning suppression and mark item @Nullable once we depend
    // on media2 1.2.0.
    @Override
    @SuppressWarnings("nullness:override.param.invalid")
    public void onCurrentMediaItemChanged(SessionPlayer player, MediaItem item) {
      currentMediaItemBuffered = isBufferedState(player.getBufferingState());
      updateAllowedCommands();
    }

    @Override
    public void onBufferingStateChanged(
        SessionPlayer player, @Nullable MediaItem item, int buffState) {
      if (currentMediaItemBuffered || player.getCurrentMediaItem() != item) {
        return;
      }
      if (isBufferedState(buffState)) {
        currentMediaItemBuffered = true;
        updateAllowedCommands();
      }
    }

    private void updateAllowedCommands() {
      for (MediaSession session : sessions) {
        List<MediaSession.ControllerInfo> connectedControllers = session.getConnectedControllers();
        for (MediaSession.ControllerInfo controller : connectedControllers) {
          SessionCommandGroup baseAllowedCommands = buildAllowedCommands(session, controller);
          SessionCommandGroup allowedCommands =
              allowedCommandProvider.getAllowedCommands(session, controller, baseAllowedCommands);
          if (allowedCommands == null) {
            allowedCommands = new SessionCommandGroup.Builder().build();
          }
          session.setAllowedCommands(controller, allowedCommands);
        }
      }
    }
  }
}
