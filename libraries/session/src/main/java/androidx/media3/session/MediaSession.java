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
package androidx.media3.session;

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.session.SessionResult.RESULT_ERROR_NOT_SUPPORTED;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaSessionManager.RemoteUserInfo;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Rating;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import com.google.common.base.Objects;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.HashMap;
import java.util.List;

/**
 * A session that allows a media app to expose its transport controls and playback information in a
 * process to other processes including the Android framework and other apps. The common use cases
 * are as follows:
 *
 * <ul>
 *   <li>Bluetooth/wired headset key events support
 *   <li>Android Auto/Wearable support
 *   <li>Separating UI process and playback process
 * </ul>
 *
 * <p>A session should be created when an app wants to publish media playback information or handle
 * media key events. In general, an app only needs one session for all playback, though multiple
 * sessions can be created to provide finer grain controls of media. See <a
 * href="#MultipleSessions">Supporting Multiple Sessions</a> for details.
 *
 * <p>If you want to support background playback, {@link MediaSessionService} is preferred. With the
 * service, your playback can be revived even after playback is finished. See {@link
 * MediaSessionService} for details.
 *
 * <p>Topics covered here:
 *
 * <ol>
 *   <li><a href="#SessionLifecycle">Session Lifecycle</a>
 *   <li><a href="#ThreadingModel">Threading Model</a>
 *   <li><a href="#KeyEvents">Media Key Events Mapping</a>
 *   <li><a href="#MultipleSessions">Supporting Multiple Sessions</a>
 *   <li><a href="#CompatibilitySession">Backward Compatibility with Legacy Session APIs</a>
 *   <li><a href="#CompatibilityController">Backward Compatibility with Legacy Controller APIs</a>
 * </ol>
 *
 * <h2 id="SessionLifecycle">Session Lifecycle</h2>
 *
 * <p>A session can be created by {@link Builder}. The owner of the session may pass its {@link
 * #getToken() session token} to other processes to allow them to create a {@link MediaController}
 * to interact with the session.
 *
 * <p>When a session receives transport control commands, the session sends the commands directly to
 * the underlying player set by {@link Builder#Builder(Context, Player)} or {@link
 * #setPlayer(Player)}.
 *
 * <p>When an app is finished performing playback it must call {@link #release()} to clean up the
 * session and notify any controllers. The app is responsible for releasing the underlying player
 * after releasing the session.
 *
 * <h2 id="ThreadingModel">Threading Model</h2>
 *
 * <p>The instances are thread safe, but should be used on the thread with a looper.
 *
 * <p>{@link Callback} methods will be called from the application thread associated with the {@link
 * Player#getApplicationLooper() application looper} of the underlying player. When a new player is
 * set by {@link #setPlayer}, the player should use the same application looper as the previous one.
 *
 * <p>The session listens to events from the underlying player via {@link Player.Listener} and
 * expects the callback methods to be called from the application thread. If the player violates the
 * threading contract, {@link IllegalStateException} will be thrown.
 *
 * <h2 id="KeyEvents">Media Key Events Mapping</h2>
 *
 * <p>When the session receives media key events they are mapped to a method call on the underlying
 * player. The following table shows the mapping between the event key code and the {@link Player}
 * method which is called.
 *
 * <table>
 * <caption>Key code and corresponding Player API</caption>
 * <tr>
 *   <th>Key code</th>
 *   <th>Player API</th>
 * </tr>
 * <tr>
 *   <td>{@link KeyEvent#KEYCODE_MEDIA_PLAY}</td>
 *   <td>{@link Player#play()}</td>
 * </tr>
 * <tr>
 *   <td>{@link KeyEvent#KEYCODE_MEDIA_PAUSE}</td>
 *   <td>{@link Player#pause()}</td>
 * </tr>
 * <tr>
 *   <td>
 *     <ul>
 *       <li>{@link KeyEvent#KEYCODE_MEDIA_PLAY_PAUSE}</li>
 *       <li>{@link KeyEvent#KEYCODE_HEADSETHOOK}</li>
 *     </ul>
 *   </td>
 *   <td>
 *     <ul>
 *       <li>For a single tap,
 *         <ul>
 *           <li>
 *             {@link Player#pause()} if {@link Player#getPlayWhenReady() playWhenReady} is
 *             {@code true}
 *           </li>
 *           <li>{@link Player#play()} otherwise</li>
 *         </ul>
 *       <li>For a double tap, {@link Player#seekToNext()}</li>
 *     </ul>
 *   </td>
 * </tr>
 * <tr>
 *   <td>{@link KeyEvent#KEYCODE_MEDIA_NEXT}</td>
 *   <td>{@link Player#seekToNext()}</td>
 * </tr>
 * <tr>
 *   <td>{@link KeyEvent#KEYCODE_MEDIA_PREVIOUS}</td>
 *   <td>{@link Player#seekToPrevious()}</td>
 * </tr>
 * <tr>
 *   <td>{@link KeyEvent#KEYCODE_MEDIA_STOP}</td>
 *   <td>{@link Player#stop()}</td>
 * </tr>
 * <tr>
 *   <td>{@link KeyEvent#KEYCODE_MEDIA_FAST_FORWARD}</td>
 *   <td>{@link Player#seekForward()}</td>
 * </tr>
 * <tr>
 *   <td>{@link KeyEvent#KEYCODE_MEDIA_REWIND}</td>
 *   <td>{@link Player#seekBack()}</td>
 * </tr>
 * </table>
 *
 * <h2 id="MultipleSessions">Supporting Multiple Sessions</h2>
 *
 * <p>Generally, multiple sessions aren't necessary for most media apps. One exception is if your
 * app can play multiple media content at the same time, but only for the playback of video-only
 * media or remote playback, since the <a
 * href="https://developer.android.com/guide/topics/media-apps/audio-focus">audio focus policy</a>
 * recommends not playing multiple audio content at the same time. Also, keep in mind that multiple
 * media sessions would make Android Auto and Bluetooth device with display to show your apps
 * multiple times, because they list up media sessions, not media apps.
 *
 * <h2 id="BackwardCompatibility">Backward Compatibility with Legacy Session APIs</h2>
 *
 * <p>An active {@link MediaSessionCompat} is internally created with the session for the backward
 * compatibility. It's used to handle incoming connection and commands from {@link
 * MediaControllerCompat}, and helps to utilize existing APIs that are built with legacy media
 * session APIs. Use {@link #getSessionCompatToken} to get the legacy token of {@link
 * MediaSessionCompat}.
 *
 * <h2 id="CompatibilityController">Backward Compatibility with Legacy Controller APIs</h2>
 *
 * <p>In addition to {@link MediaController}, session also supports connection from the legacy
 * controller APIs - {@link android.media.session.MediaController framework controller} and {@link
 * MediaControllerCompat AndroidX controller compat}. However, {@link ControllerInfo} may not be
 * precise for legacy controllers. See {@link ControllerInfo} for the details.
 *
 * <p>Unknown package name nor UID doesn't mean that you should disallow connection nor commands.
 * For SDK levels where such issues happen, session tokens could only be obtained by trusted
 * controllers (e.g. Bluetooth, Auto, ...), so it may be better for you to allow them as you did
 * with legacy sessions.
 */
