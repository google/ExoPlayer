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
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
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
 * androidx.media3.common.Player.Command#COMMAND_CHANGE_MEDIA_ITEMS supported} by the legacy
 * session.
 */
public class MediaController implements Player {

  /**
   * The timeout for handling pending commands after calling {@link #release()}. If the timeout is
   * reached, the controller is unbound from the session service even if commands are still pending.
   */
  @UnstableApi public static final long RELEASE_UNBIND_TIMEOUT_MS = 30_000;

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
     * binary data. If not set, a {@link CacheBitmapLoader} that wraps a {@link SimpleBitmapLoader}
     * will be used.
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
        bitmapLoader = new CacheBitmapLoader(new SimpleBitmapLoader());
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
     * <p>Return a {@link ListenableFuture} to reply with a {@link SessionResult} to the session
     * asynchronously. You can also return a {@link SessionResult} directly by using Guava's {@link
     * Futures#immediateFuture(Object)}.
     *
     * <p>The default implementation returns a {@link ListenableFuture} of {@link
     * SessionResult#RESULT_ERROR_NOT_SUPPORTED}.
     *
     * @param controller The controller.
     * @param layout The ordered list of {@link CommandButton}.
     * @return The result of handling the custom layout.
     */
    default ListenableFuture<SessionResult> onSetCustomLayout(
        MediaController controller, List<CommandButton> layout) {
      return Futures.immediateFuture(new SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED));
    }

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
     * Called when the session extras have changed.
     *
     * @param controller The controller.
     * @param extras The session extras that have changed.
     */
    default void onExtrasChanged(MediaController controller, Bundle extras) {}
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
  public void stop() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring stop().");
      return;
    }
    impl.stop();
  }

  /**
   * @deprecated Use {@link #stop()} and {@link #clearMediaItems()} (if {@code reset} is true) or
   *     just {@link #stop()} (if {@code reset} is false). Any player error will be cleared when
   *     {@link #prepare() re-preparing} the player.
   */
  @UnstableApi
  @Deprecated
  @Override
  public void stop(boolean reset) {
    stop();
    if (reset) {
      clearMediaItems();
    }
  }

  /**
   * Releases the connection between {@link MediaController} and {@link MediaSession}. This method
   * must be called when the controller is no longer required. The controller must not be used after
   * calling this method.
   *
   * <p>This method does not call {@link Player#release()} of the underlying player in the session.
   */
  @Override
  public void release() {
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
   */
  public static void releaseFuture(Future<? extends MediaController> controllerFuture) {
    if (!controllerFuture.isDone()) {
      controllerFuture.cancel(/* mayInterruptIfRunning= */ true);
      return;
    }
    MediaController controller;
    try {
      controller = controllerFuture.get();
    } catch (CancellationException | ExecutionException | InterruptedException e) {
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
  public SessionToken getConnectedToken() {
    return isConnected() ? impl.getConnectedToken() : null;
  }

  /** Returns whether this controller is connected to a {@link MediaSession} or not. */
  public boolean isConnected() {
    return impl.isConnected();
  }

  @Override
  public void play() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring play().");
      return;
    }
    impl.play();
  }

  @Override
  public void pause() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring pause().");
      return;
    }
    impl.pause();
  }

  @Override
  public void prepare() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring prepare().");
      return;
    }
    impl.prepare();
  }

  @Override
  public void seekToDefaultPosition() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekTo().");
      return;
    }
    impl.seekToDefaultPosition();
  }

  @Override
  public void seekToDefaultPosition(int mediaItemIndex) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekTo().");
      return;
    }
    impl.seekToDefaultPosition(mediaItemIndex);
  }

  @Override
  public void seekTo(long positionMs) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekTo().");
      return;
    }
    impl.seekTo(positionMs);
  }

  @Override
  public void seekTo(int mediaItemIndex, long positionMs) {
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
  public long getSeekBackIncrement() {
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
  public void seekBack() {
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
  public long getSeekForwardIncrement() {
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
  public void seekForward() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekForward().");
      return;
    }
    impl.seekForward();
  }

  /** Returns an intent for launching UI associated with the session if exists, or {@code null}. */
  @Nullable
  public PendingIntent getSessionActivity() {
    return isConnected() ? impl.getSessionActivity() : null;
  }

  @Override
  @Nullable
  public PlaybackException getPlayerError() {
    verifyApplicationThread();
    return isConnected() ? impl.getPlayerError() : null;
  }

  @Override
  public void setPlayWhenReady(boolean playWhenReady) {
    verifyApplicationThread();
    if (isConnected()) {
      impl.setPlayWhenReady(playWhenReady);
    }
  }

  @Override
  public boolean getPlayWhenReady() {
    verifyApplicationThread();
    return isConnected() && impl.getPlayWhenReady();
  }

  @Override
  public @PlaybackSuppressionReason int getPlaybackSuppressionReason() {
    verifyApplicationThread();
    return isConnected()
        ? impl.getPlaybackSuppressionReason()
        : Player.PLAYBACK_SUPPRESSION_REASON_NONE;
  }

  @Override
  public @State int getPlaybackState() {
    verifyApplicationThread();
    return isConnected() ? impl.getPlaybackState() : Player.STATE_IDLE;
  }

  @Override
  public boolean isPlaying() {
    verifyApplicationThread();
    return isConnected() && impl.isPlaying();
  }

  @Override
  public boolean isLoading() {
    verifyApplicationThread();
    return isConnected() && impl.isLoading();
  }

  @Override
  public long getDuration() {
    verifyApplicationThread();
    return isConnected() ? impl.getDuration() : C.TIME_UNSET;
  }

  @Override
  public long getCurrentPosition() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentPosition() : 0;
  }

  @Override
  public long getBufferedPosition() {
    verifyApplicationThread();
    return isConnected() ? impl.getBufferedPosition() : 0;
  }

  @Override
  @IntRange(from = 0, to = 100)
  public int getBufferedPercentage() {
    verifyApplicationThread();
    return isConnected() ? impl.getBufferedPercentage() : 0;
  }

  @Override
  public long getTotalBufferedDuration() {
    verifyApplicationThread();
    return isConnected() ? impl.getTotalBufferedDuration() : 0;
  }

  @Override
  public long getCurrentLiveOffset() {
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
  public long getContentDuration() {
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
  public long getContentPosition() {
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
  public long getContentBufferedPosition() {
    verifyApplicationThread();
    return isConnected() ? impl.getContentBufferedPosition() : 0;
  }

  @Override
  public boolean isPlayingAd() {
    verifyApplicationThread();
    return isConnected() && impl.isPlayingAd();
  }

  @Override
  public int getCurrentAdGroupIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentAdGroupIndex() : C.INDEX_UNSET;
  }

  @Override
  public int getCurrentAdIndexInAdGroup() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentAdIndexInAdGroup() : C.INDEX_UNSET;
  }

  @Override
  public void setPlaybackParameters(PlaybackParameters playbackParameters) {
    verifyApplicationThread();
    checkNotNull(playbackParameters, "playbackParameters must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setPlaybackParameters().");
      return;
    }
    impl.setPlaybackParameters(playbackParameters);
  }

  @Override
  public void setPlaybackSpeed(float speed) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setPlaybackSpeed().");
      return;
    }
    impl.setPlaybackSpeed(speed);
  }

  @Override
  public PlaybackParameters getPlaybackParameters() {
    verifyApplicationThread();
    return isConnected() ? impl.getPlaybackParameters() : PlaybackParameters.DEFAULT;
  }

  @Override
  public AudioAttributes getAudioAttributes() {
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
  public ListenableFuture<SessionResult> setRating(String mediaId, Rating rating) {
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
  public ListenableFuture<SessionResult> setRating(Rating rating) {
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
  public ListenableFuture<SessionResult> sendCustomCommand(SessionCommand command, Bundle args) {
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

  /** Returns {@code null}. */
  @UnstableApi
  @Override
  @Nullable
  public Object getCurrentManifest() {
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
  public Timeline getCurrentTimeline() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentTimeline() : Timeline.EMPTY;
  }

  @Override
  public void setMediaItem(MediaItem mediaItem) {
    verifyApplicationThread();
    checkNotNull(mediaItem, "mediaItems must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItem().");
      return;
    }
    impl.setMediaItem(mediaItem);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, long startPositionMs) {
    verifyApplicationThread();
    checkNotNull(mediaItem, "mediaItems must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItem().");
      return;
    }
    impl.setMediaItem(mediaItem, startPositionMs);
  }

  @Override
  public void setMediaItem(MediaItem mediaItem, boolean resetPosition) {
    verifyApplicationThread();
    checkNotNull(mediaItem, "mediaItems must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setMediaItems().");
      return;
    }
    impl.setMediaItem(mediaItem, resetPosition);
  }

  @Override
  public void setMediaItems(List<MediaItem> mediaItems) {
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
  public void setMediaItems(List<MediaItem> mediaItems, boolean resetPosition) {
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
  public void setMediaItems(List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
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
  public void setPlaylistMetadata(MediaMetadata playlistMetadata) {
    verifyApplicationThread();
    checkNotNull(playlistMetadata, "playlistMetadata must not be null");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setPlaylistMetadata().");
      return;
    }
    impl.setPlaylistMetadata(playlistMetadata);
  }

  @Override
  public MediaMetadata getPlaylistMetadata() {
    verifyApplicationThread();
    return isConnected() ? impl.getPlaylistMetadata() : MediaMetadata.EMPTY;
  }

  @Override
  public void addMediaItem(MediaItem mediaItem) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring addMediaItem().");
      return;
    }
    impl.addMediaItem(mediaItem);
  }

  @Override
  public void addMediaItem(int index, MediaItem mediaItem) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring addMediaItem().");
      return;
    }
    impl.addMediaItem(index, mediaItem);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, this doesn't atomically add items.
   */
  @Override
  public void addMediaItems(List<MediaItem> mediaItems) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring addMediaItems().");
      return;
    }
    impl.addMediaItems(mediaItems);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, this doesn't atomically add items.
   */
  @Override
  public void addMediaItems(int index, List<MediaItem> mediaItems) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring addMediaItems().");
      return;
    }
    impl.addMediaItems(index, mediaItems);
  }

  @Override
  public void removeMediaItem(int index) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring removeMediaItem().");
      return;
    }
    impl.removeMediaItem(index);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, this doesn't atomically remove items.
   */
  @Override
  public void removeMediaItems(int fromIndex, int toIndex) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring removeMediaItems().");
      return;
    }
    impl.removeMediaItems(fromIndex, toIndex);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, this doesn't atomically clear items.
   */
  @Override
  public void clearMediaItems() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearMediaItems().");
      return;
    }
    impl.clearMediaItems();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, this doesn't atomically move items.
   */
  @Override
  public void moveMediaItem(int currentIndex, int newIndex) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring moveMediaItem().");
      return;
    }
    impl.moveMediaItem(currentIndex, newIndex);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, this doesn't atomically move items.
   */
  @Override
  public void moveMediaItems(int fromIndex, int toIndex, int newIndex) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring moveMediaItems().");
      return;
    }
    impl.moveMediaItems(fromIndex, toIndex, newIndex);
  }

  /**
   * @deprecated Use {@link #isCurrentMediaItemDynamic()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public boolean isCurrentWindowDynamic() {
    return isCurrentMediaItemDynamic();
  }

  @Override
  public boolean isCurrentMediaItemDynamic() {
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
  public boolean isCurrentWindowLive() {
    return isCurrentMediaItemLive();
  }

  @Override
  public boolean isCurrentMediaItemLive() {
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
  public boolean isCurrentWindowSeekable() {
    return isCurrentMediaItemSeekable();
  }

  @Override
  public boolean isCurrentMediaItemSeekable() {
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
  public boolean canAdvertiseSession() {
    return false;
  }

  @Override
  @Nullable
  public MediaItem getCurrentMediaItem() {
    Timeline timeline = getCurrentTimeline();
    return timeline.isEmpty()
        ? null
        : timeline.getWindow(getCurrentMediaItemIndex(), window).mediaItem;
  }

  @Override
  public int getMediaItemCount() {
    return getCurrentTimeline().getWindowCount();
  }

  @Override
  public MediaItem getMediaItemAt(int index) {
    return getCurrentTimeline().getWindow(index, window).mediaItem;
  }

  @Override
  public int getCurrentPeriodIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentPeriodIndex() : C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #getCurrentMediaItemIndex()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public int getCurrentWindowIndex() {
    return getCurrentMediaItemIndex();
  }

  @Override
  public int getCurrentMediaItemIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentMediaItemIndex() : C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #getPreviousMediaItemIndex()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public int getPreviousWindowIndex() {
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
  public int getPreviousMediaItemIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getPreviousMediaItemIndex() : C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #getNextMediaItemIndex()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public int getNextWindowIndex() {
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
  public int getNextMediaItemIndex() {
    verifyApplicationThread();
    return isConnected() ? impl.getNextMediaItemIndex() : C.INDEX_UNSET;
  }

  /**
   * @deprecated Use {@link #hasPreviousMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public boolean hasPrevious() {
    return hasPreviousMediaItem();
  }

  /**
   * @deprecated Use {@link #hasNextMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public boolean hasNext() {
    return hasNextMediaItem();
  }

  /**
   * @deprecated Use {@link #hasPreviousMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public boolean hasPreviousWindow() {
    return hasPreviousMediaItem();
  }

  /**
   * @deprecated Use {@link #hasNextMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public boolean hasNextWindow() {
    return hasNextMediaItem();
  }

  @Override
  public boolean hasPreviousMediaItem() {
    verifyApplicationThread();
    return isConnected() && impl.hasPreviousMediaItem();
  }

  @Override
  public boolean hasNextMediaItem() {
    verifyApplicationThread();
    return isConnected() && impl.hasNextMediaItem();
  }

  /**
   * @deprecated Use {@link #seekToPreviousMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public void previous() {
    seekToPreviousMediaItem();
  }

  /**
   * @deprecated Use {@link #seekToNextMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public void next() {
    seekToNextMediaItem();
  }

  /**
   * @deprecated Use {@link #seekToPreviousMediaItem()} instead.
   */
  @UnstableApi
  @Deprecated
  @Override
  public void seekToPreviousWindow() {
    seekToPreviousMediaItem();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it's the same as {@link #seekToPrevious}.
   */
  @Override
  public void seekToPreviousMediaItem() {
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
  public void seekToNextWindow() {
    seekToNextMediaItem();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Interoperability: When connected to {@link
   * android.support.v4.media.session.MediaSessionCompat}, it's the same as {@link #seekToNext}.
   */
  @Override
  public void seekToNextMediaItem() {
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
  public void seekToPrevious() {
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
  public long getMaxSeekToPreviousPosition() {
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
  public void seekToNext() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring seekToNext().");
      return;
    }
    impl.seekToNext();
  }

  @Override
  public @RepeatMode int getRepeatMode() {
    verifyApplicationThread();
    return isConnected() ? impl.getRepeatMode() : Player.REPEAT_MODE_OFF;
  }

  @Override
  public void setRepeatMode(@RepeatMode int repeatMode) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setRepeatMode().");
      return;
    }
    impl.setRepeatMode(repeatMode);
  }

  @Override
  public boolean getShuffleModeEnabled() {
    verifyApplicationThread();
    return isConnected() && impl.getShuffleModeEnabled();
  }

  @Override
  public void setShuffleModeEnabled(boolean shuffleModeEnabled) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setShuffleMode().");
      return;
    }
    impl.setShuffleModeEnabled(shuffleModeEnabled);
  }

  @Override
  public VideoSize getVideoSize() {
    verifyApplicationThread();
    return isConnected() ? impl.getVideoSize() : VideoSize.UNKNOWN;
  }

  @UnstableApi
  @Override
  public Size getSurfaceSize() {
    verifyApplicationThread();
    return isConnected() ? impl.getSurfaceSize() : Size.UNKNOWN;
  }

  @Override
  public void clearVideoSurface() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoSurface().");
      return;
    }
    impl.clearVideoSurface();
  }

  @Override
  public void clearVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoSurface().");
      return;
    }
    impl.clearVideoSurface(surface);
  }

  @Override
  public void setVideoSurface(@Nullable Surface surface) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVideoSurface().");
      return;
    }
    impl.setVideoSurface(surface);
  }

  @Override
  public void setVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVideoSurfaceHolder().");
      return;
    }
    impl.setVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void clearVideoSurfaceHolder(@Nullable SurfaceHolder surfaceHolder) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoSurfaceHolder().");
      return;
    }
    impl.clearVideoSurfaceHolder(surfaceHolder);
  }

  @Override
  public void setVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVideoSurfaceView().");
      return;
    }
    impl.setVideoSurfaceView(surfaceView);
  }

  @Override
  public void clearVideoSurfaceView(@Nullable SurfaceView surfaceView) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoSurfaceView().");
      return;
    }
    impl.clearVideoSurfaceView(surfaceView);
  }

  @Override
  public void setVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVideoTextureView().");
      return;
    }
    impl.setVideoTextureView(textureView);
  }

  @Override
  public void clearVideoTextureView(@Nullable TextureView textureView) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring clearVideoTextureView().");
      return;
    }
    impl.clearVideoTextureView(textureView);
  }

  @Override
  public CueGroup getCurrentCues() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentCues() : CueGroup.EMPTY_TIME_ZERO;
  }

  @Override
  @FloatRange(from = 0, to = 1)
  public float getVolume() {
    verifyApplicationThread();
    return isConnected() ? impl.getVolume() : 1;
  }

  @Override
  public void setVolume(@FloatRange(from = 0, to = 1) float volume) {
    verifyApplicationThread();
    checkArgument(volume >= 0 && volume <= 1, "volume must be between 0 and 1");
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setVolume().");
      return;
    }
    impl.setVolume(volume);
  }

  @Override
  public DeviceInfo getDeviceInfo() {
    verifyApplicationThread();
    if (!isConnected()) {
      return DeviceInfo.UNKNOWN;
    }
    return impl.getDeviceInfo();
  }

  @Override
  @IntRange(from = 0)
  public int getDeviceVolume() {
    verifyApplicationThread();
    if (!isConnected()) {
      return 0;
    }
    return impl.getDeviceVolume();
  }

  @Override
  public boolean isDeviceMuted() {
    verifyApplicationThread();
    if (!isConnected()) {
      return false;
    }
    return impl.isDeviceMuted();
  }

  @Override
  public void setDeviceVolume(@IntRange(from = 0) int volume) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setDeviceVolume().");
      return;
    }
    impl.setDeviceVolume(volume);
  }

  @Override
  public void increaseDeviceVolume() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring increaseDeviceVolume().");
      return;
    }
    impl.increaseDeviceVolume();
  }

  @Override
  public void decreaseDeviceVolume() {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring decreaseDeviceVolume().");
      return;
    }
    impl.decreaseDeviceVolume();
  }

  @Override
  public void setDeviceMuted(boolean muted) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setDeviceMuted().");
      return;
    }
    impl.setDeviceMuted(muted);
  }

  @Override
  public MediaMetadata getMediaMetadata() {
    verifyApplicationThread();
    return isConnected() ? impl.getMediaMetadata() : MediaMetadata.EMPTY;
  }

  @Override
  public Tracks getCurrentTracks() {
    verifyApplicationThread();
    return isConnected() ? impl.getCurrentTracks() : Tracks.EMPTY;
  }

  @Override
  public TrackSelectionParameters getTrackSelectionParameters() {
    verifyApplicationThread();
    if (!isConnected()) {
      return TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT;
    }
    return impl.getTrackSelectionParameters();
  }

  @Override
  public void setTrackSelectionParameters(TrackSelectionParameters parameters) {
    verifyApplicationThread();
    if (!isConnected()) {
      Log.w(TAG, "The controller is not connected. Ignoring setTrackSelectionParameters().");
    }
    impl.setTrackSelectionParameters(parameters);
  }

  @Override
  public Looper getApplicationLooper() {
    // Don't verify application thread. We allow calls to this method from any thread.
    return applicationHandler.getLooper();
  }

  /**
   * Gets the optional time diff (in milliseconds) used for calculating the current position, or
   * {@link C#TIME_UNSET} if no diff should be applied.
   */
  /* package */ long getTimeDiffMs() {
    return timeDiffMs;
  }

  /**
   * Sets the time diff (in milliseconds) used when calculating the current position.
   *
   * @param timeDiffMs {@link C#TIME_UNSET} for reset.
   */
  @VisibleForTesting(otherwise = NONE)
  /* package */ void setTimeDiffMs(long timeDiffMs) {
    verifyApplicationThread();
    this.timeDiffMs = timeDiffMs;
  }

  @Override
  public void addListener(Player.Listener listener) {
    // Don't verify application thread. We allow calls to this method from any thread.
    checkNotNull(listener, "listener must not be null");
    impl.addListener(listener);
  }

  @Override
  public void removeListener(Player.Listener listener) {
    verifyApplicationThread();
    checkNotNull(listener, "listener must not be null");
    impl.removeListener(listener);
  }

  @Override
  public boolean isCommandAvailable(@Command int command) {
    return getAvailableCommands().contains(command);
  }

  @Override
  public Commands getAvailableCommands() {
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
  public boolean isSessionCommandAvailable(@SessionCommand.CommandCode int sessionCommandCode) {
    return getAvailableSessionCommands().contains(sessionCommandCode);
  }

  /** Returns whether the {@link SessionCommand} is available. */
  public boolean isSessionCommandAvailable(SessionCommand sessionCommand) {
    return getAvailableSessionCommands().contains(sessionCommand);
  }

  /**
   * Returns the current available session commands from {@link
   * Listener#onAvailableSessionCommandsChanged(MediaController, SessionCommands)}, or {@link
   * SessionCommands#EMPTY} if it is not connected.
   *
   * @return The available session commands.
   */
  public SessionCommands getAvailableSessionCommands() {
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

  /* package */ void runOnApplicationLooper(Runnable runnable) {
    postOrRun(applicationHandler, runnable);
  }

  /* package */ void notifyControllerListener(Consumer<Listener> listenerConsumer) {
    checkState(Looper.myLooper() == getApplicationLooper());
    listenerConsumer.accept(listener);
  }

  /* package */ void notifyAccepted() {
    checkState(Looper.myLooper() == getApplicationLooper());
    checkState(!connectionNotified);
    connectionNotified = true;
    connectionCallback.onAccepted();
  }

  private void verifyApplicationThread() {
    checkState(Looper.myLooper() == getApplicationLooper(), WRONG_THREAD_ERROR_MESSAGE);
  }

  interface MediaControllerImpl {

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

    void increaseDeviceVolume();

    void decreaseDeviceVolume();

    void setDeviceMuted(boolean muted);

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
  }
}
