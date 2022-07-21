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

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.media.MediaSessionManager;
import androidx.media.MediaSessionManager.RemoteUserInfo;
import androidx.media2.common.MediaItem;
import androidx.media2.common.MediaMetadata;
import androidx.media2.common.Rating;
import androidx.media2.common.SessionPlayer;
import androidx.media2.session.MediaController;
import androidx.media2.session.MediaSession;
import androidx.media2.session.MediaSession.ControllerInfo;
import androidx.media2.session.SessionCommand;
import androidx.media2.session.SessionCommandGroup;
import androidx.media2.session.SessionResult;
import com.google.android.exoplayer2.util.Assertions;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a {@link MediaSession.SessionCallback} with various collaborators.
 *
 * @see MediaSession.SessionCallback
 */
public final class SessionCallbackBuilder {
  /** Default timeout value for {@link #setSeekTimeoutMs}. */
  public static final int DEFAULT_SEEK_TIMEOUT_MS = 1_000;

  private final Context context;
  private final SessionPlayerConnector sessionPlayerConnector;
  private int fastForwardMs;
  private int rewindMs;
  private int seekTimeoutMs;
  @Nullable private RatingCallback ratingCallback;
  @Nullable private CustomCommandProvider customCommandProvider;
  @Nullable private MediaItemProvider mediaItemProvider;
  @Nullable private AllowedCommandProvider allowedCommandProvider;
  @Nullable private SkipCallback skipCallback;
  @Nullable private PostConnectCallback postConnectCallback;
  @Nullable private DisconnectedCallback disconnectedCallback;

  /** Provides allowed commands for {@link MediaController}. */
  public interface AllowedCommandProvider {
    /**
     * Called to query whether to allow connection from the controller.
     *
     * <p>If it returns {@code true} to accept connection, then {@link #getAllowedCommands} will be
     * immediately followed to return initial allowed command.
     *
     * <p>Prefer use {@link PostConnectCallback} for any extra initialization about controller,
     * where controller is connected and session can send commands to the controller.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the controller that is requesting
     *     connect.
     * @return {@code true} to accept connection. {@code false} otherwise.
     */
    boolean acceptConnection(MediaSession session, ControllerInfo controllerInfo);

    /**
     * Called to query allowed commands in following cases:
     *
     * <ul>
     *   <li>A {@link MediaController} requests to connect, and allowed commands is required to tell
     *       initial allowed commands.
     *   <li>Underlying {@link SessionPlayer} state changes, and allowed commands may be updated via
     *       {@link MediaSession#setAllowedCommands}.
     * </ul>
     *
     * <p>The provided {@code baseAllowedSessionCommand} is built automatically based on the state
     * of the {@link SessionPlayer}, {@link RatingCallback}, {@link MediaItemProvider}, {@link
     * CustomCommandProvider}, and {@link SkipCallback} so may be a useful starting point for any
     * required customizations.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the controller for which allowed
     *     commands are being queried.
     * @param baseAllowedSessionCommands Base allowed session commands for customization.
     * @return The allowed commands for the controller.
     * @see MediaSession.SessionCallback#onConnect(MediaSession, ControllerInfo)
     */
    SessionCommandGroup getAllowedCommands(
        MediaSession session,
        ControllerInfo controllerInfo,
        SessionCommandGroup baseAllowedSessionCommands);

    /**
     * Called when a {@link MediaController} has called an API that controls {@link SessionPlayer}
     * set to the {@link MediaSession}.
     *
     * @param session The media session.
     * @param controllerInfo A {@link ControllerInfo} that needs allowed command update.
     * @param command A {@link SessionCommand} from the controller.
     * @return A session result code defined in {@link SessionResult}.
     * @see MediaSession.SessionCallback#onCommandRequest
     */
    int onCommandRequest(
        MediaSession session, ControllerInfo controllerInfo, SessionCommand command);
  }