public class MediaSession {

  static {
    MediaLibraryInfo.registerModule("media3.session");
  }

  // It's better to have private static lock instead of using MediaSession.class because the
  // private lock object isn't exposed.
  private static final Object STATIC_LOCK = new Object();
  // Note: This checks the uniqueness of a session ID only in single process.
  // When the framework becomes able to check the uniqueness, this logic should be removed.
  @GuardedBy("STATIC_LOCK")
  private static final HashMap<String, MediaSession> SESSION_ID_TO_SESSION_MAP = new HashMap<>();

  /**
   * A builder for {@link MediaSession}.
   *
   * <p>Any incoming requests from the {@link MediaController} will be handled on the application
   * thread of the underlying {@link Player}.
   */
  public static final class Builder extends BuilderBase<MediaSession, Builder, Callback> {

    /**
     * Creates a builder for {@link MediaSession}.
     *
     * @param context The context.
     * @param player The underlying player to perform playback and handle transport controls.
     * @throws IllegalArgumentException if {@link Player#canAdvertiseSession()} returns false.
     */
    public Builder(Context context, Player player) {
      super(context, player, new Callback() {});
    }

    /**
     * Sets a {@link PendingIntent} to launch an {@link android.app.Activity} for the {@link
     * MediaSession}. This can be used as a quick link to an ongoing media screen.
     *
     * <p>A client can use this pending intent to start an activity belonging to this session. When
     * this pending intent is for instance included in the notification {@linkplain
     * NotificationCompat.Builder#setContentIntent(PendingIntent) as the content intent}, tapping
     * the notification will open this activity.
     *
     * <p>See <a href="https://developer.android.com/training/notify-user/navigation">'Start an
     * Activity from a Notification'</a> also.
     *
     * @param pendingIntent The pending intent.
     * @return The builder to allow chaining.
     */
    @Override
    public Builder setSessionActivity(PendingIntent pendingIntent) {
      return super.setSessionActivity(pendingIntent);
    }

    /**
     * Sets an ID of the {@link MediaSession}. If not set, an empty string will be used.
     *
     * <p>Use this if and only if your app supports multiple playback at the same time and also
     * wants to provide external apps to have finer-grained controls.
     *
     * @param id The ID. Must be unique among all {@link MediaSession sessions} per package.
     * @return The builder to allow chaining.
     */
    // Note: This ID is not visible to the controllers. ID is introduced in order to prevent
    // apps from creating multiple sessions without any clear reasons. If they create two
    // sessions with the same ID in a process, then an IllegalStateException will be thrown.
    @Override
    public Builder setId(String id) {
      return super.setId(id);
    }

    /**
     * Sets a callback for the {@link MediaSession} to handle incoming requests from {link
     * MediaController}.
     *
     * @param callback The callback.
     * @return The builder to allow chaining.
     */
    @Override
    public Builder setCallback(Callback callback) {
      return super.setCallback(callback);
    }

    /**
     * Sets the logic used to fill in the fields of a {@link MediaItem} from {@link
     * MediaController}.
     *
     * @param mediaItemFiller The filler.
     * @return The builder to allow chaining.
     */
    @Override
    public Builder setMediaItemFiller(MediaItemFiller mediaItemFiller) {
      return super.setMediaItemFiller(mediaItemFiller);
    }

    /**
     * Sets an extra {@link Bundle} for the {@link MediaSession}. The {@link
     * MediaSession#getToken()} session token} will have the {@link SessionToken#getExtras()
     * extras}. If not set, an empty {@link Bundle} will be used.
     *
     * @param extras The extra {@link Bundle}.
     * @return The builder to allow chaining.
     */
    @Override
    public Builder setExtras(Bundle extras) {
      return super.setExtras(extras);
    }

    /**
     * Builds a {@link MediaSession}.
     *
     * @return A new session.
     * @throws IllegalStateException if a {@link MediaSession} with the same {@link #setId(String)
     *     ID} already exists in the package.
     */
    @Override
    public MediaSession build() {
      return new MediaSession(
          context, id, player, sessionActivity, callback, mediaItemFiller, extras);
    }
  }

  /** Information of a {@link MediaController} or a {@link MediaBrowser}. */
  public static final class ControllerInfo {

    private final RemoteUserInfo remoteUserInfo;
    private final int controllerVersion;
    private final boolean isTrusted;
    @Nullable private final ControllerCb controllerCb;
    private final Bundle connectionHints;

