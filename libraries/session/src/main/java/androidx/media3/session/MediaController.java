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

import static androidx.annotation.VisibleForTesting.NONE;
import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkNotEmpty;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Util.postOrRun;

import android.app.PendingIntent;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import androidx.annotation.FloatRange;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSourceBitmapLoader;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.errorprone.annotations.DoNotMock;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import org.checkerframework.checker.initialization.qual.NotOnlyInitialized;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * A controller that interacts with a {@link MediaSession}, a {@link MediaSessionService} hosting a
 * {@link MediaSession}, or a {@link MediaLibraryService} hosting a {@link
 * MediaLibraryService.MediaLibrarySession}. The {@link MediaSession} typically resides in a remote
 * process like another app but may be in the same process as this controller. It implements {@link
 * Player} and the player commands are sent to the underlying {@link Player} of the connected {@link
 * MediaSession}. It also has session-specific commands that can be handled by {@link
 * MediaSession.Callback}.
 *
 * <p>Topics covered here:
 *
 * <ol>
 *   <li><a href="#ControllerLifeCycle">Controller Lifecycle</a>
 *   <li><a href="#ThreadingModel">Threading Model</a>
 *   <li><a href="#PackageVisibilityFilter">Package Visibility Filter</a>
 *   <li><a href="#BackwardCompatibility">Backward Compatibility with legacy media sessions</a>
 * </ol>
 *
 * <h2 id="ControllerLifeCycle">Controller Lifecycle</h2>
 *
 * <p>When a controller is created with the {@link SessionToken} for a {@link MediaSession} (i.e.
 * session token type is {@link SessionToken#TYPE_SESSION}), the controller will connect to the
 * specific session.
 *
 * <p>When a controller is created with the {@link SessionToken} for a {@link MediaSessionService}
 * (i.e. session token type is {@link SessionToken#TYPE_SESSION_SERVICE} or {@link
 * SessionToken#TYPE_LIBRARY_SERVICE}), the controller binds to the service for connecting to a
 * {@link MediaSession} in it. {@link MediaSessionService} will provide a session to connect.
 *
 * <p>When you're done, use {@link #releaseFuture(Future)} or {@link #release()} to clean up
 * resources. This also helps the session service to be destroyed when there's no controller
 * associated with it. Releasing the controller will still deliver all pending commands sent to the
 * session and only unbind from the session service once these commands have been handled, or after
 * a timeout of {@link #RELEASE_UNBIND_TIMEOUT_MS}.
 *
 * <h2 id="ThreadingModel">Threading Model</h2>
 *
 * <p>Methods of this class should be called from the application thread associated with the {@link
 * #getApplicationLooper() application looper}. Otherwise, {@link IllegalStateException} will be
 * thrown. Also, the methods of {@link Player.Listener} and {@link Listener} will be called from the
 * application thread.
 *
 * <h2 id="PackageVisibilityFilter">Package Visibility Filter</h2>
 *
 * <p>The app targeting API level 30 or higher must include a {@code <queries>} element in their
 * manifest to connect to a service component of another app like {@link MediaSessionService},
 * {@link MediaLibraryService}, or {@link androidx.media.MediaBrowserServiceCompat}). See the
 * following example and <a href="//developer.android.com/training/package-visibility">this
 * guide</a> for more information.
 *
 * <pre>{@code
 * <!-- As intent actions -->
 * <intent>
 *   <action android:name="androidx.media3.session.MediaSessionService" />
 * </intent>
 * <intent>
 *   <action android:name="androidx.media3.session.MediaLibraryService" />
 * </intent>
 * <intent>
 *   <action android:name="android.media.browse.MediaBrowserService" />
 * </intent>
 * <!-- Or, as a package name -->
 * <package android:name="package_name_of_the_other_app" />
 * }</pre>
 *
 * <h2 id="BackwardCompatibility">Backward Compatibility with legacy media sessions</h2>
 *
 * <p>In addition to {@link MediaSession}, the controller also supports connecting to a legacy media
 * session - {@linkplain android.media.session.MediaSession framework session} and {@linkplain
 * MediaSessionCompat AndroidX session compat}.
 *
 * <p>To request legacy sessions to play media, use one of the {@link #setMediaItem} methods and set
 * either {@link MediaItem#mediaId}, {@link MediaItem.RequestMetadata#mediaUri} or {@link
 * MediaItem.RequestMetadata#searchQuery}. Once the controller is {@linkplain #prepare() prepared},
 * the controller triggers one of the following callbacks depending on the provided information and
 * the value of {@link #getPlayWhenReady()}:
 *
 * <ul>
 *   <li>{@link MediaSessionCompat.Callback#onPrepareFromUri onPrepareFromUri}
 *   <li>{@link MediaSessionCompat.Callback#onPlayFromUri onPlayFromUri}
 *   <li>{@link MediaSessionCompat.Callback#onPrepareFromMediaId onPrepareFromMediaId}
 *   <li>{@link MediaSessionCompat.Callback#onPlayFromMediaId onPlayFromMediaId}
 *   <li>{@link MediaSessionCompat.Callback#onPrepareFromSearch onPrepareFromSearch}
 *   <li>{@link MediaSessionCompat.Callback#onPlayFromSearch onPlayFromSearch}
 * </ul>
 *
 * Other playlist change methods, like {@link #addMediaItem} or {@link #removeMediaItem}, trigger
 * the {@link MediaSessionCompat.Callback#onAddQueueItem onAddQueueItem} and {@link
 * MediaSessionCompat.Callback#onRemoveQueueItem} onRemoveQueueItem} callbacks. Check {@link
 * #getAvailableCommands()} to see if playlist modifications are {@linkplain
 * androidx.media3.common.Player#COMMAND_CHANGE_MEDIA_ITEMS supported} by the legacy session.
 */
@DoNotMock
public class MediaController implements Player {

  /**
   * The timeout for handling pending commands after calling {@link #release()}. If the timeout is
   * reached, the controller is unbound from the session service even if commands are still pending.
   */
  @UnstableApi public static final long RELEASE_UNBIND_TIMEOUT_MS = 30_000;

  /**
   * Key to mark the connection hints of the media notification controller.
   *
   * <p>For a controller to be {@linkplain
   * MediaSession#isMediaNotificationController(MediaSession.ControllerInfo) recognized by the
   * session as the media notification controller}, this key needs to be used to {@linkplain
   * Bundle#putBoolean(String, boolean) set a boolean flag} in the connection hints to true. Only an
   * internal controller that has the same package name as the session can be used as a media
   * notification controller.
   *
   * <p>When using a session within a {@link MediaSessionService} or {@link MediaLibraryService},
   * the service connects a media notification controller automatically. Apps can do this for
   * standalone session to configure the platform session in the same way.
   */
  @UnstableApi
  public static final String KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG =
      "androidx.media3.session.MediaNotificationManager";

  private static final String TAG = "MediaController";

  private static final String WRONG_THREAD_ERROR_MESSAGE =
      "MediaController method is called from a wrong thread."
          + " See javadoc of MediaController for details.";

  /** A builder for {@link MediaController}. */
  public static final class Builder {

    private final Context context;
    private final SessionToken token;
    private Bundle connectionHints;
    private Listener listener;
    private Looper applicationLooper;
    private @MonotonicNonNull BitmapLoader bitmapLoader;

    /**
     * Creates a builder for {@link MediaController}.
     *
     * <p>The detailed behavior of the {@link MediaController} differs depending on the type of the
     * token as follows.
     *
     * <ol>
     *   <li>{@link SessionToken#TYPE_SESSION}: The controller connects to the specified session
     *       directly. It's recommended when you're sure which session to control, or you've got a
     *       token directly from the session app. This can be used only when the session for the
     *       token is running. Once the session is closed, the token becomes unusable.
     *   <li>{@link SessionToken#TYPE_SESSION_SERVICE} or {@link SessionToken#TYPE_LIBRARY_SERVICE}:
     *       The controller connects to the session provided by the {@link
     *       MediaSessionService#onGetSession(MediaSession.ControllerInfo)} or {@link
     *       MediaLibraryService#onGetSession(MediaSession.ControllerInfo)}. It's up to the service
     *       to decide which session should be returned for the connection. Use the {@link
     *       #getConnectedToken()} to know the connected session. This can be used regardless of
     *       whether the session app is running or not. The controller will bind to the service as
     *       long as it's connected to wake up and keep the service process running.
     * </ol>
     *
     * @param context The context.
     * @param token The token to connect to.
     */
    public Builder(Context context, SessionToken token) {
      this.context = checkNotNull(context);
      this.token = checkNotNull(token);
      connectionHints = Bundle.EMPTY;
      listener = new Listener() {};
      applicationLooper = Util.getCurrentOrMainLooper();
    }

    /**
     * Sets connection hints for the controller.
     *
     * <p>The hints are session-specific arguments sent to the session when connecting. The contents
     * of this bundle may affect the connection result.
     *
     * <p>The hints are only used when connecting to the {@link MediaSession}. They will be ignored
     * when connecting to {@link MediaSessionCompat}.
     *
     * @param connectionHints A bundle containing the connection hints.
     * @return The builder to allow chaining.
     */
    @CanIgnoreReturnValue
    public Builder setConnectionHints(Bundle connectionHints) {
      this.connectionHints = new Bundle(checkNotNull(connectionHints));
      return this;
    }

    /**
     * Sets a listener for the controller.
     *
     * @param listener The listener.
     * @return The builder to allow chaining.
     */
    @CanIgnoreReturnValue
    public Builder setListener(Listener listener) {
      this.listener = checkNotNull(listener);
      return this;
    }

    /**
     * Sets a {@link Looper} that must be used for all calls to the {@link Player} methods and that
     * is used to call {@link Player.Listener} methods on. The {@link Looper#myLooper()} current
     * looper} at that time this builder is created will be used if not specified. The {@link
     * Looper#getMainLooper() main looper} will be used if the current looper doesn't exist.
     *
     * @param looper The looper.
     * @return The builder to allow chaining.
     */
    @CanIgnoreReturnValue
    public Builder setApplicationLooper(Looper looper) {
      applicationLooper = checkNotNull(looper);
      return this;
    }

    /**
     * Sets a {@link BitmapLoader} for the {@link MediaController} to decode bitmaps from compressed
     * binary data. If not set, a {@link CacheBitmapLoader} that wraps a {@link
     * DataSourceBitmapLoader} will be used.
     *
     * @param bitmapLoader The bitmap loader.
     * @return The builder to allow chaining.
     */
    @UnstableApi
    @CanIgnoreReturnValue
    public Builder setBitmapLoader(BitmapLoader bitmapLoader) {
      this.bitmapLoader = checkNotNull(bitmapLoader);
      return this;
    }

    /**
     * Builds a {@link MediaController} asynchronously.
     *
     * <p>The controller instance can be obtained like the following example:
     *
     * <pre>{@code
     * MediaController.Builder builder = ...;
     * ListenableFuture<MediaController> future = builder.buildAsync();
     * future.addListener(() -> {
     *   try {
     *     MediaController controller = future.get();
     *     // The session accepted the connection.
     *   } catch (ExecutionException e) {
     *     if (e.getCause() instanceof SecurityException) {
     *       // The session rejected the connection.
     *     }
     *   }
     * }, ContextCompat.getMainExecutor());
     * }</pre>
     *
     * <p>The future must be kept by callers until the future is complete to get the controller
     * instance. Otherwise, the future might be garbage collected and the listener added by {@link
     * ListenableFuture#addListener(Runnable, Executor)} would never be called.
     *
     * @return A future of the controller instance.
     */
    public ListenableFuture<MediaController> buildAsync() {
      MediaControllerHolder<MediaController> holder =
          new MediaControllerHolder<>(applicationLooper);
      if (token.isLegacySession() && bitmapLoader == null) {
        bitmapLoader = new CacheBitmapLoader(new DataSourceBitmapLoader(context));
      }
      MediaController controller =
          new MediaController(
              context, token, connectionHints, listener, applicationLooper, holder, bitmapLoader);
      postOrRun(new Handler(applicationLooper), () -> holder.setController(controller));
      return holder;
    }
  }

  /**
   * A listener for events and incoming commands from {@link MediaSession}.
   *
   * <p>The methods will be called from the application thread associated with the {@link
   * #getApplicationLooper() application looper} of the controller.
   */
  public interface Listener {

    /**
     * Called when the controller is disconnected from the session. The controller becomes
     * unavailable afterwards and this listener won't be called anymore.
     *
     * <p>It will be also called after the {@link #release()}, so you can put clean up code here.
     * You don't need to call {@link #release()} after this.
     *
     * @param controller The controller.
     */
    default void onDisconnected(MediaController controller) {}

    /**
     * Called when the session sets the custom layout through {@link MediaSession#setCustomLayout}.
     *
     * <p>This method will be deprecated. Use {@link #onCustomLayoutChanged(MediaController, List)}
     * instead.
     *
     * <p>There is a slight difference in behaviour. This to be deprecated method may be
     * consecutively called with an unchanged custom layout passed into it, in which case the new
     * {@link #onCustomLayoutChanged(MediaController, List)} isn't called again for equal arguments.
     *
     * <p>Further, when the available commands of a controller change in a way that affect whether
     * buttons of the custom layout are enabled or disabled, the new callback {@link
     * #onCustomLayoutChanged(MediaController, List)} is called, in which case the deprecated
     * callback isn't called.
     */
    default ListenableFuture<SessionResult> onSetCustomLayout(
        MediaController controller, List<CommandButton> layout) {
      return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED));
    }

    /**
     * Called when the {@linkplain #getCustomLayout() custom layout} changed.
     *
     * <p>The custom layout can change when either the session {@linkplain
     * MediaSession#setCustomLayout changes the custom layout}, or when the session {@linkplain
     * MediaSession#setAvailableCommands(MediaSession.ControllerInfo, SessionCommands, Commands)
     * changes the available commands} for a controller that affect whether buttons of the custom
     * layout are enabled or disabled.
     *
     * @param controller The controller.
     * @param layout The ordered list of {@linkplain CommandButton command buttons}.
     */
    @UnstableApi
    default void onCustomLayoutChanged(MediaController controller, List<CommandButton> layout) {}

    /**
     * Called when the available session commands are changed by session.
     *
     * @param controller The controller.
     * @param commands The new available session commands.
     */
    default void onAvailableSessionCommandsChanged(
        MediaController controller, SessionCommands commands) {}

    /**
     * Called when the session sends a custom command through {@link
     * MediaSession#sendCustomCommand}.
     *
     * <p>Return a {@link ListenableFuture} to reply with a {@link SessionResult} to the session
     * asynchronously. You can also return a {@link SessionResult} directly by using Guava's {@link
     * Futures#immediateFuture(Object)}.
     *
     * <p>The default implementation returns {@link ListenableFuture} of {@link
     * SessionResult#RESULT_ERROR_NOT_SUPPORTED}.
     *
     * @param controller The controller.
     * @param command The custom command.
     * @param args The additional arguments. May be empty.
     * @return The result of handling the custom command.
     */
    default ListenableFuture<SessionResult> onCustomCommand(
        MediaController controller, SessionCommand command, Bundle args) {
      return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED));
    }

    /**
     * Called when the session extras are set on the session side.
     *
     * @param controller The controller.
     * @param extras The session extras that have been set on the session.
     */
    default void onExtrasChanged(MediaController controller, Bundle extras) {}

    /**
     * Called when the {@link PendingIntent} to launch the session activity {@link
     * MediaSession#setSessionActivity(PendingIntent) has been changed} on the session side.
     *
     * @param controller The controller.
     * @param sessionActivity The pending intent to launch the session activity.
     */
    @UnstableApi
    default void onSessionActivityChanged(
        MediaController controller, PendingIntent sessionActivity) {}
  }

  /* package */ interface ConnectionCallback {

    void onAccepted();

    void onRejected();
  }

  private final Timeline.Window window;

  private boolean released;

  @NotOnlyInitialized private final MediaControllerImpl impl;

  /* package */ final Listener listener;

  /* package */ final Handler applicationHandler;

  private long timeDiffMs;

  private boolean connectionNotified;

  /* package */ final ConnectionCallback connectionCallback;

  /** Creates a {@link MediaController} from the {@link SessionToken}. */
  // This constructor has to be package-private in order to prevent subclassing outside the package.
  /* package */ MediaController(
      Context context,
      SessionToken token,
      Bundle connectionHints,
      Listener listener,
      Looper applicationLooper,
      ConnectionCallback connectionCallback,
      @Nullable BitmapLoader bitmapLoader) {
    checkNotNull(context, "context must not be null");
    checkNotNull(token, "token must not be null");

    // Initialize default values.
    window = new Timeline.Window();
    timeDiffMs = C.TIME_UNSET;

    // Initialize members with params.
    this.listener = listener;
    applicationHandler = new Handler(applicationLooper);
    this.connectionCallback = connectionCallback;

    impl = createImpl(context, token, connectionHints, applicationLooper, bitmapLoader);
    impl.connect();
  }

  /* package */ @UnderInitialization
  MediaControllerImpl createImpl(
      @UnderInitialization MediaController this,
      Context context,
      SessionToken token,
      Bundle connectionHints,
      Looper applicationLooper,
      @Nullable BitmapLoader bitmapLoader) {
    if (token.isLegacySession()) {
      return new MediaControllerImplLegacy(
          context, this, token, applicationLooper, checkNotNull(bitmapLoader));
    } else {
      return new MediaControllerImplBase(context, this, token, connectionHints, applicationLooper);
    }
  }

  @Override
  public final void stop() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring stop().");
      return;
    }
    impl.stop();
  }

  /**
   * Releases the connection between {@link MediaController} and {@link MediaSession}. This method
   * must be called when the controller is no longer required. The controller must not be used after
   * calling this method.
   *
   * <p>This method does not call {@link Player#release()} of the underlying player in the session.
   */
  @Override
  public final void release() {
    verifyApplicationThread();
    if (released) {
      return;
    }
    released = true;
    applicationHandler.removeCallbacksAndMessages(null);
    try {
      impl.release();
    } catch (Exception e) {
      // Should not be here.
      Log.d(TAG, "Exception while releasing impl", e);
    }
    if (connectionNotified) {
      notifyControllerListener(listener -> listener.onDisconnected(this));
    } else {
      connectionNotified = true;
      connectionCallback.onRejected();
    }
  }

  /**
   * Releases the future controller returned by {@link Builder#buildAsync()}. It makes sure that the
   * controller is released by canceling the future if the future is not yet done.
   *
   * <p>Must be called on the {@linkplain #getApplicationLooper() application thread} of the media
   * controller.
   */
  public static void releaseFuture(Future<? extends MediaController> controllerFuture) {
    if (controllerFuture.cancel(/* mayInterruptIfRunning= */ false)) {
      // Successfully canceled the Future. The controller will be released by MediaControllerHolder.
      return;
    }
    MediaController controller;
    try {
      controller = Futures.getDone(controllerFuture);
    } catch (CancellationException | ExecutionException e) {
      Log.w(TAG, "MediaController future failed (so we couldn't release it)", e);
      return;
    }
    controller.release();
  }

  /**
   * Returns the {@link SessionToken} of the connected session, or {@code null} if it is not
   * connected.
   *
   * <p>This may differ from the {@link SessionToken} from the constructor. For example, if the
   * controller is created with the token for {@link MediaSessionService}, this will return a token
   * for the {@link MediaSession} in the service.
   */
  @Nullable
  public final SessionToken getConnectedToken() {
    return isConnected() ? impl.getConnectedToken() : null;
  }

  /** Returns whether this controller is connected to a {@link MediaSession} or not. */
  public final boolean isConnected() {
    return impl.isConnected();
  }

  @Override
  public final void play() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring play().");
      return;
    }
    impl.play();
  }

  @Override
  public final void pause() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring pause().");
      return;
    }
    impl.pause();
  }

  @Override
  public final void prepare() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring prepare().");
      return;
    }
    impl.prepare();
  }

  @Override
  public final void seekToDefaultPosition() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekTo().");
      return;
    }
    impl.seekToDefaultPosition();
  }

  @Override
  public final void seekToDefaultPosition(int mediaItemIndex) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekTo().");
      return;
    }
    impl.seekToDefaultPosition(mediaItemIndex);
  }

  @Override
  public final void seekTo(long positionMs) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekTo().");
      return;
    }
    impl.seekTo(positionMs);
  }

  @Override
  public final void seekTo(int mediaItemIndex, long positionMs) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekTo().");
      return;
    }
    impl.seekTo(mediaItemIndex, positionMs);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link MediaSessionCompat}, it returns {code 0}.
   */
  @Override
  public final long getSeekBackIncrement() {
    verifyApplicationThread();
    return isConnected() ? impl.getSeekBackIncrement() : 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link MediaSessionCompat}, it calls {@link
   * MediaControllerCompat.TransportControls#rewind()}.
   */
  @Override
  public final void seekBack() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekBack().");
      return;
    }
    impl.seekBack();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link MediaSessionCompat}, it returns {code 0}.
   */
  @Override
  public final long getSeekForwardIncrement() {
    verifyApplicationThread();
    return isConnected() ? impl.getSeekForwardIncrement() : 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link MediaSessionCompat}, it calls {@link
   * MediaControllerCompat.TransportControls#fastForward()}.
   */
  @Override
  public final void seekForward() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekForward().");
      return;
    }
    impl.seekForward();
  }

  /** Returns an intent for launching UI associated with the session if exists, or {@code null}. */
  @Nullable
  public final PendingIntent getSessionActivity() {
    return isConnected() ? impl.getSessionActivity() : null;
  }

  @Override
  @Nullable
  public final PlaybackException getPlayerError() {
    verifyApplicationThread();
    return isConnected() ? impl.getPlayerError() : null;
  }

  @Override
  public final void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThread();
    if (isConnected()) {
      impl.setPlayWhenReady(playWhenReady);
    }
  }

  @Override
  public final boolean getPlayWhenReady() {
    verifyApplicationThread();
    return isConnected() && impl.getPlayWhenReady();
  }

  @Override
  public final @PlaybackSuppressionReason int getPlaybackSuppressionReason() {
    verifyApplicationThread();
    return isConnected()
        ? impl.getPlaybackSuppressionReason()
        : Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  @Override
  public final @State int getPlaybackState() {
    verifyApplicationThread();
    return isConnected() ? impl.getPlaybackState() : Player.STATE_IDLE;
  }

  @Override
  public final boolean isPlaying() {
    verifyApplicationThread();
    return isConnected() && impl.isPlaying();
  }

  @Override
  public final boolean isLoading() {
    verifyApplicationThread();
    return isConnected() && impl.isLoading();
  }

  @Override
  public final long getDuration() {
    verifyApplicationThread();
    return isConnected() ? impl.getDuration() : C.TIME_UNSET;
  }

  @Override
  public final long getCurrentPosition() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentPosition() : 0;
  }

  @Override
  public final long getBufferedPosition() {
    verifyApplicationThread();
    return isConnected() ? impl.getBufferedPosition() : 0;
  }

  @Override
  @IntRange(from = 0, to = 100)
  public final int getBufferedPercentage() {
    verifyApplicationThread();
    return isConnected() ? impl.getBufferedPercentage() : 0;
  }

  @Override
  public final long getTotalBufferedDuration() {
    verifyApplicationThread();
    return isConnected() ? impl.getTotalBufferedDuration() : 0;
  }

  @Override
  public final long getCurrentLiveOffset() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentLiveOffset() : C.TIME_UNSET;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it's the same as {@link #getDuration()}
   * to match the behavior with {@link #getContentPosition()} and {@link
   * #getContentBufferedPosition()}.
   */
  @Override
  public final long getContentDuration() {
    verifyApplicationThread();
    return isConnected() ? impl.getContentDuration() : C.TIME_UNSET;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it's the same as {@link
   * #getCurrentPosition()} because content position isn't available.
   */
  @Override
  public final long getContentPosition() {
    verifyApplicationThread();
    return isConnected() ? impl.getContentPosition() : 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it's the same as {@link
   * #getBufferedPosition()} because content buffered position isn't available.
   */
  @Override
  public final long getContentBufferedPosition() {
    verifyApplicationThread();
    return isConnected() ? impl.getContentBufferedPosition() : 0;
  }

  @Override
  public final boolean isPlayingAd() {
    verifyApplicationThread();
    return isConnected() && impl.isPlayingAd();
  }

  @Override
  public final int getCurrentAdGroupIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentAdGroupIndex() : C.INDEX_UNSET;
  }

  @Override
  public final int getCurrentAdIndexInAdGroup() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentAdIndexInAdGroup() : C.INDEX_UNSET;
  }

  @Override
  public final void setPlaybackParameters(PlaybackParameters playbackParameters) {
    verifyApplicationThread();
    checkNotNull(playbackParameters, "playbackParameters must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setPlaybackParameters().");
      return;
    }
    impl.setPlaybackParameters(playbackParameters);
  }

  @Override
  public final void setPlaybackSpeed(float speed) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setPlaybackSpeed().");
      return;
    }
    impl.setPlaybackSpeed(speed);
  }

  @Override
  public final PlaybackParameters getPlaybackParameters() {
    verifyApplicationThread();
    return isConnected() ? impl.getPlaybackParameters() : PlaybackParameters.DEFAULT;
  }

  @Override
  public final AudioAttributes getAudioAttributes() {
    verifyApplicationThread();
    if (!isConnected()) {
      return AudioAttributes.DEFAULT;
    }
    return impl.getAudioAttributes();
  }

  /**
   * Requests that the connected {@link MediaSession} rates the media. This will cause the rating to
   * be set for the current user. The rating style must follow the user rating style from the
   * session. You can get the rating style from the session through the {@link
   * MediaMetadata#userRating}.
   *
   * <p>If the user rating was {@code null}, the media item does not accept setting user rating.
   *
   * @param mediaId The non-empty {@link MediaItem#mediaId}.
   * @param rating The rating to set.
   * @return A {@link ListenableFuture} of {@link SessionResult} representing the pending
   *     completion.
   */
  public final ListenableFuture<SessionResult> setRating(String mediaId, Rating rating) {
    verifyApplicationThread();
    checkNotNull(mediaId, "mediaId must not be null");
    checkNotEmpty(mediaId, "mediaId must not be empty");
    checkNotNull(rating, "rating must not be null");
    if (isConnected()) {
      return impl.setRating(mediaId, rating);
    }
    return createDisconnectedFuture();
  }

  /**
   * Requests that the connected {@link MediaSession} rates the current media item. This will cause
   * the rating to be set for the current user. The rating style must follow the user rating style
   * from the session. You can get the rating style from the session through the {@link
   * MediaMetadata#userRating}.
   *
   * <p>If the user rating was {@code null}, the media item does not accept setting user rating.
   *
   * @param rating The rating to set.
   * @return A {@link ListenableFuture} of {@link SessionResult} representing the pending
   *     completion.
   */
  public final ListenableFuture<SessionResult> setRating(Rating rating) {
    verifyApplicationThread();
    checkNotNull(rating, "rating must not be null");
    if (isConnected()) {
      return impl.setRating(rating);
    }
    return createDisconnectedFuture();
  }

  /**
   * Sends a custom command to the session.
   *
   * <p>A command is not accepted if it is not a custom command or the command is not in the list of
   * {@linkplain #getAvailableSessionCommands() available session commands}.
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, {@link SessionResult#resultCode} will
   * return the custom result code from the {@code android.os.ResultReceiver#onReceiveResult(int,
   * Bundle)} instead of the standard result codes defined in the {@link SessionResult}.
   *
   * @param command The custom command.
   * @param args The additional arguments. May be empty.
   * @return A {@link ListenableFuture} of {@link SessionResult} representing the pending
   *     completion.
   */
  public final ListenableFuture<SessionResult> sendCustomCommand(
      SessionCommand command, Bundle args) {
    verifyApplicationThread();
    checkNotNull(command, "command must not be null");
    checkArgument(
        command.commandCode == SessionCommand.COMMAND_CODE_CUSTOM,
        "command must be a custom command");
    if (isConnected()) {
      return impl.sendCustomCommand(command, args);
    }
    return createDisconnectedFuture();
  }

  /**
   * Returns the custom layout.
   *
   * <p>After being connected, a change of the custom layout is reported with {@link
   * Listener#onCustomLayoutChanged(MediaController, List)}.
   *
   * @return The custom layout.
   */
  @UnstableApi
  public final ImmutableList<CommandButton> getCustomLayout() {
    verifyApplicationThread();
    return isConnected() ? impl.getCustomLayout() : ImmutableList.of();
  }

  /**
   * Returns the session extras.
   *
   * <p>After being connected, {@link Listener#onExtrasChanged(MediaController, Bundle)} is called
   * when the extras on the session are set.
   *
   * @return The session extras.
   */
  @UnstableApi
  public final Bundle getSessionExtras() {
    verifyApplicationThread();
    return isConnected() ? impl.getSessionExtras() : Bundle.EMPTY;
  }

  /** Returns {@code null}. */
  @UnstableApi
  @Override
  @Nullable
  public final Object getCurrentManifest() {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Caveat: Some methods of the {@link Timeline} such as {@link Timeline#getPeriodByUid(Object,
   * Timeline.Period)}, {@link Timeline#getIndexOfPeriod(Object)}, and {@link
   * Timeline#getUidOfPeriod(int)} will throw {@link UnsupportedOperationException} because of the
   * limitation of restoring the instance sent from session as described in {@link
   * Timeline#CREATOR}.
   */
  @Override
  public final Timeline getCurrentTimeline() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentTimeline() : Timeline.EMPTY;
  }

  @Override
  public final void setMediaItem(MediaItem mediaItem) {
    verifyApplicationThread();
    checkNotNull(mediaItem, "mediaItems must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItem().");
      return;
    }
    impl.setMediaItem(mediaItem);
  }

  @Override
  public final void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    verifyApplicationThread();
    checkNotNull(mediaItem, "mediaItems must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItem().");
      return;
    }
    impl.setMediaItem(mediaItem, startPositionMs);
  }

  @Override
  public final void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    verifyApplicationThread();
    checkNotNull(mediaItem, "mediaItems must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItems().");
      return;
    }
    impl.setMediaItem(mediaItem, resetPosition);
  }

  @Override
  public final void setMediaItems(List<MediaItem> mediaItems) {
    verifyApplicationThread();
    checkNotNull(mediaItems, "mediaItems must not be null");
    for (int i = 0; i < mediaItems.size(); i++) {
      checkArgument(mediaItems.get(i) != null, "items must not contain null, index=" + i);
    }
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItems().");
      return;
    }
    impl.setMediaItems(mediaItems);
  }

  @Override
  public final void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
    verifyApplicationThread();
    checkNotNull(mediaItems, "mediaItems must not be null");
    for (int i = 0; i < mediaItems.size(); i++) {
      checkArgument(mediaItems.get(i) != null, "items must not contain null, index=" + i);
    }
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItems().");
      return;
    }
    impl.setMediaItems(mediaItems, resetPosition);
  }

  @Override
  public final void setMediaItems(
      List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    verifyApplicationThread();
    checkNotNull(mediaItems, "mediaItems must not be null");
    for (int i = 0; i < mediaItems.size(); i++) {
      checkArgument(mediaItems.get(i) != null, "items must not contain null, index=" + i);
    }
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItems().");
      return;
    }
    impl.setMediaItems(mediaItems, startIndex, startPositionMs);
  }

  @Override
  public final void setPlaylistMetadata(MediaMetadata playlistMetadata) {
    verifyApplicationThread();
    checkNotNull(playlistMetadata, "playlistMetadata must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setPlaylistMetadata().");
      return;
    }
    impl.setPlaylistMetadata(playlistMetadata);
  }

  @Override
  public final MediaMetadata getPlaylistMetadata() {
    verifyApplicationThread();
    return isConnected() ? impl.getPlaylistMetadata() : MediaMetadata.EMPTY;
  }

  @Override
  public final void addMediaItem(MediaItem mediaItem) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring addMediaItem().");
      return;
    }
    impl.addMediaItem(mediaItem);
  }

  @Override
  public final void addMediaItem(int index, MediaItem mediaItem) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring addMediaItem().");
      return;
    }
    impl.addMediaItem(index, mediaItem);
  }

  @Override
  public final void addMediaItems(List<MediaItem> mediaItems) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring addMediaItems().");
      return;
    }
    impl.addMediaItems(mediaItems);
  }

  @Override
  public final void addMediaItems(int index, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring addMediaItems().");
      return;
    }
    impl.addMediaItems(index, mediaItems);
  }

  @Override
  public final void removeMediaItem(int index) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring removeMediaItem().");
      return;
    }
    impl.removeMediaItem(index);
  }

  @Override
  public final void removeMediaItems(int fromIndex, int toIndex) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring removeMediaItems().");
      return;
    }
    impl.removeMediaItems(fromIndex, toIndex);
  }

  @Override
  public final void clearMediaItems() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearMediaItems().");
      return;
    }
    impl.clearMediaItems();
  }

  @Override
  public final void moveMediaItem(int currentIndex, int newIndex) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring moveMediaItem().");
      return;
    }
    impl.moveMediaItem(currentIndex, newIndex);
  }

  @Override
  public final void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring moveMediaItems().");
      return;
    }
    impl.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  @Override
  public final void replaceMediaItem(int index, MediaItem mediaItem) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring replaceMediaItem().");
      return;
    }
    impl.replaceMediaItem(index, mediaItem);
  }

  @Override
  public final void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring replaceMediaItems().");
      return;
    }
    impl.replaceMediaItems(fromIndex, toIndex, mediaItems);
  }

  /**
   * @deprecated Use {@link #isCurrentMediaItemDynamic()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final boolean isCurrentWindowDynamic() {
    return isCurrentMediaItemDynamic();
  }

  @Override
  public final boolean isCurrentMediaItemDynamic() {
    verifyApplicationThread();
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentMediaItemIndex(), window).isDynamic;
  }

  /**
   * @deprecated Use {@link #isCurrentMediaItemLive()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final boolean isCurrentWindowLive() {
    return isCurrentMediaItemLive();
  }

  @Override
  public final boolean isCurrentMediaItemLive() {
    verifyApplicationThread();
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentMediaItemIndex(), window).isLive();
  }

  /**
   * @deprecated Use {@link #isCurrentMediaItemSeekable()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final boolean isCurrentWindowSeekable() {
    return isCurrentMediaItemSeekable();
  }

  @Override
  public final boolean isCurrentMediaItemSeekable() {
    verifyApplicationThread();
    Timeline timeline = getCurrentTimeline();
    return !timeline.isEmpty() && timeline.getWindow(getCurrentMediaItemIndex(), window).isSeekable;
  }

  /**
   * {@inheritDoc}
   *
   * <p>The MediaController returns {@code false}.
   */
  @Override
  public final boolean canAdvertiseSession() {
    return false;
  }

  @Override
  @Nullable
  public final MediaItem getCurrentMediaItem() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? null
        : timeline.getWindow(getCurrentMediaItemIndex(), window).mediaItem;
  }

  @Override
  public final int getMediaItemCount() {
    return getCurrentTimeline().getWindowCount();
  }

  @Override
  public final MediaItem getMediaItemAt(int index) {
    return getCurrentTimeline().getWindow(index, window).mediaItem;
  }

  @Override
  public final int getCurrentPeriodIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentPeriodIndex() : C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #getCurrentMediaItemIndex()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final int getCurrentWindowIndex() {
    return getCurrentMediaItemIndex();
  }

  @Override
  public final int getCurrentMediaItemIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentMediaItemIndex() : C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #getPreviousMediaItemIndex()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final int getPreviousWindowIndex() {
    return getPreviousMediaItemIndex();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, this will always return {@link
   * C#INDEX_UNSET} even when {@link #hasPreviousMediaItem()} is {@code true}.
   */
  @Override
  public final int getPreviousMediaItemIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getPreviousMediaItemIndex() : C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #getNextMediaItemIndex()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final int getNextWindowIndex() {
    return getNextMediaItemIndex();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, this will always return {@link
   * C#INDEX_UNSET} even when {@link #hasNextMediaItem()} is {@code true}.
   */
  @Override
  public final int getNextMediaItemIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getNextMediaItemIndex() : C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #hasPreviousMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final boolean hasPrevious() {
    return hasPreviousMediaItem();
  }

  /**
   * @deprecated Use {@link #hasNextMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final boolean hasNext() {
    return hasNextMediaItem();
  }

  /**
   * @deprecated Use {@link #hasPreviousMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final boolean hasPreviousWindow() {
    return hasPreviousMediaItem();
  }

  /**
   * @deprecated Use {@link #hasNextMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final boolean hasNextWindow() {
    return hasNextMediaItem();
  }

  @Override
  public final boolean hasPreviousMediaItem() {
    verifyApplicationThread();
    return isConnected() && impl.hasPreviousMediaItem();
  }

  @Override
  public final boolean hasNextMediaItem() {
    verifyApplicationThread();
    return isConnected() && impl.hasNextMediaItem();
  }

  /**
   * @deprecated Use {@link #seekToPreviousMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final void previous() {
    seekToPreviousMediaItem();
  }

  /**
   * @deprecated Use {@link #seekToNextMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final void next() {
    seekToNextMediaItem();
  }

  /**
   * @deprecated Use {@link #seekToPreviousMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final void seekToPreviousWindow() {
    seekToPreviousMediaItem();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it's the same as {@link #seekToPrevious}.
   */
  @Override
  public final void seekToPreviousMediaItem() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekToPreviousMediaItem().");
      return;
    }
    impl.seekToPreviousMediaItem();
  }

  /**
   * @deprecated Use {@link #seekToNextMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public final void seekToNextWindow() {
    seekToNextMediaItem();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it's the same as {@link #seekToNext}.
   */
  @Override
  public final void seekToNextMediaItem() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekToNextMediaItem().");
      return;
    }
    impl.seekToNextMediaItem();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it won't update the current media item
   * index immediately because the previous media item index is unknown.
   */
  @Override
  public final void seekToPrevious() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekToPrevious().");
      return;
    }
    impl.seekToPrevious();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it always returns {@code 0}.
   */
  @Override
  public final long getMaxSeekToPreviousPosition() {
    verifyApplicationThread();
    return isConnected() ? impl.getMaxSeekToPreviousPosition() : 0L;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it won't update the current media item
   * index immediately because the previous media item index is unknown.
   */
  @Override
  public final void seekToNext() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekToNext().");
      return;
    }
    impl.seekToNext();
  }

  @Override
  public final @RepeatMode int getRepeatMode() {
    verifyApplicationThread();
    return isConnected() ? impl.getRepeatMode() : Player.REPEAT_MODE_OFF;
  }

  @Override
  public final void setRepeatMode(@RepeatMode int repeatMode) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setRepeatMode().");
      return;
    }
    impl.setRepeatMode(repeatMode);
  }

  @Override
  public final boolean getShuffleModeEnabled() {
    verifyApplicationThread();
    return isConnected() && impl.getShuffleModeEnabled();
  }

  @Override
  public final void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setShuffleMode().");
      return;
    }
    impl.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public final VideoSize getVideoSize() {
    verifyApplicationThread();
    return isConnected() ? impl.getVideoSize() : VideoSize.UNKNOWN;
  }

  @UnstableApi
  @Override
  public final Size getSurfaceSize() {
    verifyApplicationThread();
    return isConnected() ? impl.getSurfaceSize() : Size.UNKNOWN;
  }

  @Override
  public final void clearVideoSurface() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoSurface().");
      return;
    }
    impl.clearVideoSurface();
  }

  @Override
  public final void clearVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoSurface().");
      return;
    }
    impl.clearVideoSurface(surface);
  }

  @Override
  public final void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVideoSurface().");
      return;
    }
    impl.setVideoSurface(surface);
  }

  @Override
  public final void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVideoSurfaceHolder().");
      return;
    }
    impl.setVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public final void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoSurfaceHolder().");
      return;
    }
    impl.clearVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public final void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVideoSurfaceView().");
      return;
    }
    impl.setVideoSurfaceView(surfaceView);
  }

  @Override
  public final void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoSurfaceView().");
      return;
    }
    impl.clearVideoSurfaceView(surfaceView);
  }

  @Override
  public final void setVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVideoTextureView().");
      return;
    }
    impl.setVideoTextureView(textureView);
  }

  @Override
  public final void clearVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoTextureView().");
      return;
    }
    impl.clearVideoTextureView(textureView);
  }

  @Override
  public final CueGroup getCurrentCues() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentCues() : CueGroup.EMPTY_TIME_ZERO;
  }

  @Override
  @FloatRange(from = 0, to = 1)
  public final float getVolume() {
    verifyApplicationThread();
    return isConnected() ? impl.getVolume() : 1;
  }

  @Override
  public final void setVolume(@FloatRange(from = 0, to = 1) float volume) {
    verifyApplicationThread();
    checkArgument(volume >= 0 && volume <= 1, "volume must be between 0 and 1");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVolume().");
      return;
    }
    impl.setVolume(volume);
  }

  @Override
  public final DeviceInfo getDeviceInfo() {
    verifyApplicationThread();
    if (!isConnected()) {
      return DeviceInfo.UNKNOWN;
    }
    return impl.getDeviceInfo();
  }

  @Override
  @IntRange(from = 0)
  public final int getDeviceVolume() {
    verifyApplicationThread();
    if (!isConnected()) {
      return 0;
    }
    return impl.getDeviceVolume();
  }

  @Override
  public final boolean isDeviceMuted() {
    verifyApplicationThread();
    if (!isConnected()) {
      return false;
    }
    return impl.isDeviceMuted();
  }

  /**
   * @deprecated Use {@link #setDeviceVolume(int, int)} instead.
   */
  @Deprecated
  @Override
  public final void setDeviceVolume(@IntRange(from = 0) int volume) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setDeviceVolume().");
      return;
    }
    impl.setDeviceVolume(volume);
  }

  @Override
  public final void setDeviceVolume(@IntRange(from = 0) int volume, @C.VolumeFlags int flags) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setDeviceVolume().");
      return;
    }
    impl.setDeviceVolume(volume, flags);
  }

  /**
   * @deprecated Use {@link #increaseDeviceVolume(int)} instead.
   */
  @Deprecated
  @Override
  public final void increaseDeviceVolume() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring increaseDeviceVolume().");
      return;
    }
    impl.increaseDeviceVolume();
  }

  @Override
  public final void increaseDeviceVolume(@C.VolumeFlags int flags) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring increaseDeviceVolume().");
      return;
    }
    impl.increaseDeviceVolume(flags);
  }

  /**
   * @deprecated Use {@link #decreaseDeviceVolume(int)} instead.
   */
  @Deprecated
  @Override
  public final void decreaseDeviceVolume() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring decreaseDeviceVolume().");
      return;
    }
    impl.decreaseDeviceVolume();
  }

  @Override
  public final void decreaseDeviceVolume(@C.VolumeFlags int flags) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring decreaseDeviceVolume().");
      return;
    }
    impl.decreaseDeviceVolume(flags);
  }

  /**
   * @deprecated Use {@link #setDeviceMuted(boolean, int)} instead.
   */
  @Deprecated
  @Override
  public final void setDeviceMuted(boolean muted) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setDeviceMuted().");
      return;
    }
    impl.setDeviceMuted(muted);
  }

  @Override
  public final void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setDeviceMuted().");
      return;
    }
    impl.setDeviceMuted(muted, flags);
  }

  @Override
  public final void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setAudioAttributes().");
      return;
    }
    impl.setAudioAttributes(audioAttributes, handleAudioFocus);
  }

  @Override
  public final MediaMetadata getMediaMetadata() {
    verifyApplicationThread();
    return isConnected() ? impl.getMediaMetadata() : MediaMetadata.EMPTY;
  }

  @Override
  public final Tracks getCurrentTracks() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentTracks() : Tracks.EMPTY;
  }

  @Override
  public final TrackSelectionParameters getTrackSelectionParameters() {
    verifyApplicationThread();
    if (!isConnected()) {
      return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
    }
    return impl.getTrackSelectionParameters();
  }

  @Override
  public final void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setTrackSelectionParameters().");
    }
    impl.setTrackSelectionParameters(parameters);
  }

  @Override
  public final Looper getApplicationLooper() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return applicationHandler.getLooper();
  }

  /**
   * Gets the optional time diff (in milliseconds) used for calculating the current position, or
   * {@link C#TIME_UNSET} if no diff should be applied.
   */
  /* package */ final long getTimeDiffMs() {
    return timeDiffMs;
  }

  /**
   * Sets the time diff (in milliseconds) used when calculating the current position.
   *
   * @param timeDiffMs {@link C#TIME_UNSET} for reset.
   */
  @VisibleForTesting(otherwise = NONE)
  /* package */ final void setTimeDiffMs(long timeDiffMs) {
    verifyApplicationThread();
    this.timeDiffMs = timeDiffMs;
  }

  @Override
  public final void addListener(Player.Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    checkNotNull(listener, "listener must not be null");
    impl.addListener(listener);
  }

  @Override
  public final void removeListener(Player.Listener listener) {
    verifyApplicationThread();
    checkNotNull(listener, "listener must not be null");
    impl.removeListener(listener);
  }

  @Override
  public final boolean isCommandAvailable(@Command int command) {
    return getAvailableCommands().contains(command);
  }

  @Override
  public final Commands getAvailableCommands() {
    verifyApplicationThread();
    if (!isConnected()) {
      return Commands.EMPTY;
    }
    return impl.getAvailableCommands();
  }

  /**
   * Returns whether the {@link SessionCommand.CommandCode} is available. The {@code
   * sessionCommandCode} must not be {@link SessionCommand#COMMAND_CODE_CUSTOM}. Use {@link
   * #isSessionCommandAvailable(SessionCommand)} for custom commands.
   */
  public final boolean isSessionCommandAvailable(
      @SessionCommand.CommandCode int sessionCommandCode) {
    return getAvailableSessionCommands().contains(sessionCommandCode);
  }

  /** Returns whether the {@link SessionCommand} is available. */
  public final boolean isSessionCommandAvailable(SessionCommand sessionCommand) {
    return getAvailableSessionCommands().contains(sessionCommand);
  }

  /**
   * Returns the current available session commands from {@link
   * Listener#onAvailableSessionCommandsChanged(MediaController, SessionCommands)}, or {@link
   * SessionCommands#EMPTY} if it is not connected.
   *
   * @return The available session commands.
   */
  public final SessionCommands getAvailableSessionCommands() {
    verifyApplicationThread();
    if (!isConnected()) {
      return SessionCommands.EMPTY;
    }
    return impl.getAvailableSessionCommands();
  }

  private static ListenableFuture<SessionResult> createDisconnectedFuture() {
    return Futures.immediateFuture(
        new SessionResult(SessionResult.RESULT_ERROR_SESSION_DISCONNECTED));
  }

  /* package */ final void runOnApplicationLooper(Runnable runnable) {
    postOrRun(applicationHandler, runnable);
  }

  /* package */ final void notifyControllerListener(Consumer<Listener> listenerConsumer) {
    checkState(Looper.myLooper() == getApplicationLooper());
    listenerConsumer.accept(listener);
  }

  /* package */ final void notifyAccepted() {
    checkState(Looper.myLooper() == getApplicationLooper());
    checkState(!connectionNotified);
    connectionNotified = true;
    connectionCallback.onAccepted();
  }

  /** Returns the binder object used to connect to the session. */
  @Nullable
  @VisibleForTesting(otherwise = NONE)
  /* package */ final IMediaController getBinder() {
    return impl.getBinder();
  }

  private void verifyApplicationThread() {
    checkState(Looper.myLooper() == getApplicationLooper(), WRONG_THREAD_ERROR_MESSAGE);
  }

  /* package */ interface MediaControllerImpl {

    void connect(@UnderInitialization MediaControllerImpl this);

    void addListener(Player.Listener listener);

    void removeListener(Player.Listener listener);

    @Nullable
    SessionToken getConnectedToken();

    boolean isConnected();

    void play();

    void pause();

    void setPlayWhenReady(boolean playWhenReady);

    void prepare();

    void stop();

    void release();

    void seekToDefaultPosition();

    void seekToDefaultPosition(int mediaItemIndex);

    void seekTo(long positionMs);

    void seekTo(int mediaItemIndex, long positionMs);

    long getSeekBackIncrement();

    void seekBack();

    long getSeekForwardIncrement();

    void seekForward();

    @Nullable
    PendingIntent getSessionActivity();

    @Nullable
    PlaybackException getPlayerError();

    long getDuration();

    long getCurrentPosition();

    long getBufferedPosition();

    int getBufferedPercentage();

    long getTotalBufferedDuration();

    long getCurrentLiveOffset();

    long getContentDuration();

    long getContentPosition();

    long getContentBufferedPosition();

    boolean isPlayingAd();

    int getCurrentAdGroupIndex();

    int getCurrentAdIndexInAdGroup();

    void setPlaybackParameters(PlaybackParameters playbackParameters);

    void setPlaybackSpeed(float speed);

    PlaybackParameters getPlaybackParameters();

    AudioAttributes getAudioAttributes();

    ListenableFuture<SessionResult> setRating(String mediaId, Rating rating);

    ListenableFuture<SessionResult> setRating(Rating rating);

    ListenableFuture<SessionResult> sendCustomCommand(SessionCommand command, Bundle args);

    ImmutableList<CommandButton> getCustomLayout();

    Bundle getSessionExtras();

    Timeline getCurrentTimeline();

    void setMediaItem(MediaItem mediaItem);

    void setMediaItem(MediaItem mediaItem, long startPositionMs);

    void setMediaItem(MediaItem mediaItem, boolean resetPosition);

    void setMediaItems(List<MediaItem> mediaItems);

    void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition);

    void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs);

    void setPlaylistMetadata(MediaMetadata playlistMetadata);

    MediaMetadata getPlaylistMetadata();

    void addMediaItem(MediaItem mediaItem);

    void addMediaItem(int index, MediaItem mediaItem);

    void addMediaItems(List<MediaItem> mediaItems);

    void addMediaItems(int index, List<MediaItem> mediaItems);

    void removeMediaItem(int index);

    void removeMediaItems(int fromIndex, int toIndex);

    void clearMediaItems();

    void moveMediaItem(int currentIndex, int newIndex);

    void moveMediaItems(int fromIndex, int toIndex, int newIndex);

    void replaceMediaItem(int index, MediaItem mediaItem);

    void replaceMediaItems(int fromIndex, int toIndex, List<MediaItem> mediaItems);

    int getCurrentPeriodIndex();

    int getCurrentMediaItemIndex();

    int getPreviousMediaItemIndex();

    int getNextMediaItemIndex();

    boolean hasPreviousMediaItem();

    boolean hasNextMediaItem();

    void seekToPreviousMediaItem();

    void seekToNextMediaItem();

    void seekToPrevious();

    long getMaxSeekToPreviousPosition();

    void seekToNext();

    @RepeatMode
    int getRepeatMode();

    void setRepeatMode(@RepeatMode int repeatMode);

    boolean getShuffleModeEnabled();

    void setShuffleModeEnabled(boolean shuffleModeEnabled);

    VideoSize getVideoSize();

    Size getSurfaceSize();

    void clearVideoSurface();

    void clearVideoSurface(@Nullable Surface surface);

    void setVideoSurface(@Nullable Surface surface);

    void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

    void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder);

    void setVideoSurfaceView(@Nullable SurfaceView surfaceView);

    void clearVideoSurfaceView(@Nullable SurfaceView surfaceView);

    void setVideoTextureView(@Nullable TextureView textureView);

    void clearVideoTextureView(@Nullable TextureView textureView);

    CueGroup getCurrentCues();

    float getVolume();

    void setVolume(float volume);

    DeviceInfo getDeviceInfo();

    int getDeviceVolume();

    boolean isDeviceMuted();

    void setDeviceVolume(int volume);

    void setDeviceVolume(int volume, @C.VolumeFlags int flags);

    void increaseDeviceVolume();

    void increaseDeviceVolume(@C.VolumeFlags int flags);

    void decreaseDeviceVolume();

    void decreaseDeviceVolume(@C.VolumeFlags int flags);

    void setDeviceMuted(boolean muted);

    void setDeviceMuted(boolean muted, @C.VolumeFlags int flags);

    void setAudioAttributes(AudioAttributes audioAttributes, boolean handleAudioFocus);

    boolean getPlayWhenReady();

    @PlaybackSuppressionReason
    int getPlaybackSuppressionReason();

    @State
    int getPlaybackState();

    boolean isPlaying();

    boolean isLoading();

    MediaMetadata getMediaMetadata();

    Commands getAvailableCommands();

    Tracks getCurrentTracks();

    TrackSelectionParameters getTrackSelectionParameters();

    void setTrackSelectionParameters(TrackSelectionParameters parameters);

    SessionCommands getAvailableSessionCommands();

    // Internally used methods
    Context getContext();

    @Nullable
    MediaBrowserCompat getBrowserCompat();

    @Nullable
    IMediaController getBinder();
  }
}