  /** Callback receiving a user rating for a specified media id. */
  public interface RatingCallback {
    /**
     * Called when the specified controller has set a rating for the specified media id.
     *
     * @see MediaSession.SessionCallback#onSetRating(MediaSession, MediaSession.ControllerInfo,
     *     String, Rating)
     * @see androidx.media2.session.MediaController#setRating(String, Rating)
     * @return One of the {@link SessionResult} {@code RESULT_*} constants describing the success or
     *     failure of the operation, for example, {@link SessionResult#RESULT_SUCCESS} if the
     *     operation succeeded.
     */
    int onSetRating(MediaSession session, ControllerInfo controller, String mediaId, Rating rating);
  }

  /**
   * Callbacks for querying what custom commands are supported, and for handling a custom command
   * when a controller sends it.
   */
  public interface CustomCommandProvider {
    /**
     * Called when a controller has sent a custom command.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the controller that sent the custom
     *     command.
     * @param customCommand A {@link SessionCommand} from the controller.
     * @param args A {@link Bundle} with the extra argument.
     * @see MediaSession.SessionCallback#onCustomCommand(MediaSession, MediaSession.ControllerInfo,
     *     SessionCommand, Bundle)
     * @see androidx.media2.session.MediaController#sendCustomCommand(SessionCommand, Bundle)
     */
    SessionResult onCustomCommand(
        MediaSession session,
        ControllerInfo controllerInfo,
        SessionCommand customCommand,
        @Nullable Bundle args);

    /**
     * Returns a {@link SessionCommandGroup} with custom commands to publish to the controller, or
     * {@code null} if no custom commands should be published.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the controller that is requesting custom
     *     commands.
     * @return The custom commands to publish, or {@code null} if no custom commands should be
     *     published.
     */
    @Nullable
    SessionCommandGroup getCustomCommands(MediaSession session, ControllerInfo controllerInfo);
  }

  /** Provides the {@link MediaItem}. */
  public interface MediaItemProvider {
    /**
     * Called when {@link MediaSession.SessionCallback#onCreateMediaItem(MediaSession,
     * ControllerInfo, String)} is called.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the controller that has requested to
     *     create the item.
     * @return A new {@link MediaItem} that {@link SessionPlayerConnector} can play.
     * @see MediaSession.SessionCallback#onCreateMediaItem(MediaSession, ControllerInfo, String)
     * @see androidx.media2.session.MediaController#addPlaylistItem(int, String)
     * @see androidx.media2.session.MediaController#replacePlaylistItem(int, String)
     * @see androidx.media2.session.MediaController#setMediaItem(String)
     * @see androidx.media2.session.MediaController#setPlaylist(List, MediaMetadata)
     */
    @Nullable
    MediaItem onCreateMediaItem(
        MediaSession session, ControllerInfo controllerInfo, String mediaId);
  }

  /** Callback receiving skip backward and skip forward. */
  public interface SkipCallback {
    /**
     * Called when the specified controller has sent skip backward.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the controller that has requested to
     *     skip backward.
     * @see MediaSession.SessionCallback#onSkipBackward(MediaSession, MediaSession.ControllerInfo)
     * @see MediaController#skipBackward()
     * @return One of the {@link SessionResult} {@code RESULT_*} constants describing the success or
     *     failure of the operation, for example, {@link SessionResult#RESULT_SUCCESS} if the
     *     operation succeeded.
     */
    int onSkipBackward(MediaSession session, ControllerInfo controllerInfo);

    /**
     * Called when the specified controller has sent skip forward.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the controller that has requested to
     *     skip forward.
     * @see MediaSession.SessionCallback#onSkipForward(MediaSession, MediaSession.ControllerInfo)
     * @see MediaController#skipForward()
     * @return One of the {@link SessionResult} {@code RESULT_*} constants describing the success or
     *     failure of the operation, for example, {@link SessionResult#RESULT_SUCCESS} if the
     *     operation succeeded.
     */
    int onSkipForward(MediaSession session, ControllerInfo controllerInfo);
  }