    /**
     * Creates an instance.
     *
     * @param remoteUserInfo The remote user info.
     * @param trusted {@code true} if trusted, {@code false} otherwise.
     * @param cb ControllerCb. Can be {@code null} only when a MediaBrowserCompat connects to
     *     MediaSessionService and ControllerInfo is needed for SessionCallback#onConnected().
     * @param connectionHints A session-specific argument sent from the controller for the
     *     connection. The contents of this bundle may affect the connection result.
     */
    /* package */ ControllerInfo(
        RemoteUserInfo remoteUserInfo,
        int controllerVersion,
        boolean trusted,
        @Nullable ControllerCb cb,
        Bundle connectionHints) {
      this.remoteUserInfo = remoteUserInfo;
      this.controllerVersion = controllerVersion;
      isTrusted = trusted;
      controllerCb = cb;
      this.connectionHints = connectionHints;
    }

    /* package */ RemoteUserInfo getRemoteUserInfo() {
      return remoteUserInfo;
    }

    /**
     * Returns the library version of the controller.
     *
     * <p>It will be the same as {@link MediaLibraryInfo#VERSION_INT} of the controller, or less
     * than {@code 1000000} if the controller is a legacy controller.
     */
    public int getControllerVersion() {
      return controllerVersion;
    }

    /**
     * Returns the package name. Can be {@link RemoteUserInfo#LEGACY_CONTROLLER} for
     * interoperability.
     *
     * <p>Interoperability: Package name may not be precisely obtained for legacy controller API on
     * older device. Here are details.
     *
     * <table>
     * <caption>Summary when package name isn't precise</caption>
     * <tr><th>SDK version when package name isn't precise</th>
     *     <th>{@code ControllerInfo#getPackageName()} for legacy controller</th>
     * <tr><td>{@code SDK_INT < 21}</td>
     *     <td>Actual package name via {@link PackageManager#getNameForUid} with UID.<br>
     *         It's sufficient for most cases, but doesn't precisely distinguish caller if it
     *         uses shared user ID.</td>
     * <tr><td>{@code 21 <= SDK_INT < 24}</td>
     *     <td>{@link RemoteUserInfo#LEGACY_CONTROLLER}</td>
     * </table>
     */
    public String getPackageName() {
      return remoteUserInfo.getPackageName();
    }

    /**
     * Returns the UID of the controller. Can be a negative value for interoperability.
     *
     * <p>Interoperability: If {@code 21 <= SDK_INT < 28}, then UID would be a negative value
     * because it cannot be obtained.
     */
    public int getUid() {
      return remoteUserInfo.getUid();
    }

    /** Returns the connection hints sent from controller. */
    public Bundle getConnectionHints() {
      return new Bundle(connectionHints);
    }

    /**
     * Returns if the controller has been granted {@code android.permission.MEDIA_CONTENT_CONTROL}
     * or has a enabled notification listener so it can be trusted to accept connection and incoming
     * command request.
     */
    /* package */ boolean isTrusted() {
      return isTrusted;
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(controllerCb, remoteUserInfo);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (!(obj instanceof ControllerInfo)) {
        return false;
      }
      if (this == obj) {
        return true;
      }
      ControllerInfo other = (ControllerInfo) obj;
      if (controllerCb != null || other.controllerCb != null) {
        return Util.areEqual(controllerCb, other.controllerCb);
      }
      return remoteUserInfo.equals(other.remoteUserInfo);
    }

    @Override
    public String toString() {
      return "ControllerInfo {pkg="
          + remoteUserInfo.getPackageName()
          + ", uid="
          + remoteUserInfo.getUid()
          + "})";
    }

    @Nullable
    /* package */ ControllerCb getControllerCb() {
      return controllerCb;
    }

    /* package */ static ControllerInfo createLegacyControllerInfo() {
      RemoteUserInfo legacyRemoteUserInfo =
          new RemoteUserInfo(
              RemoteUserInfo.LEGACY_CONTROLLER,
              /* pid= */ RemoteUserInfo.UNKNOWN_PID,
              /* uid= */ RemoteUserInfo.UNKNOWN_UID);
      return new ControllerInfo(
          legacyRemoteUserInfo,
          /* controllerVersion= */ 0,
          /* trusted= */ false,
          /* cb= */ null,
          /* connectionHints= */ Bundle.EMPTY);
    }
  }

  private final MediaSessionImpl impl;

  // Suppress nullness check as `this` is under initialization.
  @SuppressWarnings({"nullness:argument", "nullness:method.invocation"})
  /* package */ MediaSession(
      Context context,
      String id,
      Player player,
      @Nullable PendingIntent sessionActivity,
      Callback callback,
      MediaItemFiller mediaItemFiller,
      Bundle tokenExtras) {
    synchronized (STATIC_LOCK) {
      if (SESSION_ID_TO_SESSION_MAP.containsKey(id)) {
        throw new IllegalStateException("Session ID must be unique. ID=" + id);
      }
      SESSION_ID_TO_SESSION_MAP.put(id, this);
    }
    impl = createImpl(context, id, player, sessionActivity, callback, mediaItemFiller, tokenExtras);
  }

  /* package */ MediaSessionImpl createImpl(
      Context context,
      String id,
      Player player,
      @Nullable PendingIntent sessionActivity,
      Callback callback,
      MediaItemFiller mediaItemFiller,
      Bundle tokenExtras) {
    return new MediaSessionImpl(
        this, context, id, player, sessionActivity, callback, mediaItemFiller, tokenExtras);
  }

  /* package */ MediaSessionImpl getImpl() {
    return impl;
  }

