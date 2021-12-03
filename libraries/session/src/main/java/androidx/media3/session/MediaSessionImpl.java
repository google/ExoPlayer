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

import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.castNonNull;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.SessionResult.RESULT_ERROR_SESSION_DISCONNECTED;
import static androidx.media3.session.SessionResult.RESULT_ERROR_UNKNOWN;
import static androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import androidx.annotation.FloatRange;
import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
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
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerCb;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.session.MediaSession.MediaItemFiller;
import androidx.media3.session.MediaSession.SessionCallback;
import androidx.media3.session.SequencedFutureManager.SequencedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.initialization.qual.Initialized;

/* package */ class MediaSessionImpl {

  // Create a static lock for synchronize methods below.
  // We'd better not use MediaSessionImplBase.class for synchronized(), which indirectly exposes
  // lock object to the outside of the class.
  private static final Object STATIC_LOCK = new Object();

  private static final String WRONG_THREAD_ERROR_MESSAGE =
      "Player callback method is called from a wrong thread. "
          + "See javadoc of MediaSession for details.";

  private static final long DEFAULT_SESSION_POSITION_UPDATE_DELAY_MS = 3_000;

  @GuardedBy("STATIC_LOCK")
  private static boolean componentNamesInitialized = false;

  @GuardedBy("STATIC_LOCK")
  @Nullable
  private static ComponentName serviceComponentName;

  public static final String TAG = "MSImplBase";

  private static final SessionResult RESULT_WHEN_CLOSED = new SessionResult(RESULT_INFO_SKIPPED);

  protected final Object lock = new Object();

  private final Uri sessionUri;

  private final PlayerInfoChangedHandler onPlayerInfoChangedHandler;

  private final SessionCallback callback;
  private final MediaItemFiller mediaItemFiller;

  private final Context context;
  private final MediaSessionStub sessionStub;
  private final MediaSessionLegacyStub sessionLegacyStub;
  private final String sessionId;
  private final SessionToken sessionToken;
  private final MediaSession instance;
  @Nullable private final PendingIntent sessionActivity;
  private final PendingIntent mediaButtonIntent;
  @Nullable private final BroadcastReceiver broadcastReceiver;
  private final Handler applicationHandler;

  @Nullable private PlayerListener playerListener;

  private PlayerInfo playerInfo;
  private PlayerInfo lastPlayerInfo;

  private PlayerWrapper playerWrapper;

  @GuardedBy("lock")
  @Nullable
  private MediaSessionServiceLegacyStub browserServiceLegacyStub;

  @GuardedBy("lock")
  private boolean closed;

  // Should be only accessed on the application looper
  private long sessionPositionUpdateDelayMs;

  @Nullable private MediaSession.ForegroundServiceEventCallback foregroundServiceEventCallback;

  public MediaSessionImpl(
      MediaSession instance,
      Context context,
      String id,
      Player player,
      @Nullable PendingIntent sessionActivity,
      SessionCallback callback,
      MediaItemFiller mediaItemFiller,
      Bundle tokenExtras) {
    this.context = context;
    this.instance = instance;

    @SuppressWarnings("nullness:assignment")
    @Initialized
    MediaSessionImpl thisRef = this;

    sessionStub = new MediaSessionStub(thisRef);
    this.sessionActivity = sessionActivity;

    applicationHandler = new Handler(player.getApplicationLooper());
    this.callback = callback;
    this.mediaItemFiller = mediaItemFiller;

    playerInfo = PlayerInfo.DEFAULT;
    lastPlayerInfo = PlayerInfo.DEFAULT;
    onPlayerInfoChangedHandler = new PlayerInfoChangedHandler(player.getApplicationLooper());

    sessionId = id;
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
            context.getPackageName(),
            sessionStub,
            tokenExtras);

    @Nullable ComponentName mbrComponent;
    synchronized (STATIC_LOCK) {
      if (!componentNamesInitialized) {
        serviceComponentName =
            getServiceComponentByAction(context, MediaLibraryService.SERVICE_INTERFACE);
        if (serviceComponentName == null) {
          serviceComponentName =
              getServiceComponentByAction(context, MediaSessionService.SERVICE_INTERFACE);
        }
        componentNamesInitialized = true;
      }
      mbrComponent = serviceComponentName;
    }
    int pendingIntentFlagMutable = Util.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
    if (mbrComponent == null) {
      // No service to revive playback after it's dead.
      // Create a PendingIntent that points to the runtime broadcast receiver.
      Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON, sessionUri);
      intent.setPackage(context.getPackageName());
      mediaButtonIntent =
          PendingIntent.getBroadcast(
              context, /* requestCode= */ 0, intent, pendingIntentFlagMutable);

      // Creates a fake ComponentName for MediaSessionCompat in pre-L.
      mbrComponent = new ComponentName(context, context.getClass());

      // Create and register a BroadcastReceiver for receiving PendingIntent.
      broadcastReceiver = new MediaButtonReceiver();
      IntentFilter filter = new IntentFilter(Intent.ACTION_MEDIA_BUTTON);
      filter.addDataScheme(castNonNull(sessionUri.getScheme()));
      context.registerReceiver(broadcastReceiver, filter);
    } else {
      // Has MediaSessionService to revive playback after it's dead.
      Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON, sessionUri);
      intent.setComponent(mbrComponent);
      if (Util.SDK_INT >= 26) {
        mediaButtonIntent =
            PendingIntent.getForegroundService(context, 0, intent, pendingIntentFlagMutable);
      } else {
        mediaButtonIntent = PendingIntent.getService(context, 0, intent, pendingIntentFlagMutable);
      }
      broadcastReceiver = null;
    }

    sessionLegacyStub =
        new MediaSessionLegacyStub(thisRef, mbrComponent, mediaButtonIntent, applicationHandler);

    PlayerWrapper playerWrapper = new PlayerWrapper(player);
    this.playerWrapper = playerWrapper;
    postOrRun(
        applicationHandler,
        () ->
            thisRef.setPlayerInternal(
                /* oldPlayerWrapper= */ null, /* newPlayerWrapper= */ playerWrapper));

    sessionPositionUpdateDelayMs = DEFAULT_SESSION_POSITION_UPDATE_DELAY_MS;
    applicationHandler.postDelayed(
        thisRef::notifyPeriodicSessionPositionInfoChangesOnHandler, sessionPositionUpdateDelayMs);
  }

  public void setPlayer(Player player) {
    if (player == playerWrapper.getWrappedPlayer()) {
      return;
    }
    setPlayerInternal(/* oldPlayerWrapper= */ playerWrapper, new PlayerWrapper(player));
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
    onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ false);
  }

  public void release() {
    synchronized (lock) {
      if (closed) {
        return;
      }
      closed = true;
    }
    applicationHandler.removeCallbacksAndMessages(null);
    try {
      postOrRun(
          applicationHandler,
          () -> {
            if (playerListener != null) {
              playerWrapper.removeListener(playerListener);
            }
            if (foregroundServiceEventCallback != null) {
              foregroundServiceEventCallback.onSessionReleased(instance);
            }
          });
    } catch (Exception e) {
      // Catch all exceptions to ensure the rest of this method to be executed as exceptions may be
      // thrown by user if, for example, the application thread is dead or removeListener throws an
      // exception.
      Log.w(TAG, "Exception thrown while closing", e);
    }
    sessionLegacyStub.release();
    mediaButtonIntent.cancel();
    if (broadcastReceiver != null) {
      context.unregisterReceiver(broadcastReceiver);
    }
    dispatchRemoteControllerTaskWithoutReturn(ControllerCb::onDisconnected);
  }

  public PlayerWrapper getPlayerWrapper() {
    return playerWrapper;
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
    controllers.addAll(
        sessionLegacyStub.getConnectedControllersManager().getConnectedControllers());
    return controllers;
  }

  public boolean isConnected(ControllerInfo controller) {
    return sessionStub.getConnectedControllersManager().isConnected(controller)
        || sessionLegacyStub.getConnectedControllersManager().isConnected(controller);
  }

  public ListenableFuture<SessionResult> setCustomLayout(
      ControllerInfo controller, List<CommandButton> layout) {
    return dispatchRemoteControllerTask(
        controller,
        new RemoteControllerTask() {
          @Override
          public void run(ControllerCb controller, int seq) throws RemoteException {
            controller.setCustomLayout(seq, layout);
          }
        });
  }

  public void setAvailableCommands(
      ControllerInfo controller, SessionCommands sessionCommands, Player.Commands playerCommands) {
    if (sessionStub.getConnectedControllersManager().isConnected(controller)) {
      sessionStub
          .getConnectedControllersManager()
          .updateCommandsFromSession(controller, sessionCommands, playerCommands);
      dispatchRemoteControllerTaskWithoutReturn(
          controller,
          new RemoteControllerTask() {
            @Override
            public void run(ControllerCb callback, int seq) throws RemoteException {
              callback.onAvailableCommandsChangedFromSession(seq, sessionCommands, playerCommands);
            }
          });
      onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ false);
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

  private void dispatchOnPlayerInfoChanged(boolean excludeTimeline) {
    if (foregroundServiceEventCallback != null) {
      foregroundServiceEventCallback.onPlayerInfoChanged(instance, lastPlayerInfo, playerInfo);
    }
    lastPlayerInfo = playerInfo;

    List<ControllerInfo> controllers =
        sessionStub.getConnectedControllersManager().getConnectedControllers();
    for (int i = 0; i < controllers.size(); i++) {
      ControllerInfo controller = controllers.get(i);
      try {
        int seq;
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
        checkStateNotNull(controller.getControllerCb())
            .onPlayerInfoChanged(
                seq,
                playerInfo,
                !sessionStub
                    .getConnectedControllersManager()
                    .isPlayerCommandAvailable(controller, Player.COMMAND_GET_TIMELINE),
                !sessionStub
                    .getConnectedControllersManager()
                    .isPlayerCommandAvailable(controller, Player.COMMAND_GET_MEDIA_ITEMS_METADATA),
                !sessionStub
                    .getConnectedControllersManager()
                    .isPlayerCommandAvailable(controller, Player.COMMAND_GET_TEXT),
                excludeTimeline);
      } catch (DeadObjectException e) {
        onDeadObjectException(controller, e);
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

  public void connectFromService(
      IMediaController caller,
      int controllerVersion,
      String packageName,
      int pid,
      int uid,
      Bundle connectionHints) {
    sessionStub.connect(
        caller, controllerVersion, packageName, pid, uid, checkStateNotNull(connectionHints));
  }

  public MediaSessionCompat getSessionCompat() {
    return sessionLegacyStub.getSessionCompat();
  }

  public void setLegacyControllerConnectionTimeoutMs(long timeoutMs) {
    sessionLegacyStub.setLegacyControllerDisconnectTimeoutMs(timeoutMs);
  }

  protected MediaSession getInstance() {
    return instance;
  }

  protected Context getContext() {
    return context;
  }

  protected Handler getApplicationHandler() {
    return applicationHandler;
  }

  protected SessionCallback getCallback() {
    return callback;
  }

  protected MediaItemFiller getMediaItemFiller() {
    return mediaItemFiller;
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

    applicationHandler.removeCallbacks(this::notifyPeriodicSessionPositionInfoChangesOnHandler);
    applicationHandler.postDelayed(
        this::notifyPeriodicSessionPositionInfoChangesOnHandler, updateDelayMs);
  }

  protected void setForegroundServiceEventCallback(
      MediaSession.ForegroundServiceEventCallback foregroundServiceEventCallback) {
    this.foregroundServiceEventCallback = foregroundServiceEventCallback;
  }

  protected void clearForegroundServiceEventCallback() {
    foregroundServiceEventCallback = null;
  }

  @Nullable
  protected MediaSessionServiceLegacyStub getLegacyBrowserService() {
    synchronized (lock) {
      return browserServiceLegacyStub;
    }
  }

  private void dispatchRemoteControllerTaskToLegacyStub(RemoteControllerTask task) {
    try {
      task.run(sessionLegacyStub.getControllerLegacyCbForBroadcast(), /* seq= */ 0);
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
      task.run(checkStateNotNull(controller.getControllerCb()), seq);
    } catch (DeadObjectException e) {
      onDeadObjectException(controller, e);
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
      task.run(checkStateNotNull(controller.getControllerCb()), seq);
      return future;
    } catch (DeadObjectException e) {
      onDeadObjectException(controller, e);
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
  private void onDeadObjectException(ControllerInfo controller, DeadObjectException e) {
    // Note: Only removing from MediaSessionStub and ignoring (legacy) stubs would be fine for
    //       now. Because calls to the legacy stubs doesn't throw DeadObjectException.
    sessionStub.getConnectedControllersManager().removeController(controller);
  }

  @Nullable
  private static ComponentName getServiceComponentByAction(Context context, String action) {
    PackageManager pm = context.getPackageManager();
    Intent queryIntent = new Intent(action);
    queryIntent.setPackage(context.getPackageName());
    List<ResolveInfo> resolveInfos = pm.queryIntentServices(queryIntent, /* flags= */ 0);
    if (resolveInfos == null || resolveInfos.isEmpty()) {
      return null;
    }
    ResolveInfo resolveInfo = resolveInfos.get(0);
    return new ComponentName(resolveInfo.serviceInfo.packageName, resolveInfo.serviceInfo.name);
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
    dispatchRemoteControllerTaskWithoutReturn(
        (callback, seq) -> callback.onPeriodicSessionPositionInfoChanged(seq, sessionPositionInfo));
    if (sessionPositionUpdateDelayMs > 0) {
      applicationHandler.postDelayed(
          this::notifyPeriodicSessionPositionInfoChangesOnHandler, sessionPositionUpdateDelayMs);
    }
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      // Note: OK to omit mediaItem here, because PlayerInfo changed message will copy playerInfo
      //       with sessionPositionInfo, which includes current window index.
      session.playerInfo = session.playerInfo.copyWithMediaItemTransitionReason(reason);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
              session.playerInfo.playWhenReadyChangedReason,
              reason);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onIsPlayingChanged(seq, isPlaying));
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onIsLoadingChanged(seq, isLoading));
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.playerInfo = session.playerInfo.copyWithTimeline(timeline);
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ false);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onVolumeChanged(seq, volume));
    }

    @Override
    public void onCues(List<Cue> cues) {
      @Nullable MediaSessionImpl session = getSession();
      if (session == null) {
        return;
      }
      session.verifyApplicationThread();
      @Nullable PlayerWrapper player = this.player.get();
      if (player == null) {
        return;
      }
      session.playerInfo = new PlayerInfo.Builder(session.playerInfo).setCues(cues).build();
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ false);
      session.dispatchRemoteControllerTaskWithoutReturn(
          (callback, seq) -> callback.onAvailableCommandsChangedFromPlayer(seq, availableCommands));

      // Forcefully update playback info to update VolumeProviderCompat in case
      // COMMAND_ADJUST_DEVICE_VOLUME or COMMAND_SET_DEVICE_VOLUME value has changed.
      session.dispatchRemoteControllerTaskToLegacyStub(
          (callback, seq) -> callback.onDeviceInfoChanged(seq, session.playerInfo.deviceInfo));
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
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
      session.onPlayerInfoChangedHandler.sendPlayerInfoChangedMessage(/* excludeTimeline= */ true);
    }

    @Nullable
    private MediaSessionImpl getSession() {
      return this.session.get();
    }
  }

  // TODO(b/193193462): Replace this with androidx.media.session.MediaButtonReceiver
  private final class MediaButtonReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
      if (!Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
        return;
      }
      Uri sessionUri = intent.getData();
      if (!Util.areEqual(sessionUri, MediaSessionImpl.this.sessionUri)) {
        return;
      }
      KeyEvent keyEvent = (KeyEvent) intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
      if (keyEvent == null) {
        return;
      }
      getSessionCompat().getController().dispatchMediaButtonEvent(keyEvent);
    }
  }

  private class PlayerInfoChangedHandler extends Handler {

    private static final int MSG_PLAYER_INFO_CHANGED = 1;

    private boolean excludeTimeline;

    public PlayerInfoChangedHandler(Looper looper) {
      super(looper);
      excludeTimeline = true;
    }

    @Override
    public void handleMessage(Message msg) {
      if (msg.what == MSG_PLAYER_INFO_CHANGED) {
        playerInfo =
            playerInfo.copyWithSessionPositionInfo(
                getPlayerWrapper().createSessionPositionInfoForBundling());
        dispatchOnPlayerInfoChanged(excludeTimeline);
        excludeTimeline = true;
      } else {
        throw new IllegalStateException("Invalid message what=" + msg.what);
      }
    }

    public void sendPlayerInfoChangedMessage(boolean excludeTimeline) {
      this.excludeTimeline = this.excludeTimeline && excludeTimeline;
      if (!onPlayerInfoChangedHandler.hasMessages(MSG_PLAYER_INFO_CHANGED)) {
        onPlayerInfoChangedHandler.sendEmptyMessage(MSG_PLAYER_INFO_CHANGED);
      }
    }
  }
}