  /** Callback for handling extra initialization after the connection. */
  public interface PostConnectCallback {
    /**
     * Called after the specified controller is connected, and you need extra initialization.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the controller that just connected.
     * @see MediaSession.SessionCallback#onPostConnect(MediaSession, ControllerInfo)
     */
    void onPostConnect(MediaSession session, MediaSession.ControllerInfo controllerInfo);
  }

  /** Callback for handling controller disconnection. */
  public interface DisconnectedCallback {
    /**
     * Called when the specified controller is disconnected.
     *
     * @param session The media session.
     * @param controllerInfo The {@link ControllerInfo} for the disconnected controller.
     * @see MediaSession.SessionCallback#onDisconnected(MediaSession, ControllerInfo)
     */
    void onDisconnected(MediaSession session, MediaSession.ControllerInfo controllerInfo);
  }

  /**
   * Default implementation of {@link AllowedCommandProvider} that behaves as follows:
   *
   * <ul>
   *   <li>Accepts connection requests from controller if any of the following conditions are met:
   *       <ul>
   *         <li>Controller is in the same package as the session.
   *         <li>Controller is allowed via {@link #setTrustedPackageNames(List)}.
   *         <li>Controller has package name {@link RemoteUserInfo#LEGACY_CONTROLLER}. See {@link
   *             ControllerInfo#getPackageName() package name limitation} for details.
   *         <li>Controller is trusted (i.e. has MEDIA_CONTENT_CONTROL permission or has enabled
   *             notification manager).
   *       </ul>
   *   <li>Allows all commands that the current player can handle.
   *   <li>Accepts all command requests for allowed commands.
   * </ul>
   *
   * <p>Note: this implementation matches the behavior of the ExoPlayer MediaSession extension and
   * {@link android.support.v4.media.session.MediaSessionCompat}.
   */
  public static final class DefaultAllowedCommandProvider implements AllowedCommandProvider {
    private final Context context;
    private final List<String> trustedPackageNames;

    public DefaultAllowedCommandProvider(Context context) {
      this.context = context;
      trustedPackageNames = new ArrayList<>();
    }

    @Override
    public boolean acceptConnection(MediaSession session, ControllerInfo controllerInfo) {
      return TextUtils.equals(controllerInfo.getPackageName(), context.getPackageName())
          || TextUtils.equals(controllerInfo.getPackageName(), RemoteUserInfo.LEGACY_CONTROLLER)
          || trustedPackageNames.contains(controllerInfo.getPackageName())
          || isTrusted(controllerInfo);
    }

    @Override
    public SessionCommandGroup getAllowedCommands(
        MediaSession session,
        ControllerInfo controllerInfo,
        SessionCommandGroup baseAllowedSessionCommands) {
      return baseAllowedSessionCommands;
    }

    @Override
    public int onCommandRequest(
        MediaSession session, ControllerInfo controllerInfo, SessionCommand command) {
      return SessionResult.RESULT_SUCCESS;
    }

    /**
     * Sets the package names from which the session will accept incoming connections.
     *
     * <p>Apps that have {@code android.Manifest.permission.MEDIA_CONTENT_CONTROL}, packages listed
     * in enabled_notification_listeners and the current package are always trusted, even if they
     * are not specified here.
     *
     * @param packageNames Package names from which the session will accept incoming connections.
     * @see MediaSession.SessionCallback#onConnect(MediaSession, MediaSession.ControllerInfo)
     * @see MediaSessionManager#isTrustedForMediaControl(RemoteUserInfo)
     */
    public void setTrustedPackageNames(@Nullable List<String> packageNames) {
      trustedPackageNames.clear();
      if (packageNames != null && !packageNames.isEmpty()) {
        trustedPackageNames.addAll(packageNames);
      }
    }