  @Nullable
  /* package */ static MediaSession getSession(Uri sessionUri) {
    synchronized (STATIC_LOCK) {
      for (MediaSession session : SESSION_ID_TO_SESSION_MAP.values()) {
        if (Util.areEqual(session.getUri(), sessionUri)) {
          return session;
        }
      }
    }
    return null;
  }

  /**
   * Returns the {@link PendingIntent} to launch {@linkplain
   * Builder#setSessionActivity(PendingIntent) the session activity} or null if not set.
   *
   * @return The {@link PendingIntent} to launch an activity belonging to the session.
   */
  @Nullable
  public PendingIntent getSessionActivity() {
    return impl.getSessionActivity();
  }

  /**
   * Sets the underlying {@link Player} for this session to dispatch incoming events to.
   *
   * @param player A player that handles actual media playback in your app.
   * @throws IllegalArgumentException if the new player's application looper differs from the
   *     current player's looper, or {@link Player#canAdvertiseSession()} returns false.
   * @throws IllegalStateException if the new player's application looper differs from the current
   *     looper.
   */
  public void setPlayer(Player player) {
    checkNotNull(player);
    checkArgument(player.canAdvertiseSession());
    checkArgument(player.getApplicationLooper() == getPlayer().getApplicationLooper());
    checkState(player.getApplicationLooper() == Looper.myLooper());
    impl.setPlayer(player);
  }

  /**
   * Releases the session and disconnects all connected controllers.
   *
   * <p>The session must not be used after calling this method.
   *
   * <p>Releasing the session removes the session's listeners from the player but does not
   * {@linkplain Player#stop() stop} or {@linkplain Player#release() release} the player. An app can
   * further use the player after the session is released and needs to make sure to eventually
   * release the player.
   */
  public void release() {
    try {
      synchronized (STATIC_LOCK) {
        SESSION_ID_TO_SESSION_MAP.remove(impl.getId());
      }
      impl.release();
    } catch (Exception e) {
      // Should not be here.
    }
  }

  /* package */ boolean isReleased() {
    return impl.isReleased();
  }

  /** Returns the underlying {@link Player}. */
  public Player getPlayer() {
    return impl.getPlayerWrapper().getWrappedPlayer();
  }

  /** Returns the session ID. */
  public String getId() {
    return impl.getId();
  }

  /** Returns the {@link SessionToken} for creating {@link MediaController}. */
  public SessionToken getToken() {
    return impl.getToken();
  }

  /** Returns the list of connected controllers. */
  public List<ControllerInfo> getConnectedControllers() {
    return impl.getConnectedControllers();
  }

  /**
   * Requests that controllers set the ordered list of {@link CommandButton} to build UI with it.
   *
   * <p>It's up to controller's decision how to represent the layout in its own UI. Here are some
   * examples. Note: {@code layout[i]} means a {@link CommandButton} at index {@code i} in the given
   * list.
   *
   * <table>
   * <caption>Examples of controller's UI layout</caption>
   * <tr>
   *   <th>Controller UI layout</th>
   *   <th>Layout example</th>
   * </tr>
   * <tr>
   *   <td>
   *     Row with 3 icons
   *   </td>
   *   <td style="white-space: nowrap;">
   *     {@code layout[1]} {@code layout[0]} {@code layout[2]}
   *   </td>
   * </tr>
   * <tr>
   *   <td>
   *     Row with 5 icons
   *   </td>
   *   <td style="white-space: nowrap;">
   *     {@code layout[3]} {@code layout[1]} {@code layout[0]} {@code layout[2]} {@code layout[4]}
   *   </td>
   * </tr>
   * <tr>
   *   <td rowspan="2">
   *     Row with 5 icons and an overflow icon, and another expandable row with 5 extra icons
   *   </td>
   *   <td style="white-space: nowrap;">
   *     {@code layout[5]} {@code layout[6]} {@code layout[7]} {@code layout[8]} {@code layout[9]}
   *   </td>
   * </tr>
   * <tr>
   *   <td style="white-space: nowrap;">
   *     {@code layout[3]} {@code layout[1]} {@code layout[0]} {@code layout[2]} {@code layout[4]}
   *   </td>
   * </tr>
   * </table>
   *
   * @param controller The controller to specify layout.
   * @param layout The ordered list of {@link CommandButton}.
   */
  public ListenableFuture<SessionResult> setCustomLayout(
      ControllerInfo controller, List<CommandButton> layout) {
    checkNotNull(controller, "controller must not be null");
    checkNotNull(layout, "layout must not be null");
    return impl.setCustomLayout(controller, layout);
  }

  /**
   * Broadcasts the custom layout to all connected Media3 controllers and converts the buttons to
   * custom actions in the legacy media session playback state (see {@code
   * PlaybackStateCompat.Builder#addCustomAction(PlaybackStateCompat.CustomAction)}) for legacy
   * controllers.
   *
   * <p>When converting, the {@link SessionCommand#customExtras custom extras of the session
   * command} is used for the extras of the legacy custom action.
   *
   * <p>Media3 controllers that connect after calling this method will not receive the broadcast.
   * You need to call {@link #setCustomLayout(ControllerInfo, List)} in {@link
   * MediaSession.Callback#onPostConnect(MediaSession, ControllerInfo)} to make these controllers
   * aware of the custom layout.
   *
   * @param layout The ordered list of {@link CommandButton}.
   */
  public void setCustomLayout(List<CommandButton> layout) {
    checkNotNull(layout, "layout must not be null");
    impl.setCustomLayout(layout);
  }

