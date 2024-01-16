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

import static android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_NEXT;
import static android.view.KeyEvent.KEYCODE_MEDIA_PAUSE;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY;
import static android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
import static android.view.KeyEvent.KEYCODE_MEDIA_PREVIOUS;
import static android.view.KeyEvent.KEYCODE_MEDIA_REWIND;
import static android.view.KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD;
import static android.view.KeyEvent.KEYCODE_MEDIA_STOP;
import static androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.MediaSessionStub.UNKNOWN_SEQUENCE_NUMBER;
import static androidx.media3.session.SessionResult.RESULT_ERROR_SESSION_DISCONNECTED;
import static androidx.media3.session.SessionResult.RESULT_ERROR_UNKNOWN;
import static androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import androidx.annotation.CheckResult;
import androidx.annotation.DoNotInline;
import androidx.annotation.FloatRange;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.media.MediaBrowserServiceCompat;
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
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.BitmapLoader;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerCb;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition;
import androidx.media3.session.SequencedFutureManager.SequencedFuture;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.checkerframework.checker.initialization.qual.Initialized;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/* package */ class MediaSessionImpl {

  private static final String ANDROID_AUTOMOTIVE_LAUNCHER_PACKAGE_NAME =
      "com.android.car.carlauncher";
  private static final String ANDROID_AUTOMOTIVE_MEDIA_PACKAGE_NAME = "com.android.car.media";
  private static final String ANDROID_AUTO_PACKAGE_NAME = "com.google.android.projection.gearhead";
  private static final String SYSTEM_UI_PACKAGE_NAME = "com.android.systemui";
  private static final String WRONG_THREAD_ERROR_MESSAGE =
      "Player callback method is called from a wrong thread. "
          + "See javadoc of MediaSession for details.";

  private static final long DEFAULT_SESSION_POSITION_UPDATE_DELAY_MS = 3_000;

  public static final String TAG = "MSImplBase";

  private static final SessionResult RESULT_WHEN_CLOSED = new SessionResult(RESULT_INFO_SKIPPED);

  private final Object lock = new Object();

  private final Uri sessionUri;
  private final PlayerInfoChangedHandler onPlayerInfoChangedHandler;
  private final MediaPlayPauseKeyHandler mediaPlayPauseKeyHandler;
  private final MediaSession.Callback callback;
  private final Context context;
  private final MediaSessionStub sessionStub;
  private final MediaSessionLegacyStub sessionLegacyStub;
  private final String sessionId;
  private final SessionToken sessionToken;
  private final MediaSession instance;
  private final Handler applicationHandler;
  private final BitmapLoader bitmapLoader;
  private final Runnable periodicSessionPositionInfoUpdateRunnable;
  private final Handler mainHandler;
  private final boolean playIfSuppressed;
  private final boolean isPeriodicPositionUpdateEnabled;

  private PlayerInfo playerInfo;
  private PlayerWrapper playerWrapper;
  private @MonotonicNonNull PendingIntent sessionActivity;
  @Nullable private PlayerListener playerListener;
  @Nullable private MediaSession.Listener mediaSessionListener;
  @Nullable private ControllerInfo controllerForCurrentRequest;

  @GuardedBy("lock")
  @Nullable
  private MediaSessionServiceLegacyStub browserServiceLegacyStub;

  @GuardedBy("lock")
  private boolean closed;

  // Should be only accessed on the application looper
  private long sessionPositionUpdateDelayMs;
  private boolean isMediaNotificationControllerConnected;
  private ImmutableList<CommandButton> customLayout;
  private Bundle sessionExtras;

  public MediaSessionImpl(
      MediaSession instance,
      Context context,
      String id,
      Player player,
      @Nullable PendingIntent sessionActivity,
      ImmutableList<CommandButton> customLayout,
      MediaSession.Callback callback,
      Bundle tokenExtras,
      Bundle sessionExtras,
      BitmapLoader bitmapLoader,
      boolean playIfSuppressed,
      boolean isPeriodicPositionUpdateEnabled) {
    this.instance = instance;
    this.context = context;
    sessionId = id;
    this.sessionActivity = sessionActivity;
    this.customLayout = customLayout;
    this.callback = callback;
    this.sessionExtras = sessionExtras;
    this.bitmapLoader = bitmapLoader;
    this.playIfSuppressed = playIfSuppressed;
    this.isPeriodicPositionUpdateEnabled = isPeriodicPositionUpdateEnabled;

    @SuppressWarnings("nullness:assignment")
    @Initialized
    MediaSessionImpl thisRef = this;

    sessionStub = new MediaSessionStub(thisRef);

    mainHandler = new Handler(Looper.getMainLooper());
    Looper applicationLooper = player.getApplicationLooper();
    applicationHandler = new Handler(applicationLooper);

    playerInfo = PlayerInfo.DEFAULT;
    onPlayerInfoChangedHandler = new PlayerInfoChangedHandler(applicationLooper);
    mediaPlayPauseKeyHandler = new MediaPlayPauseKeyHandler(applicationLooper);

    // Build Uri that differentiate sessions across the creation/destruction in PendingIntent.
    // Here's the reason why Session ID / SessionToken aren't suitable here.
    //   - Session ID
    //     PendingIntent from the previously closed session with the same ID can be sent to the
    //     newly created session.
    //   - SessionToken
    //     SessionToken is a Parcelable so we can only put it into the intent extra.
    //     However, creating two different PendingIntent that only differs extras isn't allowed.
    //     See {@link PendingIntent} and {@link Intent#filterEquals} for details.
    sessionUri =
        new Uri.Builder()
            .scheme(MediaSessionImpl.class.getName())
            .appendPath(id)
            .appendPath(String.valueOf(SystemClock.elapsedRealtime()))
            .build();
    sessionToken =
        new SessionToken(
            Process.myUid(),
            SessionToken.TYPE_SESSION,
            MediaLibraryInfo.VERSION_INT,
            MediaSessionStub.VERSION_INT,
            context.getPackageName(),
            sessionStub,
            tokenExtras);

    sessionLegacyStub =
        new MediaSessionLegacyStub(/* session= */ thisRef, sessionUri, applicationHandler);
    // For PlayerWrapper, use the same default commands as the proxy controller gets when the app
    // doesn't overrides the default commands in `onConnect`. When the default is overridden by the
    // app in `onConnect`, the default set here will be overridden with these values.
    MediaSession.ConnectionResult connectionResult =
        new MediaSession.ConnectionResult.AcceptedResultBuilder(instance).build();
    PlayerWrapper playerWrapper =
        new PlayerWrapper(
            player,
            playIfSuppressed,
            customLayout,
            connectionResult.availableSessionCommands,
            connectionResult.availablePlayerCommands);
    this.playerWrapper = playerWrapper;
    postOrRun(
        applicationHandler,
        () ->
            thisRef.setPlayerInternal(
                /* oldPlayerWrapper= */ null, /* newPlayerWrapper= */ playerWrapper));

    sessionPositionUpdateDelayMs = DEFAULT_SESSION_POSITION_UPDATE_DELAY_MS;
    periodicSessionPositionInfoUpdateRunnable =
        thisRef::notifyPeriodicSessionPositionInfoChangesOnHandler;
    postOrRun(applicationHandler, thisRef::schedulePeriodicSessionPositionInfoChanges);
  }

  public void setPlayer(Player player) {
    if (player == playerWrapper.getWrappedPlayer()) {
      return;
    }
    setPlayerInternal(
        /* oldPlayerWrapper= */ playerWrapper,
        new PlayerWrapper(
            player,
            playIfSuppressed,
            playerWrapper.getCustomLayout(),
            playerWrapper.getAvailableSessionCommands(),
            playerWrapper.getAvailablePlayerCommands()));
  }

  private void setPlayerInternal(
      @Nullable PlayerWrapper oldPlayerWrapper, PlayerWrapper newPlayerWrapper) {
    playerWrapper = newPlayerWrapper;
    if (oldPlayerWrapper != null) {
      oldPlayerWrapper.removeListener(checkStateNotNull(this.playerListener));
    }
    PlayerListener playerListener = new PlayerListener(this, newPlayerWrapper);
    newPlayerWrapper.addListener(playerListener);
    this.playerListener = playerListener;

    dispatchRemoteControllerTaskToLegacyStub(
        (callback, seq) -> callback.onPlayerChanged(seq, oldPlayerWrapper, newPlayerWrapper));

    // Check whether it's called in constructor where previous player can be null.
    if (oldPlayerWrapper == null) {
      // Do followings at the last moment. Otherwise commands through framework would be sent to
      // this session while initializing, and end up with unexpected situation.
      sessionLegacyStub.start();
    }

    playerInfo = newPlayerWrapper.createPlayerInfoForBundling();
    handleAvailablePlayerCommandsChanged(newPlayerWrapper.getAvailableCommands());
  }

  public void release() {
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
    }
    mediaPlayPauseKeyHandler.clearPendingPlayPauseTask();
    applicationHandler.removeCallbacksAndMessages(null);
    try {
      postOrRun(
          applicationHandler,
          () -> {
            if (playerListener != null) {
              playerWrapper.removeListener(playerListener);
            }
          });
    } catch (Exception e) {
      // Catch all exceptions to ensure the rest of this method to be executed as exceptions may be
      // thrown by user if, for example, the application thread is dead or removeListener throws an
      // exception.
      Log.w(TAG, "Exception thrown while closing", e);
    }
    sessionLegacyStub.release();
    sessionStub.release();
  }

  public PlayerWrapper getPlayerWrapper() {
    return playerWrapper;
  }

  @CheckResult
  public Runnable callWithControllerForCurrentRequestSet(
      @Nullable ControllerInfo controllerForCurrentRequest, Runnable runnable) {
    return () -> {
      this.controllerForCurrentRequest = controllerForCurrentRequest;
      runnable.run();
      this.controllerForCurrentRequest = null;
    };
  }

  public String getId() {
    return sessionId;
  }

  public Uri getUri() {
    return sessionUri;
  }

  public SessionToken getToken() {
    return sessionToken;
  }

  public List<ControllerInfo> getConnectedControllers() {
    List<ControllerInfo> controllers = new ArrayList<>();
    controllers.addAll(sessionStub.getConnectedControllersManager().getConnectedControllers());
    if (isMediaNotificationControllerConnected) {
      ImmutableList<ControllerInfo> legacyControllers =
          sessionLegacyStub.getConnectedControllersManager().getConnectedControllers();
      for (int i = 0; i < legacyControllers.size(); i++) {
        ControllerInfo legacyController = legacyControllers.get(i);
        if (!isSystemUiController(legacyController)) {
          controllers.add(legacyController);
        }
      }
    } else {
      controllers.addAll(
          sessionLegacyStub.getConnectedControllersManager().getConnectedControllers());
    }
    return controllers;
  }

  @Nullable
  public ControllerInfo getControllerForCurrentRequest() {
    return controllerForCurrentRequest != null
        ? resolveControllerInfoForCallback(controllerForCurrentRequest)
        : null;
  }

  public boolean isConnected(ControllerInfo controller) {
    return sessionStub.getConnectedControllersManager().isConnected(controller)
        || sessionLegacyStub.getConnectedControllersManager().isConnected(controller);
  }

  /**
   * Returns whether the given {@link ControllerInfo} belongs to the the System UI controller.
   *
   * @param controllerInfo The controller info.
   * @return Whether the controller info belongs to the System UI controller.
   */
  protected boolean isSystemUiController(@Nullable MediaSession.ControllerInfo controllerInfo) {
    return controllerInfo != null
        && controllerInfo.getControllerVersion() == ControllerInfo.LEGACY_CONTROLLER_VERSION
        && Objects.equals(controllerInfo.getPackageName(), SYSTEM_UI_PACKAGE_NAME);
  }

  /**
   * Returns whether the given {@link ControllerInfo} belongs to the media notification controller.
   *
   * @param controllerInfo The controller info.
   * @return Whether the given controller info belongs to the media notification controller.
   */
  public boolean isMediaNotificationController(MediaSession.ControllerInfo controllerInfo) {
    return Objects.equals(controllerInfo.getPackageName(), context.getPackageName())
        && controllerInfo.getControllerVersion() != ControllerInfo.LEGACY_CONTROLLER_VERSION
        && controllerInfo
            .getConnectionHints()
            .getBoolean(
                MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, /* defaultValue= */ false);
  }

  /**
   * Returns whether the given {@link ControllerInfo} belongs to an Automotive OS controller.
   *
   * @param controllerInfo The controller info.
   * @return Whether the given controller info belongs to an Automotive OS controller.
   */
  public boolean isAutomotiveController(ControllerInfo controllerInfo) {
    return controllerInfo.getControllerVersion() == ControllerInfo.LEGACY_CONTROLLER_VERSION
        && (controllerInfo.getPackageName().equals(ANDROID_AUTOMOTIVE_MEDIA_PACKAGE_NAME)
            || controllerInfo.getPackageName().equals(ANDROID_AUTOMOTIVE_LAUNCHER_PACKAGE_NAME));
  }

  /**
   * Returns whether the given {@link ControllerInfo} belongs to an Android Auto companion app
   * controller.
   *
   * @param controllerInfo The controller info.
   * @return Whether the given controller info belongs to an Android Auto companion app controller.
   */
  public boolean isAutoCompanionController(ControllerInfo controllerInfo) {
    return controllerInfo.getControllerVersion() == ControllerInfo.LEGACY_CONTROLLER_VERSION
        && controllerInfo.getPackageName().equals(ANDROID_AUTO_PACKAGE_NAME);
  }

  /**
   * Returns the {@link ControllerInfo} of the system UI notification controller, or {@code null} if
   * the System UI controller is not connected.
   */
  @Nullable
  protected ControllerInfo getSystemUiControllerInfo() {
    ImmutableList<ControllerInfo> connectedControllers =
        sessionLegacyStub.getConnectedControllersManager().getConnectedControllers();
    for (int i = 0; i < connectedControllers.size(); i++) {
      ControllerInfo controllerInfo = connectedControllers.get(i);
      if (isSystemUiController(controllerInfo)) {
        return controllerInfo;
      }
    }
    return null;
  }

  /**
   * Returns the {@link ControllerInfo} of the media notification controller, or {@code null} if the
   * media notification controller is not connected.
   */
  @Nullable
  public ControllerInfo getMediaNotificationControllerInfo() {
    ImmutableList<ControllerInfo> connectedControllers =
        sessionStub.getConnectedControllersManager().getConnectedControllers();
    for (int i = 0; i < connectedControllers.size(); i++) {
      ControllerInfo controllerInfo = connectedControllers.get(i);
      if (isMediaNotificationController(controllerInfo)) {
        return controllerInfo;
      }
    }
    return null;
  }

  /** Returns whether the media notification controller is connected. */
  protected boolean isMediaNotificationControllerConnected() {
    return isMediaNotificationControllerConnected;
  }

  /**
   * Sets the custom layout for the given {@link MediaController}.
   *
   * @param controller The controller.
   * @param customLayout The custom layout.
   * @return The session result from the controller.
   */
  public ListenableFuture<SessionResult> setCustomLayout(
      ControllerInfo controller, ImmutableList<CommandButton> customLayout) {
    if (isMediaNotificationController(controller)) {
      playerWrapper.setCustomLayout(customLayout);
      sessionLegacyStub.updateLegacySessionPlaybackState(playerWrapper);
    }
    return dispatchRemoteControllerTask(
        controller, (controller1, seq) -> controller1.setCustomLayout(seq, customLayout));
  }

  /** Sets the custom layout of the session and sends the custom layout to all controllers. */
  public void setCustomLayout(ImmutableList<CommandButton> customLayout) {
    this.customLayout = customLayout;
    playerWrapper.setCustomLayout(customLayout);
    dispatchRemoteControllerTaskWithoutReturn(
        (controller, seq) -> controller.setCustomLayout(seq, customLayout));
  }

  /** Returns the custom layout. */
  public ImmutableList<CommandButton> getCustomLayout() {
    return customLayout;
  }

  public void setSessionExtras(Bundle sessionExtras) {
    this.sessionExtras = sessionExtras;
    dispatchRemoteControllerTaskWithoutReturn(
        (controller, seq) -> controller.onSessionExtrasChanged(seq, sessionExtras));
  }

  public void setSessionExtras(ControllerInfo controller, Bundle sessionExtras) {
    if (sessionStub.getConnectedControllersManager().isConnected(controller)) {
      dispatchRemoteControllerTaskWithoutReturn(
          controller, (callback, seq) -> callback.onSessionExtrasChanged(seq, sessionExtras));
      if (isMediaNotificationController(controller)) {
        dispatchRemoteControllerTaskToLegacyStub(
            (callback, seq) -> callback.onSessionExtrasChanged(seq, sessionExtras));
      }
    }
  }

  public Bundle getSessionExtras() {
    return sessionExtras;
  }

  public BitmapLoader getBitmapLoader() {
    return bitmapLoader;
  }

  public boolean shouldPlayIfSuppressed() {
    return playIfSuppressed;
  }

  public void setAvailableCommands(
      ControllerInfo controller, SessionCommands sessionCommands, Player.Commands playerCommands) {
    if (sessionStub.getConnectedControllersManager().isConnected(controller)) {
      if (isMediaNotificationController(controller)) {
        setAvailableFrameworkControllerCommands(sessionCommands, playerCommands);
        ControllerInfo systemUiControllerInfo = getSystemUiControllerInfo();
        if (systemUiControllerInfo != null) {
          // Set the available commands of the proxy controller to the ConnectedControllerRecord of
          // the hidden System UI controller.
          sessionLegacyStub
              .getConnectedControllersManager()
              .updateCommandsFromSession(systemUiControllerInfo, sessionCommands, playerCommands);
        }
      }
      sessionStub
          .getConnectedControllersManager()
          .updateCommandsFromSession(controller, sessionCommands, playerCommands);
      dispatchRemoteControllerTaskWithoutReturn(
          controller,
          (callback, seq) ->
              callback.onAvailableCommandsChangedFromSession(seq, sessionCommands, playerCommands));
      onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ false, /* excludeTracks= */ false);
    } else {
      sessionLegacyStub
          .getConnectedControllersManager()
          .updateCommandsFromSession(controller, sessionCommands, playerCommands);
    }
  }

  public void broadcastCustomCommand(SessionCommand command, Bundle args) {
    dispatchRemoteControllerTaskWithoutReturn(
        (controller, seq) -> controller.sendCustomCommand(seq, command, args));
  }

  private void dispatchOnPlayerInfoChanged(
      PlayerInfo playerInfo, boolean excludeTimeline, boolean excludeTracks) {
    playerInfo = sessionStub.generateAndCacheUniqueTrackGroupIds(playerInfo);
    List<ControllerInfo> controllers =
        sessionStub.getConnectedControllersManager().getConnectedControllers();
    for (int i = 0; i < controllers.size(); i++) {
      ControllerInfo controller = controllers.get(i);
      try {
        int seq;
        ConnectedControllersManager<IBinder> controllersManager =
            sessionStub.getConnectedControllersManager();
        SequencedFutureManager manager = controllersManager.getSequencedFutureManager(controller);
        if (manager != null) {
          seq = manager.obtainNextSequenceNumber();
        } else {
          if (!isConnected(controller)) {
            return;
          }
          // 0 is OK for legacy controllers, because they didn't have sequence numbers.
          seq = 0;
        }
        Player.Commands intersectedCommands =
            MediaUtils.intersect(
                controllersManager.getAvailablePlayerCommands(controller),
                getPlayerWrapper().getAvailableCommands());
        checkStateNotNull(controller.getControllerCb())
            .onPlayerInfoChanged(
                seq,
                playerInfo,
                intersectedCommands,
                excludeTimeline,
                excludeTracks,
                controller.getInterfaceVersion());
      } catch (DeadObjectException e) {
        onDeadObjectException(controller);
      } catch (RemoteException e) {
        // Currently it's TransactionTooLargeException or DeadSystemException.
        // We'd better to leave log for those cases because
        //   - TransactionTooLargeException means that we may need to fix our code.
        //     (e.g. add pagination or special way to deliver Bitmap)
        //   - DeadSystemException means that errors around it can be ignored.
        Log.w(TAG, "Exception in " + controller.toString(), e);
      }
    }
  }

  public ListenableFuture<SessionResult> sendCustomCommand(
      ControllerInfo controller, SessionCommand command, Bundle args) {
    return dispatchRemoteControllerTask(
        controller, (cb, seq) -> cb.sendCustomCommand(seq, command, args));
  }

  public MediaSession.ConnectionResult onConnectOnHandler(ControllerInfo controller) {
    if (isMediaNotificationControllerConnected && isSystemUiController(controller)) {
      // Hide System UI and provide the connection result from the `PlayerWrapper` state.
      return new MediaSession.ConnectionResult.AcceptedResultBuilder(instance)
          .setAvailableSessionCommands(playerWrapper.getAvailableSessionCommands())
          .setAvailablePlayerCommands(playerWrapper.getAvailablePlayerCommands())
          .setCustomLayout(playerWrapper.getCustomLayout())
          .build();
    }
    MediaSession.ConnectionResult connectionResult =
        checkNotNull(
            callback.onConnect(instance, controller),
            "Callback.onConnect must return non-null future");
    if (isMediaNotificationController(controller) && connectionResult.isAccepted) {
      isMediaNotificationControllerConnected = true;
      playerWrapper.setCustomLayout(
          connectionResult.customLayout != null
              ? connectionResult.customLayout
              : instance.getCustomLayout());
      setAvailableFrameworkControllerCommands(
          connectionResult.availableSessionCommands, connectionResult.availablePlayerCommands);
    }
    return connectionResult;
  }

  public void onPostConnectOnHandler(ControllerInfo controller) {
    if (isMediaNotificationControllerConnected && isSystemUiController(controller)) {
      // Hide System UI. Apps can use the media notification controller to maintain the platform
      // session
      return;
    }
    callback.onPostConnect(instance, controller);
  }

  public void onDisconnectedOnHandler(ControllerInfo controller) {
    if (isMediaNotificationControllerConnected) {
      if (isSystemUiController(controller)) {
        // Hide System UI controller. Apps can use the media notification controller to maintain the
        // platform session.
        return;
      } else if (isMediaNotificationController(controller)) {
        isMediaNotificationControllerConnected = false;
      }
    }
    callback.onDisconnected(instance, controller);
  }

  @SuppressWarnings("deprecation") // Calling deprecated callback method.
  public @SessionResult.Code int onPlayerCommandRequestOnHandler(
      ControllerInfo controller, @Player.Command int playerCommand) {
    return callback.onPlayerCommandRequest(
        instance, resolveControllerInfoForCallback(controller), playerCommand);
  }

  public ListenableFuture<SessionResult> onSetRatingOnHandler(
      ControllerInfo controller, String mediaId, Rating rating) {
    return checkNotNull(
        callback.onSetRating(
            instance, resolveControllerInfoForCallback(controller), mediaId, rating),
        "Callback.onSetRating must return non-null future");
  }

  public ListenableFuture<SessionResult> onSetRatingOnHandler(
      ControllerInfo controller, Rating rating) {
    return checkNotNull(
        callback.onSetRating(instance, resolveControllerInfoForCallback(controller), rating),
        "Callback.onSetRating must return non-null future");
  }

  public ListenableFuture<SessionResult> onCustomCommandOnHandler(
      ControllerInfo controller, SessionCommand command, Bundle extras) {
    return checkNotNull(
        callback.onCustomCommand(
            instance, resolveControllerInfoForCallback(controller), command, extras),
        "Callback.onCustomCommandOnHandler must return non-null future");
  }

  protected ListenableFuture<List<MediaItem>> onAddMediaItemsOnHandler(
      ControllerInfo controller, List<MediaItem> mediaItems) {
    return checkNotNull(
        callback.onAddMediaItems(
            instance, resolveControllerInfoForCallback(controller), mediaItems),
        "Callback.onAddMediaItems must return a non-null future");
  }

  protected ListenableFuture<MediaItemsWithStartPosition> onSetMediaItemsOnHandler(
      ControllerInfo controller, List<MediaItem> mediaItems, int startIndex, long startPositionMs) {
    return checkNotNull(
        callback.onSetMediaItems(
            instance,
            resolveControllerInfoForCallback(controller),
            mediaItems,
            startIndex,
            startPositionMs),
        "Callback.onSetMediaItems must return a non-null future");
  }

  public void connectFromService(IMediaController caller, ControllerInfo controllerInfo) {
    sessionStub.connect(caller, controllerInfo);
  }

  public MediaSessionCompat getSessionCompat() {
    return sessionLegacyStub.getSessionCompat();
  }

  public void setLegacyControllerConnectionTimeoutMs(long timeoutMs) {
    sessionLegacyStub.setLegacyControllerDisconnectTimeoutMs(timeoutMs);
  }

  protected Context getContext() {
    return context;
  }

  protected Handler getApplicationHandler() {
    return applicationHandler;
  }

  protected boolean isReleased() {
    synchronized (lock) {
      return closed;
    }
  }

  @Nullable
  protected PendingIntent getSessionActivity() {
    return sessionActivity;
  }

  @UnstableApi
  protected void setSessionActivity(PendingIntent sessionActivity) {
    if (Objects.equals(this.sessionActivity, sessionActivity)) {
      return;
    }
    this.sessionActivity = sessionActivity;
    sessionLegacyStub.getSessionCompat().setSessionActivity(sessionActivity);
    ImmutableList<ControllerInfo> connectedControllers =
        sessionStub.getConnectedControllersManager().getConnectedControllers();
    for (int i = 0; i < connectedControllers.size(); i++) {
      ControllerInfo controllerInfo = connectedControllers.get(i);
      if (controllerInfo.getControllerVersion() >= 3) {
        dispatchRemoteControllerTaskWithoutReturn(
            controllerInfo,
            (controller, seq) -> controller.onSessionActivityChanged(seq, sessionActivity));
      }
    }
  }

  protected ControllerInfo resolveControllerInfoForCallback(ControllerInfo controller) {
    return isMediaNotificationControllerConnected && isSystemUiController(controller)
        ? checkNotNull(getMediaNotificationControllerInfo())
        : controller;
  }

  /**
   * Gets the service binder from the MediaBrowserServiceCompat. Should be only called by the thread
   * with a Looper.
   */
  protected IBinder getLegacyBrowserServiceBinder() {
    MediaSessionServiceLegacyStub legacyStub;
    synchronized (lock) {
      if (browserServiceLegacyStub == null) {
        browserServiceLegacyStub =
            createLegacyBrowserService(instance.getSessionCompat().getSessionToken());
      }
      legacyStub = browserServiceLegacyStub;
    }
    Intent intent = new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
    return legacyStub.onBind(intent);
  }

  protected MediaSessionServiceLegacyStub createLegacyBrowserService(
      MediaSessionCompat.Token compatToken) {
    MediaSessionServiceLegacyStub stub = new MediaSessionServiceLegacyStub(this);
    stub.initialize(compatToken);
    return stub;
  }

  protected void setSessionPositionUpdateDelayMsOnHandler(long updateDelayMs) {
    verifyApplicationThread();
    sessionPositionUpdateDelayMs = updateDelayMs;
    schedulePeriodicSessionPositionInfoChanges();
  }

  @Nullable
  protected MediaSessionServiceLegacyStub getLegacyBrowserService() {
    synchronized (lock) {
      return browserServiceLegacyStub;
    }
  }

  /* package */ boolean canResumePlaybackOnStart() {
    return sessionLegacyStub.canResumePlaybackOnStart();
  }

  /* package */ void setMediaSessionListener(MediaSession.Listener listener) {
    this.mediaSessionListener = listener;
  }

  /* package */ void clearMediaSessionListener() {
    this.mediaSessionListener = null;
  }

  /* package */ void onNotificationRefreshRequired() {
    postOrRun(
        mainHandler,
        () -> {
          if (this.mediaSessionListener != null) {
            this.mediaSessionListener.onNotificationRefreshRequired(instance);
          }
        });
  }

  /* package */ boolean onPlayRequested() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      SettableFuture<Boolean> playRequested = SettableFuture.create();
      mainHandler.post(() -> playRequested.set(onPlayRequested()));
      try {
        return playRequested.get();
      } catch (InterruptedException | ExecutionException e) {
        throw new IllegalStateException(e);
      }
    }
    if (this.mediaSessionListener != null) {
      return this.mediaSessionListener.onPlayRequested(instance);
    }
    return true;
  }

  /**
   * Handles a play request from a media controller.
   *
   * <p>Attempts to prepare and play for playback resumption if the playlist is empty. {@link
   * Player#play()} is called regardless of success or failure of playback resumption.
   *
   * @param controller The controller requesting to play.
   */
  /* package */ void handleMediaControllerPlayRequest(ControllerInfo controller) {
    if (!onPlayRequested()) {
      // Request denied, e.g. due to missing foreground service abilities.
      return;
    }
    boolean hasCurrentMediaItem =
        playerWrapper.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
            && playerWrapper.getCurrentMediaItem() != null;
    boolean canAddMediaItems =
        playerWrapper.isCommandAvailable(COMMAND_SET_MEDIA_ITEM)
            || playerWrapper.isCommandAvailable(COMMAND_CHANGE_MEDIA_ITEMS);
    if (hasCurrentMediaItem || !canAddMediaItems) {
      // No playback resumption needed or possible.
      if (!hasCurrentMediaItem) {
        Log.w(
            TAG,
            "Play requested without current MediaItem, but playback resumption prevented by"
                + " missing available commands");
      }
      Util.handlePlayButtonAction(playerWrapper);
    } else {
      @Nullable
      ListenableFuture<MediaItemsWithStartPosition> future =
          checkNotNull(
              callback.onPlaybackResumption(instance, resolveControllerInfoForCallback(controller)),
              "Callback.onPlaybackResumption must return a non-null future");
      Futures.addCallback(
          future,
          new FutureCallback<MediaItemsWithStartPosition>() {
            @Override
            public void onSuccess(MediaItemsWithStartPosition mediaItemsWithStartPosition) {
              MediaUtils.setMediaItemsWithStartIndexAndPosition(
                  playerWrapper, mediaItemsWithStartPosition);
              Util.handlePlayButtonAction(playerWrapper);
            }

            @Override
            public void onFailure(Throwable t) {
              if (t instanceof UnsupportedOperationException) {
                Log.w(
                    TAG,
                    "UnsupportedOperationException: Make sure to implement"
                        + " MediaSession.Callback.onPlaybackResumption() if you add a"
                        + " media button receiver to your manifest or if you implement the recent"
                        + " media item contract with your MediaLibraryService.",
                    t);
              } else {
                Log.e(
                    TAG,
                    "Failure calling MediaSession.Callback.onPlaybackResumption(): "
                        + t.getMessage(),
                    t);
              }
              // Play as requested even if playback resumption fails.
              Util.handlePlayButtonAction(playerWrapper);
            }
          },
          this::postOrRunOnApplicationHandler);
    }
  }

  private void setAvailableFrameworkControllerCommands(
      SessionCommands sessionCommands, Player.Commands playerCommands) {
    boolean commandGetTimelineChanged =
        playerWrapper.getAvailablePlayerCommands().contains(Player.COMMAND_GET_TIMELINE)
            != playerCommands.contains(Player.COMMAND_GET_TIMELINE);
    playerWrapper.setAvailableCommands(sessionCommands, playerCommands);
    if (commandGetTimelineChanged) {
      sessionLegacyStub.updateLegacySessionPlaybackStateAndQueue(playerWrapper);
    } else {
      sessionLegacyStub.updateLegacySessionPlaybackState(playerWrapper);
    }
  }

  private void dispatchRemoteControllerTaskToLegacyStub(RemoteControllerTask task) {
    try {
      task.run(sessionLegacyStub.getControllerLegacyCbForBroadcast(), /* seq= */ 0);
    } catch (RemoteException e) {
      Log.e(TAG, "Exception in using media1 API", e);
    }
  }

  private void dispatchOnPeriodicSessionPositionInfoChanged(
      SessionPositionInfo sessionPositionInfo) {
    ConnectedControllersManager<IBinder> controllersManager =
        sessionStub.getConnectedControllersManager();
    List<ControllerInfo> controllers =
        sessionStub.getConnectedControllersManager().getConnectedControllers();
    for (int i = 0; i < controllers.size(); i++) {
      ControllerInfo controller = controllers.get(i);
      boolean canAccessCurrentMediaItem =
          controllersManager.isPlayerCommandAvailable(
              controller, Player.COMMAND_GET_CURRENT_MEDIA_ITEM);
      boolean canAccessTimeline =
          controllersManager.isPlayerCommandAvailable(controller, Player.COMMAND_GET_TIMELINE);
      dispatchRemoteControllerTaskWithoutReturn(
          controller,
          (controllerCb, seq) ->
              controllerCb.onPeriodicSessionPositionInfoChanged(
                  seq,
                  sessionPositionInfo,
                  canAccessCurrentMediaItem,
                  canAccessTimeline,
                  controller.getInterfaceVersion()));
    }
    try {
      sessionLegacyStub
          .getControllerLegacyCbForBroadcast()
          .onPeriodicSessionPositionInfoChanged(
              /* seq= */ 0,
              sessionPositionInfo,
              /* canAccessCurrentMediaItem= */ true,
              /* canAccessTimeline= */ true,
              ControllerInfo.LEGACY_CONTROLLER_INTERFACE_VERSION);
    } catch (RemoteException e) {
      Log.e(TAG, "Exception in using media1 API", e);
    }
  }

  protected void dispatchRemoteControllerTaskWithoutReturn(RemoteControllerTask task) {
    List<ControllerInfo> controllers =
        sessionStub.getConnectedControllersManager().getConnectedControllers();
    for (int i = 0; i < controllers.size(); i++) {
      ControllerInfo controller = controllers.get(i);
      dispatchRemoteControllerTaskWithoutReturn(controller, task);
    }
    try {
      task.run(sessionLegacyStub.getControllerLegacyCbForBroadcast(), /* seq= */ 0);
    } catch (RemoteException e) {
      Log.e(TAG, "Exception in using media1 API", e);
    }
  }

  protected void dispatchRemoteControllerTaskWithoutReturn(
      ControllerInfo controller, RemoteControllerTask task) {
    try {
      int seq;
      @Nullable
      SequencedFutureManager manager =
          sessionStub.getConnectedControllersManager().getSequencedFutureManager(controller);
      if (manager != null) {
        seq = manager.obtainNextSequenceNumber();
      } else {
        if (!isConnected(controller)) {
          return;
        }
        // 0 is OK for legacy controllers, because they didn't have sequence numbers.
        seq = 0;
      }
      ControllerCb cb = controller.getControllerCb();
      if (cb != null) {
        task.run(cb, seq);
      }
    } catch (DeadObjectException e) {
      onDeadObjectException(controller);
    } catch (RemoteException e) {
      // Currently it's TransactionTooLargeException or DeadSystemException.
      // We'd better to leave log for those cases because
      //   - TransactionTooLargeException means that we may need to fix our code.
      //     (e.g. add pagination or special way to deliver Bitmap)
      //   - DeadSystemException means that errors around it can be ignored.
      Log.w(TAG, "Exception in " + controller.toString(), e);
    }
  }

  private ListenableFuture<SessionResult> dispatchRemoteControllerTask(
      ControllerInfo controller, RemoteControllerTask task) {
    try {
      ListenableFuture<SessionResult> future;
      int seq;
      @Nullable
      SequencedFutureManager manager =
          sessionStub.getConnectedControllersManager().getSequencedFutureManager(controller);
      if (manager != null) {
        future = manager.createSequencedFuture(RESULT_WHEN_CLOSED);
        seq = ((SequencedFuture<SessionResult>) future).getSequenceNumber();
      } else {
        if (!isConnected(controller)) {
          return Futures.immediateFuture(new SessionResult(RESULT_ERROR_SESSION_DISCONNECTED));
        }
        // 0 is OK for legacy controllers, because they didn't have sequence numbers.
        seq = 0;
        // Tell that operation is successful, although we don't know the actual result.
        future = Futures.immediateFuture(new SessionResult(SessionResult.RESULT_SUCCESS));
      }
      ControllerCb cb = controller.getControllerCb();
      if (cb != null) {
        task.run(cb, seq);
      }
      return future;
    } catch (DeadObjectException e) {
      onDeadObjectException(controller);
      return Futures.immediateFuture(new SessionResult(RESULT_ERROR_SESSION_DISCONNECTED));
    } catch (RemoteException e) {
      // Currently it's TransactionTooLargeException or DeadSystemException.
      // We'd better to leave log for those cases because
      //   - TransactionTooLargeException means that we may need to fix our code.
      //     (e.g. add pagination or special way to deliver Bitmap)
      //   - DeadSystemException means that errors around it can be ignored.
      Log.w(TAG, "Exception in " + controller.toString(), e);
    }
    return Futures.immediateFuture(new SessionResult(RESULT_ERROR_UNKNOWN));
  }

  /** Removes controller. Call this when DeadObjectException is happened with binder call. */
  private void onDeadObjectException(ControllerInfo controller) {
    // Note: Only removing from MediaSessionStub and ignoring (legacy) stubs would be fine for
    //       now. Because calls to the legacy stubs doesn't throw DeadObjectException.
    sessionStub.getConnectedControllersManager().removeController(controller);
  }

  private void verifyApplicationThread() {
    if (Looper.myLooper() != applicationHandler.getLooper()) {
      throw new IllegalStateException(WRONG_THREAD_ERROR_MESSAGE);
    }
  }

  private void notifyPeriodicSessionPositionInfoChangesOnHandler() {
    synchronized (lock) {
      if (closed) {
        return;
      }
    }
    SessionPositionInfo sessionPositionInfo = playerWrapper.createSessionPositionInfoForBundling();
    if (!onPlayerInfoChangedHandler.hasPendingPlayerInfoChangedUpdate()
        && MediaUtils.areSessionPositionInfosInSamePeriodOrAd(
            sessionPositionInfo, playerInfo.sessionPositionInfo)) {
      // Send a periodic position info only if a PlayerInfo update is not already already pending
      // and the player state is still corresponding to the currently known PlayerInfo. Both
      // conditions will soon trigger a new PlayerInfo update with the latest position info anyway
      // and we also don't want to send a new position info early if the corresponding Timeline
      // update hasn't been sent yet (see [internal b/277301159]).
      dispatchOnPeriodicSessionPositionInfoChanged(sessionPositionInfo);
    }
    schedulePeriodicSessionPositionInfoChanges();
  }

  private void schedulePeriodicSessionPositionInfoChanges() {
    applicationHandler.removeCallbacks(periodicSessionPositionInfoUpdateRunnable);
    if (isPeriodicPositionUpdateEnabled
        && sessionPositionUpdateDelayMs > 0
        && (playerWrapper.isPlaying() || playerWrapper.isLoading())) {
      applicationHandler.postDelayed(
          periodicSessionPositionInfoUpdateRunnable, sessionPositionUpdateDelayMs);
    }
  }

  private void handleAvailablePlayerCommandsChanged(Player.Commands availableCommands) {
    // Update PlayerInfo and do not force exclude elements in case they need to be updated because
    // an available command has been removed.
    onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
        /* excludeTimeline= */ false, /* excludeTracks= */ false);
    dispatchRemoteControllerTaskWithoutReturn(
        (callback, seq) -> callback.onAvailableCommandsChangedFromPlayer(seq, availableCommands));

    // Forcefully update playback info to update VolumeProviderCompat in case
    // COMMAND_ADJUST_DEVICE_VOLUME or COMMAND_SET_DEVICE_VOLUME value has changed.
    dispatchRemoteControllerTaskToLegacyStub(
        (callback, seq) -> callback.onDeviceInfoChanged(seq, playerInfo.deviceInfo));
  }

  /**
   * Returns true if the media button event was handled, false otherwise.
   *
   * <p>Must be called on the application thread of the session.
   *
   * @param callerInfo The calling {@link ControllerInfo}.
   * @param intent The media button intent.
   * @return True if the event was handled, false otherwise.
   */
  /* package */ boolean onMediaButtonEvent(ControllerInfo callerInfo, Intent intent) {
    KeyEvent keyEvent = DefaultActionFactory.getKeyEvent(intent);
    ComponentName intentComponent = intent.getComponent();
    if (!Objects.equals(intent.getAction(), Intent.ACTION_MEDIA_BUTTON)
        || (intentComponent != null
            && !Objects.equals(intentComponent.getPackageName(), context.getPackageName()))
        || keyEvent == null
        || keyEvent.getAction() != KeyEvent.ACTION_DOWN) {
      return false;
    }

    verifyApplicationThread();
    if (callback.onMediaButtonEvent(instance, callerInfo, intent)) {
      // Event handled by app callback.
      return true;
    }
    // Double tap detection.
    int keyCode = keyEvent.getKeyCode();
    boolean isTvApp = Util.SDK_INT >= 21 && Api21.isTvApp(context);
    boolean doubleTapCompleted = false;
    switch (keyCode) {
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
      case KeyEvent.KEYCODE_HEADSETHOOK:
        if (isTvApp
            || callerInfo.getControllerVersion() != ControllerInfo.LEGACY_CONTROLLER_VERSION
            || keyEvent.getRepeatCount() != 0) {
          // Double tap detection is only for mobile apps that receive a media button event from
          // external sources (for instance Bluetooth) and excluding long press (repeatCount > 0).
          mediaPlayPauseKeyHandler.flush();
        } else if (mediaPlayPauseKeyHandler.hasPendingPlayPauseTask()) {
          // A double tap arrived. Clear the pending playPause task.
          mediaPlayPauseKeyHandler.clearPendingPlayPauseTask();
          doubleTapCompleted = true;
        } else {
          // Handle event with a delayed callback that's run if no double tap arrives in time.
          mediaPlayPauseKeyHandler.setPendingPlayPauseTask(callerInfo, keyEvent);
          return true;
        }
        break;
      default:
        // If another key is pressed within double tap timeout, make play/pause as a single tap to
        // handle media keys in order.
        mediaPlayPauseKeyHandler.flush();
        break;
    }

    if (!isMediaNotificationControllerConnected()) {
      if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && doubleTapCompleted) {
        // Double tap completion for legacy when media notification controller is disabled.
        sessionLegacyStub.onSkipToNext();
        return true;
      } else if (callerInfo.getControllerVersion() != ControllerInfo.LEGACY_CONTROLLER_VERSION) {
        sessionLegacyStub.getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);
        return true;
      }
      // This is an unhandled framework event. Return false to let the framework resolve by calling
      // `MediaSessionCompat.Callback.onXyz()`.
      return false;
    }
    // Send from media notification controller.
    return applyMediaButtonKeyEvent(keyEvent, doubleTapCompleted);
  }

  private boolean applyMediaButtonKeyEvent(KeyEvent keyEvent, boolean doubleTapCompleted) {
    ControllerInfo controllerInfo = checkNotNull(instance.getMediaNotificationControllerInfo());
    Runnable command;
    int keyCode = keyEvent.getKeyCode();
    if ((keyCode == KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KEYCODE_MEDIA_PLAY)
        && doubleTapCompleted) {
      keyCode = KEYCODE_MEDIA_NEXT;
    }
    switch (keyCode) {
      case KEYCODE_MEDIA_PLAY_PAUSE:
        command =
            getPlayerWrapper().getPlayWhenReady()
                ? () -> sessionStub.pauseForControllerInfo(controllerInfo, UNKNOWN_SEQUENCE_NUMBER)
                : () -> sessionStub.playForControllerInfo(controllerInfo, UNKNOWN_SEQUENCE_NUMBER);
        break;
      case KEYCODE_MEDIA_PLAY:
        command = () -> sessionStub.playForControllerInfo(controllerInfo, UNKNOWN_SEQUENCE_NUMBER);
        break;
      case KEYCODE_MEDIA_PAUSE:
        command = () -> sessionStub.pauseForControllerInfo(controllerInfo, UNKNOWN_SEQUENCE_NUMBER);
        break;
      case KEYCODE_MEDIA_NEXT: // Fall through.
      case KEYCODE_MEDIA_SKIP_FORWARD:
        command =
            () -> sessionStub.seekToNextForControllerInfo(controllerInfo, UNKNOWN_SEQUENCE_NUMBER);
        break;
      case KEYCODE_MEDIA_PREVIOUS: // Fall through.
      case KEYCODE_MEDIA_SKIP_BACKWARD:
        command =
            () ->
                sessionStub.seekToPreviousForControllerInfo(
                    controllerInfo, UNKNOWN_SEQUENCE_NUMBER);
        break;
      case KEYCODE_MEDIA_FAST_FORWARD:
        command =
            () -> sessionStub.seekForwardForControllerInfo(controllerInfo, UNKNOWN_SEQUENCE_NUMBER);
        break;
      case KEYCODE_MEDIA_REWIND:
        command =
            () -> sessionStub.seekBackForControllerInfo(controllerInfo, UNKNOWN_SEQUENCE_NUMBER);
        break;
      case KEYCODE_MEDIA_STOP:
        command = () -> sessionStub.stopForControllerInfo(controllerInfo, UNKNOWN_SEQUENCE_NUMBER);
        break;
      default:
        return false;
    }
    postOrRun(
        getApplicationHandler(),
        () -> {
          command.run();
          sessionStub.getConnectedControllersManager().flushCommandQueue(controllerInfo);
        });
    return true;
  }

  private void postOrRunOnApplicationHandler(Runnable runnable) {
    Util.postOrRun(getApplicationHandler(), runnable);
  }

  /* @FunctionalInterface */
  interface RemoteControllerTask {

    void run(ControllerCb controller, int seq) throws RemoteException;
  }

  private static class PlayerListener implements Player.Listener {

    private final WeakReference<MediaSessionImpl> session;
    private final WeakReference<PlayerWrapper> player;

    public PlayerListener(MediaSessionImpl session, PlayerWrapper player) {
      this.session = new WeakReference<>(session);
      this.player = new WeakReference<>(player);
    }

    @Override
    public void onPlayerError(PlaybackException error) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithPlayerError(error);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onPlayerError(seq, error));
    }

    @Override
    public void onMediaItemTransition(
        @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithMediaItemTransitionReason(reason);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onMediaItemTransition(seq, mediaItem, reason));
    }

    @Override
    public void onPlayWhenReadyChanged(
        boolean playWhenReady, @Player.PlayWhenReadyChangeReason int reason) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo =
          session.playerInfo.copyWithPlayWhenReady(
              playWhenReady, reason, session.playerInfo.playbackSuppressionReason);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onPlayWhenReadyChanged(seq, playWhenReady, reason));
    }

    @Override
    public void onPlaybackSuppressionReasonChanged(@Player.PlaybackSuppressionReason int reason) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo =
          session.playerInfo.copyWithPlayWhenReady(
              session.playerInfo.playWhenReady,
              session.playerInfo.playWhenReadyChangeReason,
              reason);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onPlaybackSuppressionReasonChanged(seq, reason));
    }

    @Override
    public void onPlaybackStateChanged(@Player.State int playbackState) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo =
          session.playerInfo.copyWithPlaybackState(playbackState, player.getPlayerError());
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> {
            callback.onPlaybackStateChanged(seq, playbackState, player.getPlayerError());
          });
    }

    @Override
    public void onIsPlayingChanged(boolean isPlaying) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithIsPlaying(isPlaying);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onIsPlayingChanged(seq, isPlaying));
      session.schedulePeriodicSessionPositionInfoChanges();
    }

    @Override
    public void onIsLoadingChanged(boolean isLoading) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithIsLoading(isLoading);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onIsLoadingChanged(seq, isLoading));
      session.schedulePeriodicSessionPositionInfoChanges();
    }

    @Override
    public void onPositionDiscontinuity(
        PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }

      session.playerInfo =
          session.playerInfo.copyWithPositionInfos(oldPosition, newPosition, reason);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) ->
              callback.onPositionDiscontinuity(seq, oldPosition, newPosition, reason));
    }

    @Override
    public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithPlaybackParameters(playbackParameters);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onPlaybackParametersChanged(seq, playbackParameters));
    }

    @Override
    public void onSeekBackIncrementChanged(long seekBackIncrementMs) {
      MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithSeekBackIncrement(seekBackIncrementMs);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onSeekBackIncrementChanged(seq, seekBackIncrementMs));
    }

    @Override
    public void onSeekForwardIncrementChanged(long seekForwardIncrementMs) {
      MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithSeekForwardIncrement(seekForwardIncrementMs);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onSeekForwardIncrementChanged(seq, seekForwardIncrementMs));
    }

    @Override
    public void onTimelineChanged(Timeline timeline, @Player.TimelineChangeReason int reason) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo =
          session.playerInfo.copyWithTimelineAndSessionPositionInfo(
              timeline, player.createSessionPositionInfoForBundling(), reason);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ false, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onTimelineChanged(seq, timeline, reason));
    }

    @Override
    public void onPlaylistMetadataChanged(MediaMetadata playlistMetadata) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      session.playerInfo = session.playerInfo.copyWithPlaylistMetadata(playlistMetadata);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onPlaylistMetadataChanged(seq, playlistMetadata));
    }

    @Override
    public void onRepeatModeChanged(@RepeatMode int repeatMode) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithRepeatMode(repeatMode);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onRepeatModeChanged(seq, repeatMode));
    }

    @Override
    public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithShuffleModeEnabled(shuffleModeEnabled);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onShuffleModeEnabledChanged(seq, shuffleModeEnabled));
    }

    @Override
    public void onAudioAttributesChanged(AudioAttributes attributes) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithAudioAttributes(attributes);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (controller, seq) -> controller.onAudioAttributesChanged(seq, attributes));
    }

    @Override
    public void onVideoSizeChanged(VideoSize size) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      session.playerInfo = session.playerInfo.copyWithVideoSize(size);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onVideoSizeChanged(seq, size));
    }

    @Override
    public void onVolumeChanged(@FloatRange(from = 0, to = 1) float volume) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      session.playerInfo = session.playerInfo.copyWithVolume(volume);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onVolumeChanged(seq, volume));
    }

    @Override
    public void onCues(CueGroup cueGroup) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = new PlayerInfo.Builder(session.playerInfo).setCues(cueGroup).build();
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
    }

    @Override
    public void onDeviceInfoChanged(DeviceInfo deviceInfo) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithDeviceInfo(deviceInfo);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onDeviceInfoChanged(seq, deviceInfo));
    }

    @Override
    public void onDeviceVolumeChanged(int volume, boolean muted) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithDeviceVolume(volume, muted);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onDeviceVolumeChanged(seq, volume, muted));
    }

    @Override
    public void onAvailableCommandsChanged(Player.Commands availableCommands) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.handleAvailablePlayerCommandsChanged(availableCommands);
    }

    @Override
    public void onTracksChanged(Tracks tracks) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithCurrentTracks(tracks);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ false);
      session.dispatchRemoteControllerTaskWithoutReturn(
          (callback, seq) -> callback.onTracksChanged(seq, tracks));
    }

    @Override
    public void onTrackSelectionParametersChanged(TrackSelectionParameters parameters) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithTrackSelectionParameters(parameters);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskWithoutReturn(
          (callback, seq) -> callback.onTrackSelectionParametersChanged(seq, parameters));
    }

    @Override
    public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = session.playerInfo.copyWithMediaMetadata(mediaMetadata);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onMediaMetadataChanged(seq, mediaMetadata));
    }

    @Override
    public void onRenderedFirstFrame() {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      session.dispatchRemoteControllerTaskWithoutReturn(ControllerCb::onRenderedFirstFrame);
    }

    @Override
    public void onMaxSeekToPreviousPositionChanged(long maxSeekToPreviousPositionMs) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo =
          session.playerInfo.copyWithMaxSeekToPreviousPositionMs(maxSeekToPreviousPositionMs);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(
          /* excludeTimeline= */ true, /* excludeTracks= */ true);
    }

    @Nullable
    private MediaSessionImpl getSession() {
      return this.session.get();
    }
  }

  /**
   * A handler for double click detection.
   *
   * <p>All methods must be called on the application thread.
   */
  private class MediaPlayPauseKeyHandler extends Handler {

    @Nullable private Runnable playPauseTask;

    public MediaPlayPauseKeyHandler(Looper applicationLooper) {
      super(applicationLooper);
    }

    public void setPendingPlayPauseTask(ControllerInfo controllerInfo, KeyEvent keyEvent) {
      playPauseTask =
          () -> {
            if (isMediaNotificationController(controllerInfo)) {
              applyMediaButtonKeyEvent(keyEvent, /* doubleTapCompleted= */ false);
            } else {
              sessionLegacyStub.handleMediaPlayPauseOnHandler(
                  checkNotNull(controllerInfo.getRemoteUserInfo()));
            }
            playPauseTask = null;
          };
      postDelayed(playPauseTask, ViewConfiguration.getDoubleTapTimeout());
    }

    @Nullable
    public Runnable clearPendingPlayPauseTask() {
      if (playPauseTask != null) {
        removeCallbacks(playPauseTask);
        Runnable task = playPauseTask;
        playPauseTask = null;
        return task;
      }
      return null;
    }

    public boolean hasPendingPlayPauseTask() {
      return playPauseTask != null;
    }

    public void flush() {
      @Nullable Runnable task = clearPendingPlayPauseTask();
      if (task != null) {
        postOrRun(this, task);
      }
    }
  }

  private class PlayerInfoChangedHandler extends Handler {

    private static final int MSG_PLAYER_INFO_CHANGED = 1;

    private boolean excludeTimeline;
    private boolean excludeTracks;

    public PlayerInfoChangedHandler(Looper looper) {
      super(looper);
      excludeTimeline = true;
      excludeTracks = true;
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_PLAYER_INFO_CHANGED) {
        playerInfo =
            playerInfo.copyWithTimelineAndSessionPositionInfo(
                getPlayerWrapper().getCurrentTimelineWithCommandCheck(),
                getPlayerWrapper().createSessionPositionInfoForBundling(),
                playerInfo.timelineChangeReason);
        dispatchOnPlayerInfoChanged(playerInfo, excludeTimeline, excludeTracks);
        excludeTimeline = true;
        excludeTracks = true;
      } else {
        throw new IllegalStateException("Invalid message what=" + msg.what);
      }
    }

    public boolean hasPendingPlayerInfoChangedUpdate() {
      return hasMessages(MSG_PLAYER_INFO_CHANGED);
    }

    public void sendPlayerInfoChangedMessage(boolean excludeTimeline, boolean excludeTracks) {
      this.excludeTimeline = this.excludeTimeline && excludeTimeline;
      this.excludeTracks = this.excludeTracks && excludeTracks;
      if (!hasMessages(MSG_PLAYER_INFO_CHANGED)) {
        sendEmptyMessage(MSG_PLAYER_INFO_CHANGED);
      }
    }
  }

  @RequiresApi(21)
  private static final class Api21 {
    @DoNotInline
    public static boolean isTvApp(Context context) {
      return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
  }
}