    // TODO: Replace with ControllerInfo#isTrusted() when it's unhidden [Internal: b/142835448].
    private boolean isTrusted(MediaSession.ControllerInfo controllerInfo) {
      // Check whether the controller has granted MEDIA_CONTENT_CONTROL.
      if (context
              .getPackageManager()
              .checkPermission(
                  Manifest.permission.MEDIA_CONTENT_CONTROL, controllerInfo.getPackageName())
          == PackageManager.PERMISSION_GRANTED) {
        return true;
      }

      // Check whether the app has an enabled notification listener.
      String enabledNotificationListeners =
          Settings.Secure.getString(context.getContentResolver(), "enabled_notification_listeners");
      if (!TextUtils.isEmpty(enabledNotificationListeners)) {
        String[] components = enabledNotificationListeners.split(":");
        for (String componentString : components) {
          @Nullable ComponentName component = ComponentName.unflattenFromString(componentString);
          if (component != null) {
            if (component.getPackageName().equals(controllerInfo.getPackageName())) {
              return true;
            }
          }
        }
      }
      return false;
    }
  }

  /** A {@link MediaItemProvider} that creates media items containing only a media ID. */
  public static final class MediaIdMediaItemProvider implements MediaItemProvider {
    @Override
    @Nullable
    public MediaItem onCreateMediaItem(
        MediaSession session, ControllerInfo controllerInfo, String mediaId) {
      if (TextUtils.isEmpty(mediaId)) {
        return null;
      }
      MediaMetadata metadata =
          new MediaMetadata.Builder()
              .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, mediaId)
              .build();
      return new MediaItem.Builder().setMetadata(metadata).build();
    }
  }

  /**
   * Creates a new builder.
   *
   * <p>The builder uses the following default values:
   *
   * <ul>
   *   <li>{@link AllowedCommandProvider}: {@link DefaultAllowedCommandProvider}
   *   <li>Seek timeout: {@link #DEFAULT_SEEK_TIMEOUT_MS}
   *   <li>
   * </ul>
   *
   * Unless stated above, {@code null} or {@code 0} would be used to disallow relevant features.
   *
   * @param context A context.
   * @param sessionPlayerConnector A session player connector to handle incoming calls from the
   *     controller.
   */
  public SessionCallbackBuilder(Context context, SessionPlayerConnector sessionPlayerConnector) {
    this.context = Assertions.checkNotNull(context);
    this.sessionPlayerConnector = Assertions.checkNotNull(sessionPlayerConnector);
    this.seekTimeoutMs = DEFAULT_SEEK_TIMEOUT_MS;
  }

  /**
   * Sets the {@link RatingCallback} to handle user ratings.
   *
   * @param ratingCallback A rating callback.
   * @return This builder.
   * @see MediaSession.SessionCallback#onSetRating(MediaSession, ControllerInfo, String, Rating)
   * @see androidx.media2.session.MediaController#setRating(String, Rating)
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setRatingCallback(@Nullable RatingCallback ratingCallback) {
    this.ratingCallback = ratingCallback;
    return this;
  }

  /**
   * Sets the {@link CustomCommandProvider} to handle incoming custom commands.
   *
   * @param customCommandProvider A custom command provider.
   * @return This builder.
   * @see MediaSession.SessionCallback#onCustomCommand(MediaSession, ControllerInfo, SessionCommand,
   *     Bundle)
   * @see androidx.media2.session.MediaController#sendCustomCommand(SessionCommand, Bundle)
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setCustomCommandProvider(
      @Nullable CustomCommandProvider customCommandProvider) {
    this.customCommandProvider = customCommandProvider;
    return this;
  }

  /**
   * Sets the {@link MediaItemProvider} that will convert media ids to {@link MediaItem MediaItems}.
   *
   * @param mediaItemProvider The media item provider.
   * @return This builder.
   * @see MediaSession.SessionCallback#onCreateMediaItem(MediaSession, ControllerInfo, String)
   * @see androidx.media2.session.MediaController#addPlaylistItem(int, String)
   * @see androidx.media2.session.MediaController#replacePlaylistItem(int, String)
   * @see androidx.media2.session.MediaController#setMediaItem(String)
   * @see androidx.media2.session.MediaController#setPlaylist(List, MediaMetadata)
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setMediaItemProvider(
      @Nullable MediaItemProvider mediaItemProvider) {
    this.mediaItemProvider = mediaItemProvider;
    return this;
  }

  /**
   * Sets the {@link AllowedCommandProvider} to provide allowed commands for controllers.
   *
   * @param allowedCommandProvider A allowed command provider.
   * @return This builder.
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setAllowedCommandProvider(
      @Nullable AllowedCommandProvider allowedCommandProvider) {
    this.allowedCommandProvider = allowedCommandProvider;
    return this;
  }

  /**
   * Sets the {@link SkipCallback} to handle skip backward and skip forward.
   *
   * @param skipCallback The skip callback.
   * @return This builder.
   * @see MediaSession.SessionCallback#onSkipBackward(MediaSession, ControllerInfo)
   * @see MediaSession.SessionCallback#onSkipForward(MediaSession, ControllerInfo)
   * @see MediaController#skipBackward()
   * @see MediaController#skipForward()
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setSkipCallback(@Nullable SkipCallback skipCallback) {
    this.skipCallback = skipCallback;
    return this;
  }

  /**
   * Sets the {@link PostConnectCallback} to handle extra initialization after the connection.
   *
   * @param postConnectCallback The post connect callback.
   * @return This builder.
   * @see MediaSession.SessionCallback#onPostConnect(MediaSession, ControllerInfo)
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setPostConnectCallback(
      @Nullable PostConnectCallback postConnectCallback) {
    this.postConnectCallback = postConnectCallback;
    return this;
  }

  /**
   * Sets the {@link DisconnectedCallback} to handle cleaning up controller.
   *
   * @param disconnectedCallback The disconnected callback.
   * @return This builder.
   * @see MediaSession.SessionCallback#onDisconnected(MediaSession, ControllerInfo)
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setDisconnectedCallback(
      @Nullable DisconnectedCallback disconnectedCallback) {
    this.disconnectedCallback = disconnectedCallback;
    return this;
  }

  /**
   * Sets the rewind increment in milliseconds.
   *
   * @param rewindMs The rewind increment in milliseconds. A non-positive value will cause the
   *     rewind to be disabled.
   * @return This builder.
   * @see MediaSession.SessionCallback#onRewind(MediaSession, MediaSession.ControllerInfo)
   * @see #setSeekTimeoutMs(int)
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setRewindIncrementMs(int rewindMs) {
    this.rewindMs = rewindMs;
    return this;
  }

  /**
   * Sets the fast forward increment in milliseconds.
   *
   * @param fastForwardMs The fast forward increment in milliseconds. A non-positive value will
   *     cause the fast forward to be disabled.
   * @return This builder.
   * @see MediaSession.SessionCallback#onFastForward(MediaSession, MediaSession.ControllerInfo)
   * @see #setSeekTimeoutMs(int)
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setFastForwardIncrementMs(int fastForwardMs) {
    this.fastForwardMs = fastForwardMs;
    return this;
  }

  /**
   * Sets the timeout in milliseconds for fast forward and rewind operations, or {@code 0} for no
   * timeout. If a timeout is set, controllers will receive an error if the session's call to {@link
   * SessionPlayer#seekTo} takes longer than this amount of time.
   *
   * @param seekTimeoutMs A timeout for {@link SessionPlayer#seekTo}. A non-positive value will wait
   *     forever.
   * @return This builder.
   */
  @CanIgnoreReturnValue
  public SessionCallbackBuilder setSeekTimeoutMs(int seekTimeoutMs) {
    this.seekTimeoutMs = seekTimeoutMs;
    return this;
  }

  /**
   * Builds {@link MediaSession.SessionCallback}.
   *
   * @return A new callback for a media session.
   */
  public MediaSession.SessionCallback build() {
    return new SessionCallback(
        sessionPlayerConnector,
        fastForwardMs,
        rewindMs,
        seekTimeoutMs,
        allowedCommandProvider == null
            ? new DefaultAllowedCommandProvider(context)
            : allowedCommandProvider,
        ratingCallback,
        customCommandProvider,
        mediaItemProvider,
        skipCallback,
        postConnectCallback,
        disconnectedCallback);
  }
}