  /**
   * Sets the new available commands for the controller.
   *
   * <p>This is a synchronous call. Changes in the available commands take effect immediately
   * regardless of the controller notified about the change through {@link
   * Player.Listener#onAvailableCommandsChanged(Player.Commands)} and {@link
   * MediaController.Listener#onAvailableSessionCommandsChanged(MediaController, SessionCommands)}.
   *
   * <p>Note that {@code playerCommands} will be intersected with the {@link
   * Player#getAvailableCommands() available commands} of the underlying {@link Player} and the
   * controller will only be able to call the commonly available commands.
   *
   * @param controller The controller to change allowed commands.
   * @param sessionCommands The new available session commands.
   * @param playerCommands The new available player commands.
   */
  public void setAvailableCommands(
      ControllerInfo controller, SessionCommands sessionCommands, Player.Commands playerCommands) {
    checkNotNull(controller, "controller must not be null");
    checkNotNull(sessionCommands, "sessionCommands must not be null");
    checkNotNull(playerCommands, "playerCommands must not be null");
    impl.setAvailableCommands(controller, sessionCommands, playerCommands);
  }

  /**
   * Broadcasts a custom command to all connected controllers.
   *
   * <p>This is a synchronous call and doesn't wait for results from the controllers.
   *
   * <p>A command is not accepted if it is not a custom command.
   *
   * @param command A custom command.
   * @param args A {@link Bundle} for additional arguments. May be empty.
   * @see #sendCustomCommand(ControllerInfo, SessionCommand, Bundle)
   */
  public void broadcastCustomCommand(SessionCommand command, Bundle args) {
    checkNotNull(command);
    checkNotNull(args);
    checkArgument(
        command.commandCode == SessionCommand.COMMAND_CODE_CUSTOM,
        "command must be a custom command");
    impl.broadcastCustomCommand(command, args);
  }

  /**
   * Sends a custom command to a specific controller.
   *
   * <p>The result from {@link MediaController.Listener#onCustomCommand(MediaController,
   * SessionCommand, Bundle)} will be returned.
   *
   * <p>A command is not accepted if it is not a custom command.
   *
   * @param command A custom command.
   * @param args A {@link Bundle} for additional arguments. May be empty.
   * @return A {@link ListenableFuture} of {@link SessionResult} from the controller.
   * @see #broadcastCustomCommand(SessionCommand, Bundle)
   */
  public ListenableFuture<SessionResult> sendCustomCommand(
      ControllerInfo controller, SessionCommand command, Bundle args) {
    checkNotNull(controller);
    checkNotNull(command);
    checkNotNull(args);
    checkArgument(
        command.commandCode == SessionCommand.COMMAND_CODE_CUSTOM,
        "command must be a custom command");
    return impl.sendCustomCommand(controller, command, args);
  }

  /* package */ MediaSessionCompat getSessionCompat() {
    return impl.getSessionCompat();
  }

  /**
   * Returns the {@link MediaSessionCompat.Token} of the {@link MediaSessionCompat} created
   * internally by this session. You may cast the {@link Object} to {@link
   * MediaSessionCompat.Token}.
   */
  @UnstableApi
  public Object getSessionCompatToken() {
    return impl.getSessionCompat().getSessionToken();
  }

  /**
   * Sets the timeout for disconnecting legacy controllers.
   *
   * @param timeoutMs The timeout in milliseconds.
   */
  /* package */ void setLegacyControllerConnectionTimeoutMs(long timeoutMs) {
    impl.setLegacyControllerConnectionTimeoutMs(timeoutMs);
  }

  /** Handles the controller's connection request from {@link MediaSessionService}. */
  /* package */ void handleControllerConnectionFromService(
      IMediaController controller,
      int controllerVersion,
      String packageName,
      int pid,
      int uid,
      Bundle connectionHints) {
    impl.connectFromService(controller, controllerVersion, packageName, pid, uid, connectionHints);
  }

  /* package */ IBinder getLegacyBrowserServiceBinder() {
    return impl.getLegacyBrowserServiceBinder();
  }

  /**
   * Sets delay for periodic {@link SessionPositionInfo} updates. This resets previously pended
   * update. Should be only called on the application looper.
   *
   * <p>A {@code updateDelayMs delay} less than or equal to {@code 0} will disable further updates
   * after an immediate one-time update.
   */
  @VisibleForTesting
  /* package */ void setSessionPositionUpdateDelayMs(long updateDelayMs) {
    impl.setSessionPositionUpdateDelayMsOnHandler(updateDelayMs);
  }

  private Uri getUri() {
    return impl.getUri();
  }

  /**
   * A callback to handle incoming commands from {@link MediaController}.
   *
   * <p>The callback methods will be called from the application thread associated with the {@link
   * Player#getApplicationLooper() application looper} of the underlying {@link Player}.
   *
   * <p>If it's not set by {@link MediaSession.Builder#setCallback(Callback)}, the session will
   * accept all controllers and all incoming commands by default.
   */
  public interface Callback {

    /**
     * Called when a controller is about to connect to this session. Return a {@link
     * ConnectionResult result} containing available commands for the controller by using {@link
     * ConnectionResult#accept(SessionCommands, Player.Commands)}. By default it allows all
     * connection requests and commands.
     *
     * <p>Note that the player commands in {@link ConnectionResult#availablePlayerCommands} will be
     * intersected with the {@link Player#getAvailableCommands() available commands} of the
     * underlying {@link Player} and the controller will only be able to call the commonly available
     * commands.
     *
     * <p>You can reject the connection by returning {@link ConnectionResult#reject()}}. In that
     * case, the controller will get {@link SecurityException} when resolving the {@link
     * ListenableFuture} returned by {@link MediaController.Builder#buildAsync()}.
     *
     * <p>The controller isn't connected yet, so calls to the controller (e.g. {@link
     * #sendCustomCommand}, {@link #setCustomLayout}) will be ignored. Use {@link #onPostConnect}
     * for custom initialization of the controller instead.
     *
     * <p>Interoperability: If a legacy controller is connecting to the session then this callback
     * may block the main thread, even if it's called on a different application thread. If it's
     * possible that legacy controllers will connect to the session, you should ensure that the
     * callback returns quickly to avoid blocking the main thread for a long period of time.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     * @return The {@link ConnectionResult}.
     */
    default ConnectionResult onConnect(MediaSession session, ControllerInfo controller) {
      SessionCommands sessionCommands =
          new SessionCommands.Builder().addAllSessionCommands().build();
      Player.Commands playerCommands = new Player.Commands.Builder().addAllCommands().build();
      return ConnectionResult.accept(sessionCommands, playerCommands);
    }

