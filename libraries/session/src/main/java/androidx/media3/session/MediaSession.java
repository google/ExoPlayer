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

import static androidx.annotation.VisibleForTesting.PRIVATE;
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
import androidx.media3.common.C;
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
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.HashMap;
import java.util.List;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A session that allows a media app to expose its player functionality, information of the playlist
 * and the media item currently being played to other processes including the Android framework and
 * other apps. The common use cases are as follows:
 *
 * <ul>
 *   <li>Receiving and dispatching media key events (for instance Bluetooth/wired headset and remote
 *       control devices).
 *   <li>Publish media playback information and player commands to SystemUI (media notification) and
 *       Android Auto/Wear OS.
 *   <li>Separating UI process and playback process.
 * </ul>
 *
 * <p>A session should be created when an app wants to publish media playback information or handle
 * media key events. In general, an app only needs one session for all playback, though multiple
 * sessions can be created to provide finer grain controls of media. See <a
 * href="#MultipleSessions">Supporting Multiple Sessions</a> for details.
 *
 * <p>If an app wants to support playback when in the background, using a {@link
 * MediaSessionService} is the preferred approach. See {@link MediaSessionService} for details.
 *
 * <p>Topics covered here:
 *
 * <ol>
 *   <li><a href="#SessionLifecycle">Session Lifecycle</a>
 *   <li><a href="#ThreadingModel">Threading Model</a>
 *   <li><a href="#KeyEvents">Media Key Events Mapping</a>
 *   <li><a href="#MultipleSessions">Supporting Multiple Sessions</a>
 *   <li><a href="#BackwardCompatibility">Backward Compatibility with Legacy Session APIs</a>
 *   <li><a href="#CompatibilityController">Backward Compatibility with Legacy Controller APIs</a>
 * </ol>
 *
 * <h2 id="SessionLifecycle">Session Lifecycle</h2>
 *
 * <p>A session can be created by {@link Builder}. The owner of the session may pass its {@link
 * #getToken() session token} to other processes to allow them to create a {@link MediaController}
 * to interact with the session.
 *
 * <p>When a session receives playback commands, the session calls corresponding methods directly on
 * the underlying player set by {@link Builder#Builder(Context, Player)} or {@link
 * #setPlayer(Player)}.
 *
 * <p>When an app is finished performing playback it must call {@link #release()} to clean up the
 * session and notify any controllers. The app is responsible for releasing the underlying player
 * after releasing the session.
 *
 * <h2 id="ThreadingModel">Threading Model</h2>
 *
 * <p>The instances are thread safe, but must be used on a thread with a looper.
 *
 * <p>{@link Callback} methods will be called on the application thread associated with the {@link
 * Player#getApplicationLooper() application looper} of the underlying player. When a new player is
 * set by {@link #setPlayer}, the player must use the same application looper as the previous one.
 *
 * <p>The session listens to player events via {@link Player.Listener} and expects the callback
 * methods to be called on the application thread. If the player violates the threading contract, an
 * {@link IllegalStateException} will be thrown.
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
 *       <li>In case the media key events are coming from another package ID than the package ID of
 *         the media app (events coming for instance from Bluetooth), a double tap generating two
 *         key events within a brief amount of time, is converted to {@link Player#seekToNext()}
 *         </li>
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
 * media sessions would make Android Auto and Bluetooth devices with display to show your app
 * multiple times, because they list up media sessions, not media apps.
 *
 * <h2 id="BackwardCompatibility">Backward Compatibility with Legacy Session APIs</h2>
 *
 * <p>An active {@link MediaSessionCompat} is internally created with the session for backwards
 * compatibility. It's used to handle incoming connections and commands from {@link
 * MediaControllerCompat} instances, and helps to utilize existing APIs that are built with legacy
 * media session APIs.
 *
 * <h2 id="CompatibilityController">Backward Compatibility with Legacy Controller APIs</h2>
 *
 * <p>In addition to {@link MediaController}, the session also supports connections from the legacy
 * controller APIs - {@linkplain android.media.session.MediaController framework controller} and
 * {@linkplain MediaControllerCompat AndroidX controller compat}. However, {@link ControllerInfo}
 * may not be precise for legacy controllers. See {@link ControllerInfo} for the details.
 *
 * <p>Neither an unknown package name nor an unknown UID mean that you should disallow a connection
 * or commands per se. For SDK levels where such issues happen, session tokens can only be obtained
 * by trusted controllers (e.g. Bluetooth, Auto, ...). This means only trusted controllers can
 * connect and an app can accept such controllers in the same way as with legacy sessions.
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
     * @param player The underlying player to perform playback and handle player commands.
     * @throws IllegalArgumentException if {@link Player#canAdvertiseSession()} returns false.
     */
    public Builder(Context context, Player player) {
      super(context, player, new Callback() {});
    }

    /**
     * Sets a {@link PendingIntent} to launch an {@link android.app.Activity} for the {@link
     * MediaSession}. This can be used as a quick link to an ongoing media screen.
     *
     * <p>A client can use this pending intent to start an activity belonging to this session. On
     * API levels below 33 the pending intent can be used {@linkplain
     * NotificationCompat.Builder#setContentIntent(PendingIntent) as the content intent}. Tapping
     * the notification will then send that pending intent and open the activity (see <a
     * href="https://developer.android.com/training/notify-user/navigation">'Start an Activity from
     * a Notification'</a>). For API levels starting with 33, the media notification reads the
     * pending intent directly from the session.
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
     * <p>Apps that want to allow controllers to {@linkplain MediaController#setMediaItems(List)
     * set} or {@linkplain MediaController#addMediaItems(List) add} media items to the playlist,
     * must use a callback and override its {@link
     * MediaSession.Callback#onSetMediaItems(MediaSession, ControllerInfo, List, int, long)} or
     * {@link MediaSession.Callback#onSetMediaItems(MediaSession, ControllerInfo, List, int, long)}
     * methods.
     *
     * @param callback The callback.
     * @return The builder to allow chaining.
     */
    @Override
    public Builder setCallback(Callback callback) {
      return super.setCallback(callback);
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
     * Sets a {@link BitmapLoader} for the {@link MediaSession} to decode bitmaps from compressed
     * binary data or load bitmaps from {@link Uri}. If not set, a {@link CacheBitmapLoader} with a
     * {@link SimpleBitmapLoader} inside will be used.
     *
     * <p>The provided instance will likely be called repeatedly with the same request, so it would
     * be best if any provided instance does some caching. Simple caching can be added to any {@link
     * BitmapLoader} implementation by wrapping it in {@link CacheBitmapLoader} before passing it to
     * this method.
     *
     * <p>If no instance is set, a {@link CacheBitmapLoader} with a {@link SimpleBitmapLoader}
     * inside will be used.
     *
     * @param bitmapLoader The bitmap loader {@link BitmapLoader}.
     * @return The builder to allow chaining.
     */
    @UnstableApi
    @Override
    public Builder setBitmapLoader(BitmapLoader bitmapLoader) {
      return super.setBitmapLoader(bitmapLoader);
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
      if (bitmapLoader == null) {
        bitmapLoader = new CacheBitmapLoader(new SimpleBitmapLoader());
      }
      return new MediaSession(
          context, id, player, sessionActivity, callback, extras, checkNotNull(bitmapLoader));
    }
  }

  /** Information of a {@link MediaController} or a {@link MediaBrowser}. */
  public static final class ControllerInfo {

    /** The {@linkplain #getControllerVersion() controller version} of legacy controllers. */
    public static final int LEGACY_CONTROLLER_VERSION = 0;
    /** The {@linkplain #getInterfaceVersion()} interface version} of legacy controllers. */
    @UnstableApi public static final int LEGACY_CONTROLLER_INTERFACE_VERSION = 0;

    private final RemoteUserInfo remoteUserInfo;
    private final int libraryVersion;
    private final int interfaceVersion;
    private final boolean isTrusted;
    @Nullable private final ControllerCb controllerCb;
    private final Bundle connectionHints;

    /**
     * Creates an instance.
     *
     * @param remoteUserInfo The remote user info.
     * @param trusted {@code true} if trusted, {@code false} otherwise.
     * @param cb ControllerCb. Can be {@code null} only when a MediaBrowserCompat connects to
     *     MediaSessionService and ControllerInfo is needed for {@link
     *     MediaSession.Callback#onConnect(MediaSession, ControllerInfo)}.
     * @param connectionHints A session-specific argument sent from the controller for the
     *     connection. The contents of this bundle may affect the connection result.
     */
    /* package */ ControllerInfo(
        RemoteUserInfo remoteUserInfo,
        int libraryVersion,
        int interfaceVersion,
        boolean trusted,
        @Nullable ControllerCb cb,
        Bundle connectionHints) {
      this.remoteUserInfo = remoteUserInfo;
      this.libraryVersion = libraryVersion;
      this.interfaceVersion = interfaceVersion;
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
      return libraryVersion;
    }

    /** Returns the interface version of the controller, or 0 if it's a legacy controller. */
    @UnstableApi
    public int getInterfaceVersion() {
      return interfaceVersion;
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
          ControllerInfo.LEGACY_CONTROLLER_VERSION,
          ControllerInfo.LEGACY_CONTROLLER_INTERFACE_VERSION,
          /* trusted= */ false,
          /* cb= */ null,
          /* connectionHints= */ Bundle.EMPTY);
    }

    /** Returns a {@link ControllerInfo} suitable for use when testing client code. */
    @VisibleForTesting(otherwise = PRIVATE)
    public static ControllerInfo createTestOnlyControllerInfo(
        RemoteUserInfo remoteUserInfo,
        int libraryVersion,
        int interfaceVersion,
        boolean trusted,
        Bundle connectionHints) {
      return new MediaSession.ControllerInfo(
          remoteUserInfo,
          libraryVersion,
          interfaceVersion,
          trusted,
          /* cb= */ null,
          connectionHints);
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
      Bundle tokenExtras,
      BitmapLoader bitmapLoader) {
    synchronized (STATIC_LOCK) {
      if (SESSION_ID_TO_SESSION_MAP.containsKey(id)) {
        throw new IllegalStateException("Session ID must be unique. ID=" + id);
      }
      SESSION_ID_TO_SESSION_MAP.put(id, this);
    }
    impl = createImpl(context, id, player, sessionActivity, callback, tokenExtras, bitmapLoader);
  }

  /* package */ MediaSessionImpl createImpl(
      Context context,
      String id,
      Player player,
      @Nullable PendingIntent sessionActivity,
      Callback callback,
      Bundle tokenExtras,
      BitmapLoader bitmapLoader) {
    return new MediaSessionImpl(
        this, context, id, player, sessionActivity, callback, tokenExtras, bitmapLoader);
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
   * <p>Interoperability: This call has no effect when called for a {@linkplain
   * ControllerInfo#LEGACY_CONTROLLER_VERSION legacy controller}.
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
   * Sends the session extras to connected controllers.
   *
   * <p>This is a synchronous call and doesn't wait for results from the controllers.
   *
   * @param sessionExtras The session extras.
   */
  public void setSessionExtras(Bundle sessionExtras) {
    checkNotNull(sessionExtras);
    impl.setSessionExtras(sessionExtras);
  }

  /**
   * Sends the session extras to the connected controller.
   *
   * <p>This is a synchronous call and doesn't wait for results from the controller.
   *
   * <p>Interoperability: This call has no effect when called for a {@linkplain
   * ControllerInfo#LEGACY_CONTROLLER_VERSION legacy controller}.
   *
   * @param controller The controller to send the extras to.
   * @param sessionExtras The session extras.
   */
  public void setSessionExtras(ControllerInfo controller, Bundle sessionExtras) {
    checkNotNull(controller, "controller must not be null");
    checkNotNull(sessionExtras);
    impl.setSessionExtras(controller, sessionExtras);
  }

  /** Returns the {@link BitmapLoader}. */
  @UnstableApi
  public BitmapLoader getBitmapLoader() {
    return impl.getBitmapLoader();
  }

  /**
   * Sends a custom command to a specific controller.
   *
   * <p>The result from {@link MediaController.Listener#onCustomCommand(MediaController,
   * SessionCommand, Bundle)} will be returned.
   *
   * <p>A command is not accepted if it is not a custom command.
   *
   * <p>Interoperability: This call has no effect when called for a {@linkplain
   * ControllerInfo#LEGACY_CONTROLLER_VERSION legacy controller}.
   *
   * @param controller The controller to send the custom command to.
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
   * internally by this session.
   */
  @UnstableApi
  public MediaSessionCompat.Token getSessionCompatToken() {
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
      int controllerInterfaceVersion,
      String packageName,
      int pid,
      int uid,
      Bundle connectionHints) {
    impl.connectFromService(
        controller,
        controllerVersion,
        controllerInterfaceVersion,
        packageName,
        pid,
        uid,
        connectionHints);
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

  /**
   * Sets the {@linkplain Listener listener}.
   *
   * <p>This method must be called on the main thread.
   */
  /* package */ void setListener(Listener listener) {
    impl.setMediaSessionListener(listener);
  }

  /**
   * Clears the {@linkplain Listener listener}.
   *
   * <p>This method must be called on the main thread.
   */
  /* package */ void clearListener() {
    impl.clearMediaSessionListener();
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
     * Called when a controller sent a custom command through {@link
     * MediaController#sendCustomCommand(SessionCommand, Bundle)}.
     *
     * <p>{@link MediaController} instances are only allowed to send a command if the command has
     * been added to the {@link MediaSession.ConnectionResult#availableSessionCommands list of
     * available session commands} in {@link #onConnect} or set via {@link #setAvailableCommands}.
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

    /**
     * Called when a controller requested to add new {@linkplain MediaItem media items} to the
     * playlist via one of the {@code Player.addMediaItem(s)} methods. Unless overriden, {@link
     * Callback#onSetMediaItems} will direct {@code Player.setMediaItem(s)} to this method as well.
     *
     * <p>In addition, unless {@link Callback#onSetMediaItems} is overridden, this callback is also
     * called when an app is using a legacy {@link MediaControllerCompat.TransportControls} to
     * prepare or play media (for instance when browsing the catalogue and then selecting an item
     * for preparation from Android Auto that is using the legacy Media1 library).
     *
     * <p>Note that the requested {@linkplain MediaItem media items} don't have a {@link
     * MediaItem.LocalConfiguration} (for example, a URI) and need to be updated to make them
     * playable by the underlying {@link Player}. Typically, this implementation should be able to
     * identify the correct item by its {@link MediaItem#mediaId} and/or the {@link
     * MediaItem#requestMetadata}.
     *
     * <p>Return a {@link ListenableFuture} with the resolved {@link MediaItem media items}. You can
     * also return the items directly by using Guava's {@link Futures#immediateFuture(Object)}. Once
     * the {@link MediaItem media items} have been resolved, the session will call {@link
     * Player#setMediaItems} or {@link Player#addMediaItems} as requested.
     *
     * <p>Interoperability: This method will be called, unless {@link Callback#onSetMediaItems} is
     * overridden, in response to the following {@link MediaControllerCompat} methods:
     *
     * <ul>
     *   <li>{@link MediaControllerCompat.TransportControls#prepareFromUri prepareFromUri}
     *   <li>{@link MediaControllerCompat.TransportControls#playFromUri playFromUri}
     *   <li>{@link MediaControllerCompat.TransportControls#prepareFromMediaId prepareFromMediaId}
     *   <li>{@link MediaControllerCompat.TransportControls#playFromMediaId playFromMediaId}
     *   <li>{@link MediaControllerCompat.TransportControls#prepareFromSearch prepareFromSearch}
     *   <li>{@link MediaControllerCompat.TransportControls#playFromSearch playFromSearch}
     *   <li>{@link MediaControllerCompat.TransportControls#addQueueItem addQueueItem}
     * </ul>
     *
     * The values of {@link MediaItem#mediaId}, {@link MediaItem.RequestMetadata#mediaUri}, {@link
     * MediaItem.RequestMetadata#searchQuery} and {@link MediaItem.RequestMetadata#extras} will be
     * set to match the legacy method call. The session will call {@link Player#setMediaItems} or
     * {@link Player#addMediaItems}, followed by {@link Player#prepare()} and {@link Player#play()}
     * as appropriate once the {@link MediaItem} has been resolved.
     *
     * @param mediaSession The session for this event.
     * @param controller The controller information.
     * @param mediaItems The list of requested {@link MediaItem media items}.
     * @return A {@link ListenableFuture} for the list of resolved {@link MediaItem media items}
     *     that are playable by the underlying {@link Player}.
     */
    default ListenableFuture<List<MediaItem>> onAddMediaItems(
        MediaSession mediaSession, ControllerInfo controller, List<MediaItem> mediaItems) {
      return Futures.immediateFailedFuture(new UnsupportedOperationException());
    }

    /**
     * Called when a controller requested to set {@linkplain MediaItem media items} to the playlist
     * via one of the {@code Player.setMediaItem(s)} methods. The default implementation calls
     * {@link Callback#onAddMediaItems}. Override this method if you want to modify/set the starting
     * index/position for the {@code Player.setMediaItem(s)} methods.
     *
     * <p>This callback is also called when an app is using a legacy {@link
     * MediaControllerCompat.TransportControls} to prepare or play media (for instance when browsing
     * the catalogue and then selecting an item for preparation from Android Auto that is using the
     * legacy Media1 library).
     *
     * <p>Note that the requested {@linkplain MediaItem media items} in the
     * MediaItemsWithStartPosition don't have a {@link MediaItem.LocalConfiguration} (for example, a
     * URI) and need to be updated to make them playable by the underlying {@link Player}.
     * Typically, this implementation should be able to identify the correct item by its {@link
     * MediaItem#mediaId} and/or the {@link MediaItem#requestMetadata}.
     *
     * <p>Return a {@link ListenableFuture} with the resolved {@linkplain
     * MediaItemsWithStartPosition media items and starting index and position}. You can also return
     * the items directly by using Guava's {@link Futures#immediateFuture(Object)}. Once the {@link
     * MediaItemsWithStartPosition} has been resolved, the session will call {@link
     * Player#setMediaItems} as requested. If the resolved {@link
     * MediaItemsWithStartPosition#startIndex startIndex} is {@link C#INDEX_UNSET C.INDEX_UNSET}
     * then the session will call {@link Player#setMediaItem(MediaItem, boolean)} with {@code
     * resetPosition} set to {@code true}.
     *
     * <p>Interoperability: This method will be called in response to the following {@link
     * MediaControllerCompat} methods:
     *
     * <ul>
     *   <li>{@link MediaControllerCompat.TransportControls#prepareFromUri prepareFromUri}
     *   <li>{@link MediaControllerCompat.TransportControls#playFromUri playFromUri}
     *   <li>{@link MediaControllerCompat.TransportControls#prepareFromMediaId prepareFromMediaId}
     *   <li>{@link MediaControllerCompat.TransportControls#playFromMediaId playFromMediaId}
     *   <li>{@link MediaControllerCompat.TransportControls#prepareFromSearch prepareFromSearch}
     *   <li>{@link MediaControllerCompat.TransportControls#playFromSearch playFromSearch}
     *   <li>{@link MediaControllerCompat.TransportControls#addQueueItem addQueueItem}
     * </ul>
     *
     * The values of {@link MediaItem#mediaId}, {@link MediaItem.RequestMetadata#mediaUri}, {@link
     * MediaItem.RequestMetadata#searchQuery} and {@link MediaItem.RequestMetadata#extras} will be
     * set to match the legacy method call. The session will call {@link Player#setMediaItems} or
     * {@link Player#addMediaItems}, followed by {@link Player#prepare()} and {@link Player#play()}
     * as appropriate once the {@link MediaItem} has been resolved.
     *
     * @param mediaSession The session for this event.
     * @param controller The controller information.
     * @param mediaItems The list of requested {@linkplain MediaItem media items}.
     * @param startIndex The start index in the {@link MediaItem} list from which to start playing,
     *     or {@link C#INDEX_UNSET C.INDEX_UNSET} to start playing from the default index in the
     *     playlist.
     * @param startPositionMs The starting position in the media item from where to start playing,
     *     or {@link C#TIME_UNSET C.TIME_UNSET} to start playing from the default position in the
     *     media item. This value is ignored if startIndex is C.INDEX_UNSET
     * @return A {@link ListenableFuture} with a {@link MediaItemsWithStartPosition} containing a
     *     list of resolved {@linkplain MediaItem media items}, and a starting index and position
     *     that are playable by the underlying {@link Player}. If returned {@link
     *     MediaItemsWithStartPosition#startIndex} is {@link C#INDEX_UNSET C.INDEX_UNSET} and {@link
     *     MediaItemsWithStartPosition#startPositionMs} is {@link C#TIME_UNSET C.TIME_UNSET}, then
     *     {@linkplain Player#setMediaItems(List, boolean) Player#setMediaItems(List, true)} will be
     *     called to set media items with default index and position.
     */
    @UnstableApi
    default ListenableFuture<MediaItemsWithStartPosition> onSetMediaItems(
        MediaSession mediaSession,
        ControllerInfo controller,
        List<MediaItem> mediaItems,
        int startIndex,
        long startPositionMs) {
      return Util.transformFutureAsync(
          onAddMediaItems(mediaSession, controller, mediaItems),
          (mediaItemList) ->
              Futures.immediateFuture(
                  new MediaItemsWithStartPosition(mediaItemList, startIndex, startPositionMs)));
    }
  }

  /** Representation of a list of {@linkplain MediaItem media items} and where to start playing. */
  @UnstableApi
  public static final class MediaItemsWithStartPosition {
    /** List of {@linkplain MediaItem media items}. */
    public final ImmutableList<MediaItem> mediaItems;
    /**
     * Index to start playing at in {@link #mediaItems}.
     *
     * <p>The start index in {@link #mediaItems} from which to start playing, or {@link
     * C#INDEX_UNSET} to start playing from the default index in the playlist.
     */
    public final int startIndex;
    /**
     * Position in milliseconds to start playing from in the starting media item.
     *
     * <p>The starting position in the media item from where to start playing, or {@link
     * C#TIME_UNSET} to start playing from the default position in the media item. This value is
     * ignored if {@code startIndex} is {@link C#INDEX_UNSET}.
     */
    public final long startPositionMs;

    /**
     * Creates an instance.
     *
     * @param mediaItems List of {@linkplain MediaItem media items}.
     * @param startIndex Index to start playing at in {@code mediaItems}, or {@link C#INDEX_UNSET}
     *     to start from the default index.
     * @param startPositionMs Position in milliseconds to start playing from in the starting media
     *     item, or {@link C#TIME_UNSET} to start from the default position.
     */
    public MediaItemsWithStartPosition(
        List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
      this.mediaItems = ImmutableList.copyOf(mediaItems);
      this.startIndex = startIndex;
      this.startPositionMs = startPositionMs;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof MediaItemsWithStartPosition)) {
        return false;
      }

      MediaItemsWithStartPosition other = (MediaItemsWithStartPosition) obj;

      return mediaItems.equals(other.mediaItems)
          && Util.areEqual(startIndex, other.startIndex)
          && Util.areEqual(startPositionMs, other.startPositionMs);
    }

    @Override
    public int hashCode() {
      int result = mediaItems.hashCode();
      result = 31 * result + startIndex;
      result = 31 * result + Longs.hashCode(startPositionMs);
      return result;
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
        Player.Commands availableCommands,
        boolean excludeTimeline,
        boolean excludeTracks,
        int controllerInterfaceVersion)
        throws RemoteException {}

    default void onPeriodicSessionPositionInfoChanged(
        int seq,
        SessionPositionInfo sessionPositionInfo,
        boolean canAccessCurrentMediaItem,
        boolean canAccessTimeline)
        throws RemoteException {}

    // Mostly matched with MediaController.ControllerCallback

    default void onDisconnected(int seq) throws RemoteException {}

    default void setCustomLayout(int seq, List<CommandButton> layout) throws RemoteException {}

    default void onSessionExtrasChanged(int seq, Bundle sessionExtras) throws RemoteException {}

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

    default void onTracksChanged(int seq, Tracks tracks) throws RemoteException {}

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
   * Listener for media session events.
   *
   * <p>All methods must be called on the main thread.
   */
  /* package */ interface Listener {

    /**
     * Called when the notification requires to be refreshed.
     *
     * @param session The media session for which the notification requires to be refreshed.
     */
    void onNotificationRefreshRequired(MediaSession session);

    /**
     * Called when the {@linkplain MediaSession session} receives the play command and requests from
     * the listener on whether the media can be played.
     *
     * @param session The media session which requests if the media can be played.
     * @return True if the media can be played, false otherwise.
     */
    boolean onPlayRequested(MediaSession session);
  }

  /**
   * A base class for {@link MediaSession.Builder} and {@link
   * MediaLibraryService.MediaLibrarySession.Builder}. Any changes to this class should be also
   * applied to the subclasses.
   */
  /* package */ abstract static class BuilderBase<
      SessionT extends MediaSession,
      BuilderT extends BuilderBase<SessionT, BuilderT, CallbackT>,
      CallbackT extends Callback> {

    /* package */ final Context context;
    /* package */ final Player player;
    /* package */ String id;
    /* package */ CallbackT callback;
    /* package */ @Nullable PendingIntent sessionActivity;
    /* package */ Bundle extras;
    /* package */ @MonotonicNonNull BitmapLoader bitmapLoader;

    public BuilderBase(Context context, Player player, CallbackT callback) {
      this.context = checkNotNull(context);
      this.player = checkNotNull(player);
      checkArgument(player.canAdvertiseSession());
      id = "";
      this.callback = callback;
      extras = Bundle.EMPTY;
    }

    @SuppressWarnings("unchecked")
    public BuilderT setSessionActivity(PendingIntent pendingIntent) {
      sessionActivity = checkNotNull(pendingIntent);
      return (BuilderT) this;
    }

    @SuppressWarnings("unchecked")
    public BuilderT setId(String id) {
      this.id = checkNotNull(id);
      return (BuilderT) this;
    }

    @SuppressWarnings("unchecked")
    /* package */ BuilderT setCallback(CallbackT callback) {
      this.callback = checkNotNull(callback);
      return (BuilderT) this;
    }

    @SuppressWarnings("unchecked")
    public BuilderT setExtras(Bundle extras) {
      this.extras = new Bundle(checkNotNull(extras));
      return (BuilderT) this;
    }

    @SuppressWarnings("unchecked")
    public BuilderT setBitmapLoader(BitmapLoader bitmapLoader) {
      this.bitmapLoader = bitmapLoader;
      return (BuilderT) this;
    }

    public abstract SessionT build();
  }
}