    /**
     * Called immediately after a controller is connected. This is for custom initialization of the
     * controller.
     *
     * <p>Note that calls to the controller (e.g. {@link #sendCustomCommand}, {@link
     * #setCustomLayout}) work here but don't work in {@link #onConnect} because the controller
     * isn't connected yet in {@link #onConnect}.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     */
    default void onPostConnect(MediaSession session, ControllerInfo controller) {}

    /**
     * Called when a controller is disconnected.
     *
     * <p>Interoperability: For legacy controllers, this is called when the controller doesn't send
     * any command for a while. It's because there were no explicit disconnection in legacy
     * controller APIs.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     */
    default void onDisconnected(MediaSession session, ControllerInfo controller) {}

    /**
     * Called when a controller sent a command which will be sent directly to the underlying {@link
     * Player}.
     *
     * <p>Return {@link SessionResult#RESULT_SUCCESS} to proceed the command. Otherwise, the command
     * won't be sent and the controller will receive the code. This method will be called for every
     * single command.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     * @param playerCommand A {@link Player.Command command}.
     * @return {@link SessionResult#RESULT_SUCCESS} to proceed, or another code to ignore.
     * @see Player.Command#COMMAND_PLAY_PAUSE
     * @see Player.Command#COMMAND_PREPARE
     * @see Player.Command#COMMAND_STOP
     * @see Player.Command#COMMAND_SEEK_TO_DEFAULT_POSITION
     * @see Player.Command#COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
     * @see Player.Command#COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
     * @see Player.Command#COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
     * @see Player.Command#COMMAND_SEEK_TO_MEDIA_ITEM
     * @see Player.Command#COMMAND_SET_SPEED_AND_PITCH
     * @see Player.Command#COMMAND_SET_SHUFFLE_MODE
     * @see Player.Command#COMMAND_SET_REPEAT_MODE
     * @see Player.Command#COMMAND_GET_CURRENT_MEDIA_ITEM
     * @see Player.Command#COMMAND_GET_TIMELINE
     * @see Player.Command#COMMAND_GET_MEDIA_ITEMS_METADATA
     * @see Player.Command#COMMAND_SET_MEDIA_ITEMS_METADATA
     * @see Player.Command#COMMAND_CHANGE_MEDIA_ITEMS
     * @see Player.Command#COMMAND_GET_AUDIO_ATTRIBUTES
     * @see Player.Command#COMMAND_GET_VOLUME
     * @see Player.Command#COMMAND_GET_DEVICE_VOLUME
     * @see Player.Command#COMMAND_SET_VOLUME
     * @see Player.Command#COMMAND_SET_DEVICE_VOLUME
     * @see Player.Command#COMMAND_ADJUST_DEVICE_VOLUME
     * @see Player.Command#COMMAND_SET_VIDEO_SURFACE
     * @see Player.Command#COMMAND_GET_TEXT
     * @see Player.Command#COMMAND_SET_TRACK_SELECTION_PARAMETERS
     * @see Player.Command#COMMAND_GET_TRACKS
     */
    default @SessionResult.Code int onPlayerCommandRequest(
        MediaSession session, ControllerInfo controller, @Player.Command int playerCommand) {
      return RESULT_SUCCESS;
    }

    /**
     * Called when a controller requested to set a rating to a media for the current user by {@link
     * MediaController#setRating(String, Rating)}.
     *
     * <p>To allow setting the user rating for a {@link MediaItem}, the item's {@link
     * MediaItem#mediaMetadata metadata} should have the {@link Rating} field in order to provide
     * possible rating style for controllers. Controllers will follow the rating style.
     *
     * <p>Return a {@link ListenableFuture} to send a {@link SessionResult} back to the controller
     * asynchronously. You can also return a {@link SessionResult} directly by using Guava's {@link
     * Futures#immediateFuture(Object)}.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     * @param mediaId The media id.
     * @param rating The new rating from the controller.
     * @see SessionCommand#COMMAND_CODE_SESSION_SET_RATING
     */
    default ListenableFuture<SessionResult> onSetRating(
        MediaSession session, ControllerInfo controller, String mediaId, Rating rating) {
      return Futures.immediateFuture(new SessionResult(RESULT_ERROR_NOT_SUPPORTED));
    }

    /**
     * Called when a controller requested to set a rating for the current media item for the current
     * user by {@link MediaController#setRating(Rating)}.
     *
     * <p>To allow setting the user rating for the current {@link MediaItem}, the item's {@link
     * MediaItem#mediaMetadata metadata} should have the {@link Rating} field in order to provide
     * possible rating style for controllers. Controllers will follow the rating style.
     *
     * <p>Return a {@link ListenableFuture} to send a {@link SessionResult} back to the controller
     * asynchronously. You can also return a {@link SessionResult} directly by using Guava's {@link
     * Futures#immediateFuture(Object)}.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     * @param rating The new rating from the controller.
     * @see SessionCommand#COMMAND_CODE_SESSION_SET_RATING
     */
    default ListenableFuture<SessionResult> onSetRating(
        MediaSession session, ControllerInfo controller, Rating rating) {
      return Futures.immediateFuture(new SessionResult(RESULT_ERROR_NOT_SUPPORTED));
    }

    /**
     * Called when a controller requested to set the specific media item(s) represented by a URI
     * through {@link MediaController#setMediaUri(Uri, Bundle)}.
     *
     * <p>The implementation should create proper {@link MediaItem media item(s)} for the given
     * {@code uri} and call {@link Player#setMediaItems}.
     *
     * <p>When {@link MediaControllerCompat} is connected and sends commands with following methods,
     * the {@code uri} will have the following patterns:
     *
     * <table>
     * <caption>Uri patterns corresponding to MediaControllerCompat command methods</caption>
     * <tr>
     *   <th>Method</th>
     *   <th>Uri pattern</th>
     * </tr>
     * <tr>
     *   <td>{@link MediaControllerCompat.TransportControls#prepareFromUri prepareFromUri}</td>
     *   <td>The {@code uri} passed as argument</td>
     * </tr>
     * <tr>
     *   <td>
     *     {@link MediaControllerCompat.TransportControls#prepareFromMediaId prepareFromMediaId}
     *   </td>
     *   <td>{@code androidx://media3-session/prepareFromMediaId?id=[mediaId]}</td>
     * </tr>
     * <tr>
     *   <td>
     *     {@link MediaControllerCompat.TransportControls#prepareFromSearch prepareFromSearch}
     *   </td>
     *   <td>{@code androidx://media3-session/prepareFromSearch?query=[query]}</td>
     * </tr>
     * <tr>
     *   <td>{@link MediaControllerCompat.TransportControls#playFromUri playFromUri}</td>
     *   <td>The {@code uri} passed as argument</td>
     * </tr>
     * <tr>
     *   <td>{@link MediaControllerCompat.TransportControls#playFromMediaId playFromMediaId}</td>
     *   <td>{@code androidx://media3-session/playFromMediaId?id=[mediaId]}</td>
     * </tr>
     * <tr>
     *   <td>{@link MediaControllerCompat.TransportControls#playFromSearch playFromSearch}</td>
     *   <td>{@code androidx://media3-session/playFromSearch?query=[query]}</td>
     * </tr>
     * </table>
     *
     * <p>{@link Player#prepare()} or {@link Player#play()} should follow if this is called by above
     * methods.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     * @param uri The uri.
     * @param extras An extra {@link Bundle}. May be empty.
     * @return A result code.
     */
    default @SessionResult.Code int onSetMediaUri(
        MediaSession session, ControllerInfo controller, Uri uri, Bundle extras) {
      return RESULT_ERROR_NOT_SUPPORTED;
    }

    /**
     * Called when a controller sent a custom command through {@link
     * MediaController#sendCustomCommand(SessionCommand, Bundle)}.
     *
     * <p>Interoperability: This will be also called by {@link
     * android.support.v4.media.MediaBrowserCompat#sendCustomAction}. If so, {@code extras} from
     * {@link android.support.v4.media.MediaBrowserCompat#sendCustomAction} will be considered as
     * {@code args} and the custom command will have {@code null} {@link
     * SessionCommand#customExtras}.
     *
     * <p>Return a {@link ListenableFuture} to send a {@link SessionResult} back to the controller
     * asynchronously. You can also return a {@link SessionResult} directly by using Guava's {@link
     * Futures#immediateFuture(Object)}.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     * @param customCommand The custom command.
     * @param args A {@link Bundle} for additional arguments. May be empty.
     * @return The result of handling the custom command.
     * @see SessionCommand#COMMAND_CODE_CUSTOM
     */
    default ListenableFuture<SessionResult> onCustomCommand(
        MediaSession session,
        ControllerInfo controller,
        SessionCommand customCommand,
        Bundle args) {
      return Futures.immediateFuture(new SessionResult(RESULT_ERROR_NOT_SUPPORTED));
    }
  }

  /** An object which fills in the fields of a {@link MediaItem} from {@link MediaController}. */
  public interface MediaItemFiller {

    /**
     * Called to fill in the {@link MediaItem#localConfiguration} of the media item from
     * controllers.
     *
     * @param session The session for this event.
     * @param controller The controller information.
     * @param mediaItem The media item whose local configuration will be filled in.
     * @return A media item with filled local configuration.
     */
    default MediaItem fillInLocalConfiguration(
        MediaSession session, MediaSession.ControllerInfo controller, MediaItem mediaItem) {
      return mediaItem;
    }
  }

  /**
   * A result for {@link Callback#onConnect(MediaSession, ControllerInfo)} to denote the set of
   * commands that are available for the given {@link ControllerInfo controller}.
   */
  public static final class ConnectionResult {

    /** Whether the connection request is accepted or not. */
    public final boolean isAccepted;

    /** Available session commands. */
    public final SessionCommands availableSessionCommands;

    /** Available player commands. */
    public final Player.Commands availablePlayerCommands;

    /** Creates a new instance with the given available session and player commands. */
    private ConnectionResult(
        boolean accepted,
        SessionCommands availableSessionCommands,
        Player.Commands availablePlayerCommands) {
      isAccepted = accepted;
      this.availableSessionCommands = checkNotNull(availableSessionCommands);
      this.availablePlayerCommands = checkNotNull(availablePlayerCommands);
    }

    public static ConnectionResult accept(
        SessionCommands availableSessionCommands, Player.Commands availablePlayerCommands) {
      return new ConnectionResult(
          /* accepted= */ true, availableSessionCommands, availablePlayerCommands);
    }

    public static ConnectionResult reject() {
      return new ConnectionResult(
          /* accepted= */ false, SessionCommands.EMPTY, Player.Commands.EMPTY);
    }
  }

  /* package */ interface ControllerCb {

    default void onSessionResult(int seq, SessionResult result) throws RemoteException {}

    default void onLibraryResult(int seq, LibraryResult<?> result) throws RemoteException {}

    default void onPlayerChanged(
        int seq, @Nullable PlayerWrapper oldPlayerWrapper, PlayerWrapper newPlayerWrapper)
        throws RemoteException {}

    default void onPlayerInfoChanged(
        int seq,
        PlayerInfo playerInfo,
        boolean excludeMediaItems,
        boolean excludeMediaItemsMetadata,
        boolean excludeCues,
        boolean excludeTimeline)
        throws RemoteException {}

    default void onPeriodicSessionPositionInfoChanged(
        int seq, SessionPositionInfo sessionPositionInfo) throws RemoteException {}

    // Mostly matched with MediaController.ControllerCallback

    default void onDisconnected(int seq) throws RemoteException {}

    default void setCustomLayout(int seq, List<CommandButton> layout) throws RemoteException {}

    default void sendCustomCommand(int seq, SessionCommand command, Bundle args)
        throws RemoteException {}

    default void onAvailableCommandsChangedFromSession(
        int seq, SessionCommands sessionCommands, Player.Commands playerCommands)
        throws RemoteException {}

    default void onAvailableCommandsChangedFromPlayer(int seq, Player.Commands availableCommands)
        throws RemoteException {}

    // Mostly matched with MediaBrowser.BrowserCallback

    default void onChildrenChanged(
        int seq, String parentId, int itemCount, @Nullable LibraryParams params)
        throws RemoteException {}

    default void onSearchResultChanged(
        int seq, String query, int itemCount, @Nullable LibraryParams params)
        throws RemoteException {}

    // Mostly matched with Player.Listener

    default void onPlayerError(int seq, @Nullable PlaybackException playerError)
        throws RemoteException {}

    default void onPlayWhenReadyChanged(
        int seq, boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason)
        throws RemoteException {}

    default void onPlaybackSuppressionReasonChanged(
        int seq, @Player.PlaybackSuppressionReason int reason) throws RemoteException {}

    default void onPlaybackStateChanged(
        int seq, @Player.State int state, @Nullable PlaybackException playerError)
        throws RemoteException {}

    default void onIsPlayingChanged(int seq, boolean isPlaying) throws RemoteException {}

    default void onIsLoadingChanged(int seq, boolean isLoading) throws RemoteException {}

    default void onTrackSelectionParametersChanged(int seq, TrackSelectionParameters parameters)
        throws RemoteException {}

    default void onPlaybackParametersChanged(int seq, PlaybackParameters playbackParameters)
        throws RemoteException {}

    default void onPositionDiscontinuity(
        int seq,
        PositionInfo oldPosition,
        PositionInfo newPosition,
        @DiscontinuityReason int reason)
        throws RemoteException {}

    default void onMediaItemTransition(
        int seq, @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason)
        throws RemoteException {}

    default void onTimelineChanged(
        int seq, Timeline timeline, @Player.TimelineChangeReason int reason)
        throws RemoteException {}

    default void onPlaylistMetadataChanged(int seq, MediaMetadata metadata)
        throws RemoteException {}

    default void onShuffleModeEnabledChanged(int seq, boolean shuffleModeEnabled)
        throws RemoteException {}

    default void onRepeatModeChanged(int seq, @RepeatMode int repeatMode) throws RemoteException {}

    default void onSeekBackIncrementChanged(int seq, long seekBackIncrementMs)
        throws RemoteException {}

    default void onSeekForwardIncrementChanged(int seq, long seekForwardIncrementMs)
        throws RemoteException {}

    default void onVideoSizeChanged(int seq, VideoSize videoSize) throws RemoteException {}

    default void onVolumeChanged(int seq, float volume) throws RemoteException {}

    default void onAudioAttributesChanged(int seq, AudioAttributes audioAttributes)
        throws RemoteException {}

    default void onDeviceInfoChanged(int seq, DeviceInfo deviceInfo) throws RemoteException {}

    default void onDeviceVolumeChanged(int seq, int volume, boolean muted) throws RemoteException {}

    default void onMediaMetadataChanged(int seq, MediaMetadata mediaMetadata)
        throws RemoteException {}

    default void onRenderedFirstFrame(int seq) throws RemoteException {}
  }

  /**
   * A base class for {@link MediaSession.Builder} and {@link
   * MediaLibraryService.MediaLibrarySession.Builder}. Any changes to this class should be also
   * applied to the subclasses.
   */
  /* package */ abstract static class BuilderBase<
      T extends MediaSession, U extends BuilderBase<T, U, C>, C extends Callback> {

    /* package */ final Context context;
    /* package */ final Player player;
    /* package */ String id;
    /* package */ C callback;
    /* package */ MediaItemFiller mediaItemFiller;
    /* package */ @Nullable PendingIntent sessionActivity;
    /* package */ Bundle extras;

    public BuilderBase(Context context, Player player, C callback) {
      this.context = checkNotNull(context);
      this.player = checkNotNull(player);
      checkArgument(player.canAdvertiseSession());
      id = "";
      this.callback = callback;
      this.mediaItemFiller = new MediaItemFiller() {};
      extras = Bundle.EMPTY;
    }

    @SuppressWarnings("unchecked")
    public U setSessionActivity(PendingIntent pendingIntent) {
      sessionActivity = checkNotNull(pendingIntent);
      return (U) this;
    }

    @SuppressWarnings("unchecked")
    public U setId(String id) {
      this.id = checkNotNull(id);
      return (U) this;
    }

    @SuppressWarnings("unchecked")
    /* package */ U setCallback(C callback) {
      this.callback = checkNotNull(callback);
      return (U) this;
    }

    @SuppressWarnings("unchecked")
    /* package */ U setMediaItemFiller(MediaItemFiller mediaItemFiller) {
      this.mediaItemFiller = checkNotNull(mediaItemFiller);
      return (U) this;
    }

    @SuppressWarnings("unchecked")
    public U setExtras(Bundle extras) {
      this.extras = new Bundle(checkNotNull(extras));
      return (U) this;
    }

    public abstract T build();
  }
}
