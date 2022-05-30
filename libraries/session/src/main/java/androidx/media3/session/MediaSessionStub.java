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

import static androidx.media3.common.Player.COMMAND_ADJUST_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_PREPARE;
import static androidx.media3.common.Player.COMMAND_SEEK_BACK;
import static androidx.media3.common.Player.COMMAND_SEEK_FORWARD;
import static androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_DEFAULT_POSITION;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_DEVICE_VOLUME;
import static androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEMS_METADATA;
import static androidx.media3.common.Player.COMMAND_SET_REPEAT_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SHUFFLE_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH;
import static androidx.media3.common.Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS;
import static androidx.media3.common.Player.COMMAND_SET_VIDEO_SURFACE;
import static androidx.media3.common.Player.COMMAND_SET_VOLUME;
import static androidx.media3.common.Player.COMMAND_STOP;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_CUSTOM;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_SEARCH;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_SESSION_SET_MEDIA_URI;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_SESSION_SET_RATING;

import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.media.MediaSessionManager;
import androidx.media3.common.BundleListRetriever;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaLibraryInfo;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Rating;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.util.BundleableUtil;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaLibraryService.LibraryParams;
import androidx.media3.session.MediaLibraryService.MediaLibrarySession;
import androidx.media3.session.MediaSession.ControllerCb;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.session.SessionCommand.CommandCode;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

/**
 * Class that handles incoming commands from {@link MediaController} and {@link MediaBrowser} to
 * both {@link MediaSession} and {@link MediaLibrarySession}.
 */
// We cannot create a subclass for library service specific function because AIDL doesn't support
// subclassing and it's generated stub class is an abstract class.
/* package */ final class MediaSessionStub extends IMediaSession.Stub {

  private static final String TAG = "MediaSessionStub";

  private final WeakReference<MediaSessionImpl> sessionImpl;
  private final MediaSessionManager sessionManager;
  private final ConnectedControllersManager<IBinder> connectedControllersManager;
  private final Set<ControllerInfo> pendingControllers;

  public MediaSessionStub(MediaSessionImpl sessionImpl) {
    // Initialize members with params.
    this.sessionImpl = new WeakReference<>(sessionImpl);
    sessionManager = MediaSessionManager.getSessionManager(sessionImpl.getContext());
    connectedControllersManager = new ConnectedControllersManager<>(sessionImpl);
    pendingControllers = Collections.newSetFromMap(new ConcurrentHashMap<>());
  }

  public ConnectedControllersManager<IBinder> getConnectedControllersManager() {
    return connectedControllersManager;
  }

  private static <K extends MediaSessionImpl> void sendSessionResult(
      K sessionImpl, ControllerInfo controller, int seq, @SessionResult.Code int resultCode) {
    sendSessionResult(sessionImpl, controller, seq, new SessionResult(resultCode));
  }

  private static <K extends MediaSessionImpl> void sendSessionResult(
      K sessionImpl, ControllerInfo controller, int seq, SessionResult result) {
    try {
      checkStateNotNull(controller.getControllerCb()).onSessionResult(seq, result);
    } catch (RemoteException e) {
      Log.w(TAG, "Failed to send result to controller " + controller, e);
    }
  }

  private static <K extends MediaSessionImpl> void sendSessionResultWhenReady(
      K sessionImpl, ControllerInfo controller, int seq, ListenableFuture<SessionResult> future) {
    future.addListener(
        () -> {
          SessionResult result;
          try {
            result = checkNotNull(future.get(), "SessionResult must not be null");
          } catch (CancellationException unused) {
            result = new SessionResult(SessionResult.RESULT_INFO_SKIPPED);
          } catch (ExecutionException | InterruptedException unused) {
            result = new SessionResult(SessionResult.RESULT_ERROR_UNKNOWN);
          }
          sendSessionResult(sessionImpl, controller, seq, result);
        },
        MoreExecutors.directExecutor());
  }

  private static <K extends MediaSessionImpl> void handleMediaItemsWhenReady(
      K sessionImpl,
      ControllerInfo controller,
      int seq,
      ListenableFuture<List<MediaItem>> future,
      MediaItemPlayerTask mediaItemPlayerTask) {
    future.addListener(
        () -> {
          SessionResult result;
          try {
            List<MediaItem> mediaItems =
                checkNotNull(future.get(), "MediaItem list must not be null");
            postOrRun(
                sessionImpl.getApplicationHandler(),
                () -> mediaItemPlayerTask.run(sessionImpl.getPlayerWrapper(), mediaItems));
            result = new SessionResult(SessionResult.RESULT_SUCCESS);
          } catch (CancellationException unused) {
            result = new SessionResult(SessionResult.RESULT_INFO_SKIPPED);
          } catch (ExecutionException | InterruptedException exception) {
            result =
                new SessionResult(
                    exception.getCause() instanceof UnsupportedOperationException
                        ? SessionResult.RESULT_ERROR_NOT_SUPPORTED
                        : SessionResult.RESULT_ERROR_UNKNOWN);
          }
          sendSessionResult(sessionImpl, controller, seq, result);
        },
        MoreExecutors.directExecutor());
  }

  private static void sendLibraryResult(
      ControllerInfo controller, int seq, LibraryResult<?> result) {
    try {
      checkStateNotNull(controller.getControllerCb()).onLibraryResult(seq, result);
    } catch (RemoteException e) {
      Log.w(TAG, "Failed to send result to browser " + controller, e);
    }
  }

  private static <V, K extends MediaSessionImpl> void sendLibraryResultWhenReady(
      K sessionImpl,
      ControllerInfo controller,
      int seq,
      ListenableFuture<LibraryResult<V>> future) {
    future.addListener(
        () -> {
          LibraryResult<V> result;
          try {
            result = checkNotNull(future.get(), "LibraryResult must not be null");
          } catch (CancellationException unused) {
            result = LibraryResult.ofError(LibraryResult.RESULT_INFO_SKIPPED);
          } catch (ExecutionException | InterruptedException unused) {
            result = LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN);
          }
          sendLibraryResult(controller, seq, result);
        },
        MoreExecutors.directExecutor());
  }

  private <T, K extends MediaSessionImpl> void dispatchSessionTaskWithPlayerCommand(
      IMediaController caller,
      int seq,
      @Player.Command int command,
      SessionTask<T, K> task,
      PostSessionTask<T, K> postTask) {
    long token = Binder.clearCallingIdentity();
    try {
      @SuppressWarnings({"unchecked", "cast.unsafe"})
      @Nullable
      K sessionImpl = (K) this.sessionImpl.get();
      if (sessionImpl == null || sessionImpl.isReleased()) {
        return;
      }
      @Nullable
      ControllerInfo controller = connectedControllersManager.getController(caller.asBinder());
      if (controller == null) {
        return;
      }
      if (command == COMMAND_SET_VIDEO_SURFACE) {
        postOrRun(
            sessionImpl.getApplicationHandler(),
            getSessionTaskWithPlayerCommandRunnable(
                controller, seq, command, sessionImpl, task, postTask));
      } else {
        connectedControllersManager.addToCommandQueue(
            controller,
            getSessionTaskWithPlayerCommandRunnable(
                controller, seq, command, sessionImpl, task, postTask));
      }
    } finally {
      Binder.restoreCallingIdentity(token);
    }
  }

  private <T, K extends MediaSessionImpl> Runnable getSessionTaskWithPlayerCommandRunnable(
      ControllerInfo controller,
      int seq,
      @Player.Command int command,
      K sessionImpl,
      SessionTask<T, K> task,
      PostSessionTask<T, K> postTask) {
    return () -> {
      if (!connectedControllersManager.isPlayerCommandAvailable(controller, command)) {
        sendSessionResult(
            sessionImpl,
            controller,
            seq,
            new SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED));
        return;
      }
      @SessionResult.Code
      int resultCode = sessionImpl.onPlayerCommandRequestOnHandler(controller, command);
      if (resultCode != SessionResult.RESULT_SUCCESS) {
        // Don't run rejected command.
        sendSessionResult(sessionImpl, controller, seq, new SessionResult(resultCode));
        return;
      }
      T result = task.run(sessionImpl, controller);
      postTask.run(sessionImpl, controller, seq, result);
    };
  }

  private <T> void dispatchSessionTaskWithLibrarySessionCommand(
      IMediaController caller,
      int seq,
      @CommandCode int commandCode,
      SessionTask<T, MediaLibrarySessionImpl> task,
      PostSessionTask<T, MediaLibrarySessionImpl> postTask) {
    dispatchSessionTaskWithSessionCommandInternal(
        caller, seq, /* sessionCommand= */ null, commandCode, task, postTask);
  }

  private <T, K extends MediaSessionImpl> void dispatchSessionTaskWithSessionCommand(
      IMediaController caller,
      int seq,
      @CommandCode int commandCode,
      SessionTask<T, K> task,
      PostSessionTask<T, K> postTask) {
    dispatchSessionTaskWithSessionCommandInternal(
        caller, seq, /* sessionCommand= */ null, commandCode, task, postTask);
  }

  private <T, K extends MediaSessionImpl> void dispatchSessionTaskWithSessionCommand(
      IMediaController caller,
      int seq,
      SessionCommand sessionCommand,
      SessionTask<T, K> task,
      PostSessionTask<T, K> postTask) {
    dispatchSessionTaskWithSessionCommandInternal(
        caller, seq, sessionCommand, COMMAND_CODE_CUSTOM, task, postTask);
  }

  private <T, K extends MediaSessionImpl> void dispatchSessionTaskWithSessionCommandInternal(
      IMediaController caller,
      int seq,
      @Nullable SessionCommand sessionCommand,
      @CommandCode int commandCode,
      SessionTask<T, K> task,
      PostSessionTask<T, K> postTask) {
    long token = Binder.clearCallingIdentity();
    try {
      @SuppressWarnings({"unchecked", "cast.unsafe"})
      @Nullable
      K sessionImpl = (K) this.sessionImpl.get();
      if (sessionImpl == null || sessionImpl.isReleased()) {
        return;
      }
      @Nullable
      ControllerInfo controller = connectedControllersManager.getController(caller.asBinder());
      if (controller == null) {
        return;
      }
      postOrRun(
          sessionImpl.getApplicationHandler(),
          () -> {
            if (!connectedControllersManager.isConnected(controller)) {
              return;
            }
            if (sessionCommand != null) {
              if (!connectedControllersManager.isSessionCommandAvailable(
                  controller, sessionCommand)) {
                sendSessionResult(
                    sessionImpl,
                    controller,
                    seq,
                    new SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED));
                return;
              }
            } else {
              if (!connectedControllersManager.isSessionCommandAvailable(controller, commandCode)) {
                sendSessionResult(
                    sessionImpl,
                    controller,
                    seq,
                    new SessionResult(SessionResult.RESULT_ERROR_PERMISSION_DENIED));
                return;
              }
            }
            T result = task.run(sessionImpl, controller);
            postTask.run(sessionImpl, controller, seq, result);
          });
    } finally {
      Binder.restoreCallingIdentity(token);
    }
  }

  public void connect(
      IMediaController caller,
      int controllerVersion,
      String callingPackage,
      int pid,
      int uid,
      Bundle connectionHints) {
    MediaSessionManager.RemoteUserInfo remoteUserInfo =
        new MediaSessionManager.RemoteUserInfo(callingPackage, pid, uid);
    ControllerInfo controllerInfo =
        new ControllerInfo(
            remoteUserInfo,
            controllerVersion,
            sessionManager.isTrustedForMediaControl(remoteUserInfo),
            new Controller2Cb(caller),
            connectionHints);
    @Nullable MediaSessionImpl sessionImpl = this.sessionImpl.get();
    if (sessionImpl == null || sessionImpl.isReleased()) {
      try {
        caller.onDisconnected(/* seq= */ 0);
      } catch (RemoteException e) {
        // Controller may be died prematurely.
        // Not an issue because we'll ignore it anyway.
      }
      return;
    }
    pendingControllers.add(controllerInfo);
    postOrRun(
        sessionImpl.getApplicationHandler(),
        () -> {
          boolean connected = false;
          try {
            pendingControllers.remove(controllerInfo);
            if (sessionImpl.isReleased()) {
              return;
            }
            IBinder callbackBinder =
                checkStateNotNull((Controller2Cb) controllerInfo.getControllerCb())
                    .getCallbackBinder();
            MediaSession.ConnectionResult connectionResult =
                sessionImpl.onConnectOnHandler(controllerInfo);
            // Don't reject connection for the request from trusted app.
            // Otherwise server will fail to retrieve session's information to dispatch
            // media keys to.
            if (!connectionResult.isAccepted && !controllerInfo.isTrusted()) {
              return;
            }
            if (!connectionResult.isAccepted) {
              // For the accepted controller, send non-null allowed commands to keep connection.
              connectionResult =
                  MediaSession.ConnectionResult.accept(
                      SessionCommands.EMPTY, Player.Commands.EMPTY);
            }
            SequencedFutureManager sequencedFutureManager;
            if (connectedControllersManager.isConnected(controllerInfo)) {
              Log.w(
                  TAG,
                  "Controller "
                      + controllerInfo
                      + " has sent connection"
                      + " request multiple times");
            }
            connectedControllersManager.addController(
                callbackBinder,
                controllerInfo,
                connectionResult.availableSessionCommands,
                connectionResult.availablePlayerCommands);
            sequencedFutureManager =
                checkStateNotNull(
                    connectedControllersManager.getSequencedFutureManager(controllerInfo));
            // If connection is accepted, notify the current state to the controller.
            // It's needed because we cannot call synchronous calls between
            // session/controller.
            PlayerWrapper playerWrapper = sessionImpl.getPlayerWrapper();
            PlayerInfo playerInfo = playerWrapper.createPlayerInfoForBundling();
            ConnectionState state =
                new ConnectionState(
                    MediaLibraryInfo.VERSION_INT,
                    MediaSessionStub.this,
                    sessionImpl.getSessionActivity(),
                    connectionResult.availableSessionCommands,
                    connectionResult.availablePlayerCommands,
                    playerWrapper.getAvailableCommands(),
                    sessionImpl.getToken().getExtras(),
                    playerInfo);

            // Double check if session is still there, because release() can be called in
            // another thread.
            if (sessionImpl.isReleased()) {
              return;
            }
            try {
              caller.onConnected(
                  sequencedFutureManager.obtainNextSequenceNumber(), state.toBundle());
              connected = true;
            } catch (RemoteException e) {
              // Controller may be died prematurely.
            }
            sessionImpl.onPostConnectOnHandler(controllerInfo);
          } finally {
            if (!connected) {
              try {
                caller.onDisconnected(/* seq= */ 0);
              } catch (RemoteException e) {
                // Controller may be died prematurely.
                // Not an issue because we'll ignore it anyway.
              }
            }
          }
        });
  }

  public void release() {
    List<ControllerInfo> controllers = connectedControllersManager.getConnectedControllers();
    for (ControllerInfo controller : controllers) {
      ControllerCb cb = controller.getControllerCb();
      if (cb != null) {
        try {
          cb.onDisconnected(/* seq= */ 0);
        } catch (RemoteException e) {
          // Ignore. We're releasing.
        }
      }
    }
    for (ControllerInfo controller : pendingControllers) {
      ControllerCb cb = controller.getControllerCb();
      if (cb != null) {
        try {
          cb.onDisconnected(/* seq= */ 0);
        } catch (RemoteException e) {
          // Ignore. We're releasing.
        }
      }
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////////
  // AIDL methods for session overrides
  //////////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void connect(
      @Nullable IMediaController caller, int seq, @Nullable Bundle connectionRequestBundle)
      throws RuntimeException {
    if (caller == null || connectionRequestBundle == null) {
      return;
    }
    ConnectionRequest request;
    try {
      request = ConnectionRequest.CREATOR.fromBundle(connectionRequestBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for ConnectionRequest", e);
      return;
    }
    int uid = Binder.getCallingUid();
    int callingPid = Binder.getCallingPid();
    long token = Binder.clearCallingIdentity();
    // Binder.getCallingPid() can be 0 for an oneway call from the remote process.
    // If it's the case, use PID from the ConnectionRequest.
    int pid = (callingPid != 0) ? callingPid : request.pid;
    try {
      connect(caller, request.version, request.packageName, pid, uid, request.connectionHints);
    } finally {
      Binder.restoreCallingIdentity(token);
    }
  }

  @Override
  public void stop(@Nullable IMediaController caller, int seq) throws RemoteException {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_STOP,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().stop();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void release(@Nullable IMediaController caller, int seq) throws RemoteException {
    if (caller == null) {
      return;
    }
    long token = Binder.clearCallingIdentity();
    try {
      connectedControllersManager.removeController(caller.asBinder());
    } finally {
      Binder.restoreCallingIdentity(token);
    }
  }

  @Override
  public void onControllerResult(
      @Nullable IMediaController caller, int seq, @Nullable Bundle sessionResultBundle) {
    if (caller == null || sessionResultBundle == null) {
      return;
    }
    SessionResult result;
    try {
      result = SessionResult.CREATOR.fromBundle(sessionResultBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for SessionResult", e);
      return;
    }
    long token = Binder.clearCallingIdentity();
    try {
      @Nullable
      SequencedFutureManager manager =
          connectedControllersManager.getSequencedFutureManager(caller.asBinder());
      if (manager == null) {
        return;
      }
      manager.setFutureResult(seq, result);
    } finally {
      Binder.restoreCallingIdentity(token);
    }
  }

  @Override
  public void play(@Nullable IMediaController caller, int seq) throws RuntimeException {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_PLAY_PAUSE,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().play();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void pause(@Nullable IMediaController caller, int seq) throws RuntimeException {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_PLAY_PAUSE,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().pause();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void prepare(@Nullable IMediaController caller, int seq) throws RuntimeException {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_PREPARE,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().prepare();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekToDefaultPosition(IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_TO_DEFAULT_POSITION,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekToDefaultPosition();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekToDefaultPositionWithMediaItemIndex(
      IMediaController caller, int seq, int mediaItemIndex) throws RemoteException {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_TO_MEDIA_ITEM,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekToDefaultPosition(mediaItemIndex);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekTo(@Nullable IMediaController caller, int seq, long positionMs)
      throws RuntimeException {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekTo(positionMs);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekToWithMediaItemIndex(
      IMediaController caller, int seq, int mediaItemIndex, long positionMs)
      throws RemoteException {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_TO_MEDIA_ITEM,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekTo(mediaItemIndex, positionMs);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekBack(IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_BACK,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekBack();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekForward(IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_FORWARD,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekForward();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void onCustomCommand(
      @Nullable IMediaController caller,
      int seq,
      @Nullable Bundle commandBundle,
      @Nullable Bundle args) {
    if (caller == null || commandBundle == null || args == null) {
      return;
    }
    SessionCommand command;
    try {
      command = SessionCommand.CREATOR.fromBundle(commandBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for SessionCommand", e);
      return;
    }
    dispatchSessionTaskWithSessionCommand(
        caller,
        seq,
        command,
        (sessionImpl, controller) ->
            sessionImpl.onCustomCommandOnHandler(controller, command, args),
        MediaSessionStub::sendSessionResultWhenReady);
  }

  @Override
  public void setRatingWithMediaId(
      @Nullable IMediaController caller, int seq, String mediaId, @Nullable Bundle ratingBundle) {
    if (caller == null || ratingBundle == null) {
      return;
    }
    if (TextUtils.isEmpty(mediaId)) {
      Log.w(TAG, "setRatingWithMediaId(): Ignoring empty mediaId");
      return;
    }
    Rating rating;
    try {
      rating = Rating.CREATOR.fromBundle(ratingBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for Rating", e);
      return;
    }
    dispatchSessionTaskWithSessionCommand(
        caller,
        seq,
        COMMAND_CODE_SESSION_SET_RATING,
        (sessionImpl, controller) -> sessionImpl.onSetRatingOnHandler(controller, mediaId, rating),
        MediaSessionStub::sendSessionResultWhenReady);
  }

  @Override
  public void setRating(@Nullable IMediaController caller, int seq, @Nullable Bundle ratingBundle) {
    if (caller == null || ratingBundle == null) {
      return;
    }
    Rating rating;
    try {
      rating = Rating.CREATOR.fromBundle(ratingBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for Rating", e);
      return;
    }
    dispatchSessionTaskWithSessionCommand(
        caller,
        seq,
        COMMAND_CODE_SESSION_SET_RATING,
        (sessionImpl, controller) -> sessionImpl.onSetRatingOnHandler(controller, rating),
        MediaSessionStub::sendSessionResultWhenReady);
  }

  @Override
  public void setPlaybackSpeed(@Nullable IMediaController caller, int seq, float speed) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_SPEED_AND_PITCH,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setPlaybackSpeed(speed);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setPlaybackParameters(
      @Nullable IMediaController caller, int seq, Bundle playbackParametersBundle) {
    if (caller == null || playbackParametersBundle == null) {
      return;
    }
    PlaybackParameters playbackParameters =
        PlaybackParameters.CREATOR.fromBundle(playbackParametersBundle);
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_SPEED_AND_PITCH,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setPlaybackParameters(playbackParameters);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setMediaItem(
      @Nullable IMediaController caller, int seq, @Nullable Bundle mediaItemBundle) {
    if (caller == null || mediaItemBundle == null) {
      return;
    }
    MediaItem mediaItem;
    try {
      mediaItem = MediaItem.CREATOR.fromBundle(mediaItemBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) ->
            sessionImpl.onAddMediaItemsOnHandler(controller, ImmutableList.of(mediaItem)),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl, controller, sequence, future, Player::setMediaItems));
  }

  @Override
  public void setMediaItemWithStartPosition(
      @Nullable IMediaController caller,
      int seq,
      @Nullable Bundle mediaItemBundle,
      long startPositionMs) {
    if (caller == null || mediaItemBundle == null) {
      return;
    }
    MediaItem mediaItem;
    try {
      mediaItem = MediaItem.CREATOR.fromBundle(mediaItemBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) ->
            sessionImpl.onAddMediaItemsOnHandler(controller, ImmutableList.of(mediaItem)),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl,
                controller,
                sequence,
                future,
                (player, mediaItems) ->
                    player.setMediaItems(mediaItems, /* startIndex= */ 0, startPositionMs)));
  }

  @Override
  public void setMediaItemWithResetPosition(
      @Nullable IMediaController caller,
      int seq,
      @Nullable Bundle mediaItemBundle,
      boolean resetPosition) {
    if (caller == null || mediaItemBundle == null) {
      return;
    }
    MediaItem mediaItem;
    try {
      mediaItem = MediaItem.CREATOR.fromBundle(mediaItemBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) ->
            sessionImpl.onAddMediaItemsOnHandler(controller, ImmutableList.of(mediaItem)),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl,
                controller,
                sequence,
                future,
                (player, mediaItems) -> player.setMediaItems(mediaItems, resetPosition)));
  }

  @Override
  public void setMediaItems(
      @Nullable IMediaController caller, int seq, @Nullable IBinder mediaItemsRetriever) {
    if (caller == null || mediaItemsRetriever == null) {
      return;
    }
    List<MediaItem> mediaItemList;
    try {
      mediaItemList =
          BundleableUtil.fromBundleList(
              MediaItem.CREATOR, BundleListRetriever.getList(mediaItemsRetriever));
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }

    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) ->
            sessionImpl.onAddMediaItemsOnHandler(controller, mediaItemList),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl, controller, sequence, future, Player::setMediaItems));
  }

  @Override
  public void setMediaItemsWithResetPosition(
      @Nullable IMediaController caller,
      int seq,
      @Nullable IBinder mediaItemsRetriever,
      boolean resetPosition) {
    if (caller == null || mediaItemsRetriever == null) {
      return;
    }
    List<MediaItem> mediaItemList;
    try {
      mediaItemList =
          BundleableUtil.fromBundleList(
              MediaItem.CREATOR, BundleListRetriever.getList(mediaItemsRetriever));
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) ->
            sessionImpl.onAddMediaItemsOnHandler(controller, mediaItemList),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl,
                controller,
                sequence,
                future,
                (player, mediaItems) -> player.setMediaItems(mediaItems, resetPosition)));
  }

  @Override
  public void setMediaItemsWithStartIndex(
      @Nullable IMediaController caller,
      int seq,
      @Nullable IBinder mediaItemsRetriever,
      int startIndex,
      long startPositionMs) {
    if (caller == null || mediaItemsRetriever == null) {
      return;
    }
    List<MediaItem> mediaItemList;
    try {
      mediaItemList =
          BundleableUtil.fromBundleList(
              MediaItem.CREATOR, BundleListRetriever.getList(mediaItemsRetriever));
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) ->
            sessionImpl.onAddMediaItemsOnHandler(controller, mediaItemList),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl,
                controller,
                sequence,
                future,
                (player, mediaItems) ->
                    player.setMediaItems(mediaItems, startIndex, startPositionMs)));
  }

  @Override
  public void setMediaUri(
      @Nullable IMediaController caller, int seq, @Nullable Uri uri, @Nullable Bundle extras) {
    if (caller == null || uri == null || extras == null) {
      return;
    }
    dispatchSessionTaskWithSessionCommand(
        caller,
        seq,
        COMMAND_CODE_SESSION_SET_MEDIA_URI,
        (sessionImpl, controller) ->
            new SessionResult(sessionImpl.onSetMediaUriOnHandler(controller, uri, extras)),
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setPlaylistMetadata(
      @Nullable IMediaController caller, int seq, @Nullable Bundle playlistMetadataBundle) {
    if (caller == null || playlistMetadataBundle == null) {
      return;
    }
    MediaMetadata playlistMetadata;
    try {
      playlistMetadata = MediaMetadata.CREATOR.fromBundle(playlistMetadataBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaMetadata", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_MEDIA_ITEMS_METADATA,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setPlaylistMetadata(playlistMetadata);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void addMediaItem(@Nullable IMediaController caller, int seq, Bundle mediaItemBundle) {
    if (caller == null || mediaItemBundle == null) {
      return;
    }
    MediaItem mediaItem;
    try {
      mediaItem = MediaItem.CREATOR.fromBundle(mediaItemBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) ->
            sessionImpl.onAddMediaItemsOnHandler(controller, ImmutableList.of(mediaItem)),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl, controller, sequence, future, Player::addMediaItems));
  }

  @Override
  public void addMediaItemWithIndex(
      @Nullable IMediaController caller, int seq, int index, Bundle mediaItemBundle) {
    if (caller == null || mediaItemBundle == null) {
      return;
    }
    MediaItem mediaItem;
    try {
      mediaItem = MediaItem.CREATOR.fromBundle(mediaItemBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) ->
            sessionImpl.onAddMediaItemsOnHandler(controller, ImmutableList.of(mediaItem)),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl,
                controller,
                sequence,
                future,
                (player, mediaItems) -> player.addMediaItems(index, mediaItems)));
  }

  @Override
  public void addMediaItems(
      @Nullable IMediaController caller, int seq, @Nullable IBinder mediaItemsRetriever) {
    if (caller == null || mediaItemsRetriever == null) {
      return;
    }
    List<MediaItem> mediaItems;
    try {
      mediaItems =
          BundleableUtil.fromBundleList(
              MediaItem.CREATOR, BundleListRetriever.getList(mediaItemsRetriever));
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) -> sessionImpl.onAddMediaItemsOnHandler(controller, mediaItems),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl, controller, sequence, future, Player::addMediaItems));
  }

  @Override
  public void addMediaItemsWithIndex(
      @Nullable IMediaController caller,
      int seq,
      int index,
      @Nullable IBinder mediaItemsRetriever) {
    if (caller == null || mediaItemsRetriever == null) {
      return;
    }
    List<MediaItem> mediaItems;
    try {
      mediaItems =
          BundleableUtil.fromBundleList(
              MediaItem.CREATOR, BundleListRetriever.getList(mediaItemsRetriever));
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for MediaItem", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) -> sessionImpl.onAddMediaItemsOnHandler(controller, mediaItems),
        (sessionImpl, controller, sequence, future) ->
            handleMediaItemsWhenReady(
                sessionImpl,
                controller,
                sequence,
                future,
                (player, items) -> player.addMediaItems(index, items)));
  }

  @Override
  public void removeMediaItem(@Nullable IMediaController caller, int seq, int index) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().removeMediaItem(index);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void removeMediaItems(
      @Nullable IMediaController caller, int seq, int fromIndex, int toIndex) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().removeMediaItems(fromIndex, toIndex);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void clearMediaItems(@Nullable IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().clearMediaItems();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void moveMediaItem(
      @Nullable IMediaController caller, int seq, int currentIndex, int newIndex) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().moveMediaItem(currentIndex, newIndex);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void moveMediaItems(
      @Nullable IMediaController caller, int seq, int fromIndex, int toIndex, int newIndex) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_CHANGE_MEDIA_ITEMS,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().moveMediaItems(fromIndex, toIndex, newIndex);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekToPreviousMediaItem(@Nullable IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekToPreviousMediaItem();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekToNextMediaItem(@Nullable IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekToNextMediaItem();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekToPrevious(@Nullable IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_TO_PREVIOUS,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekToPrevious();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void seekToNext(@Nullable IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SEEK_TO_NEXT,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().seekToNext();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setRepeatMode(
      @Nullable IMediaController caller, int seq, @Player.RepeatMode int repeatMode) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_REPEAT_MODE,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setRepeatMode(repeatMode);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setShuffleModeEnabled(
      @Nullable IMediaController caller, int seq, boolean shuffleModeEnabled) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_SHUFFLE_MODE,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setShuffleModeEnabled(shuffleModeEnabled);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setVideoSurface(
      @Nullable IMediaController caller, int seq, @Nullable Surface surface) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_VIDEO_SURFACE,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setVideoSurface(surface);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setVolume(@Nullable IMediaController caller, int seq, float volume) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_VOLUME,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setVolume(volume);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setDeviceVolume(@Nullable IMediaController caller, int seq, int volume) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_DEVICE_VOLUME,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setDeviceVolume(volume);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void increaseDeviceVolume(@Nullable IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_ADJUST_DEVICE_VOLUME,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().increaseDeviceVolume();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void decreaseDeviceVolume(@Nullable IMediaController caller, int seq) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_ADJUST_DEVICE_VOLUME,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().decreaseDeviceVolume();
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setDeviceMuted(@Nullable IMediaController caller, int seq, boolean muted) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_DEVICE_VOLUME,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setDeviceMuted(muted);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void setPlayWhenReady(@Nullable IMediaController caller, int seq, boolean playWhenReady) {
    if (caller == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_PLAY_PAUSE,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setPlayWhenReady(playWhenReady);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  @Override
  public void flushCommandQueue(@Nullable IMediaController caller) {
    if (caller == null) {
      return;
    }
    long token = Binder.clearCallingIdentity();
    try {
      @Nullable MediaSessionImpl sessionImpl = this.sessionImpl.get();
      if (sessionImpl == null || sessionImpl.isReleased()) {
        return;
      }
      ControllerInfo controllerInfo = connectedControllersManager.getController(caller.asBinder());
      if (controllerInfo != null) {
        Deque<Runnable> queue = connectedControllersManager.getAndClearCommandQueue(controllerInfo);
        postOrRun(
            sessionImpl.getApplicationHandler(),
            () -> {
              while (!queue.isEmpty()) {
                Runnable runnable = queue.poll();
                if (runnable != null) {
                  runnable.run();
                }
              }
            });
      }
    } finally {
      Binder.restoreCallingIdentity(token);
    }
  }

  @Override
  public void setTrackSelectionParameters(
      @Nullable IMediaController caller, int seq, Bundle trackSelectionParametersBundle)
      throws RemoteException {
    if (caller == null) {
      return;
    }
    TrackSelectionParameters trackSelectionParameters;
    try {
      trackSelectionParameters =
          TrackSelectionParameters.fromBundle(trackSelectionParametersBundle);
    } catch (RuntimeException e) {
      Log.w(TAG, "Ignoring malformed Bundle for TrackSelectionParameters", e);
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        caller,
        seq,
        COMMAND_SET_TRACK_SELECTION_PARAMETERS,
        (sessionImpl, controller) -> {
          sessionImpl.getPlayerWrapper().setTrackSelectionParameters(trackSelectionParameters);
          return SessionResult.RESULT_SUCCESS;
        },
        MediaSessionStub::sendSessionResult);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////
  // AIDL methods for LibrarySession overrides
  //////////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public void getLibraryRoot(
      @Nullable IMediaController caller, int seq, @Nullable Bundle libraryParamsBundle)
      throws RuntimeException {
    if (caller == null) {
      return;
    }
    @Nullable
    LibraryParams libraryParams =
        libraryParamsBundle == null ? null : LibraryParams.CREATOR.fromBundle(libraryParamsBundle);
    dispatchSessionTaskWithLibrarySessionCommand(
        caller,
        seq,
        COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
        (librarySessionImpl, controller) ->
            librarySessionImpl.onGetLibraryRootOnHandler(controller, libraryParams),
        MediaSessionStub::sendLibraryResultWhenReady);
  }

  @Override
  public void getItem(@Nullable IMediaController caller, int seq, @Nullable String mediaId)
      throws RuntimeException {
    if (caller == null) {
      return;
    }
    if (TextUtils.isEmpty(mediaId)) {
      Log.w(TAG, "getItem(): Ignoring empty mediaId");
      return;
    }
    dispatchSessionTaskWithLibrarySessionCommand(
        caller,
        seq,
        COMMAND_CODE_LIBRARY_GET_ITEM,
        (librarySessionImpl, controller) ->
            librarySessionImpl.onGetItemOnHandler(controller, mediaId),
        MediaSessionStub::sendLibraryResultWhenReady);
  }

  @Override
  public void getChildren(
      @Nullable IMediaController caller,
      int seq,
      String parentId,
      int page,
      int pageSize,
      @Nullable Bundle libraryParamsBundle)
      throws RuntimeException {
    if (caller == null) {
      return;
    }
    if (TextUtils.isEmpty(parentId)) {
      Log.w(TAG, "getChildren(): Ignoring empty parentId");
      return;
    }
    if (page < 0) {
      Log.w(TAG, "getChildren(): Ignoring negative page");
      return;
    }
    if (pageSize < 1) {
      Log.w(TAG, "getChildren(): Ignoring pageSize less than 1");
      return;
    }
    @Nullable
    LibraryParams libraryParams =
        libraryParamsBundle == null ? null : LibraryParams.CREATOR.fromBundle(libraryParamsBundle);
    dispatchSessionTaskWithLibrarySessionCommand(
        caller,
        seq,
        COMMAND_CODE_LIBRARY_GET_CHILDREN,
        (librarySessionImpl, controller) ->
            librarySessionImpl.onGetChildrenOnHandler(
                controller, parentId, page, pageSize, libraryParams),
        MediaSessionStub::sendLibraryResultWhenReady);
  }

  @Override
  public void search(
      @Nullable IMediaController caller,
      int seq,
      String query,
      @Nullable Bundle libraryParamsBundle) {
    if (caller == null) {
      return;
    }
    if (TextUtils.isEmpty(query)) {
      Log.w(TAG, "search(): Ignoring empty query");
      return;
    }
    @Nullable
    LibraryParams libraryParams =
        libraryParamsBundle == null ? null : LibraryParams.CREATOR.fromBundle(libraryParamsBundle);
    dispatchSessionTaskWithLibrarySessionCommand(
        caller,
        seq,
        COMMAND_CODE_LIBRARY_SEARCH,
        (librarySessionImpl, controller) ->
            librarySessionImpl.onSearchOnHandler(controller, query, libraryParams),
        MediaSessionStub::sendLibraryResultWhenReady);
  }

  @Override
  public void getSearchResult(
      @Nullable IMediaController caller,
      int seq,
      String query,
      int page,
      int pageSize,
      @Nullable Bundle libraryParamsBundle) {
    if (caller == null) {
      return;
    }
    if (TextUtils.isEmpty(query)) {
      Log.w(TAG, "getSearchResult(): Ignoring empty query");
      return;
    }
    if (page < 0) {
      Log.w(TAG, "getSearchResult(): Ignoring negative page");
      return;
    }
    if (pageSize < 1) {
      Log.w(TAG, "getSearchResult(): Ignoring pageSize less than 1");
      return;
    }
    @Nullable
    LibraryParams libraryParams =
        libraryParamsBundle == null ? null : LibraryParams.CREATOR.fromBundle(libraryParamsBundle);
    dispatchSessionTaskWithLibrarySessionCommand(
        caller,
        seq,
        COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT,
        (librarySessionImpl, controller) ->
            librarySessionImpl.onGetSearchResultOnHandler(
                controller, query, page, pageSize, libraryParams),
        MediaSessionStub::sendLibraryResultWhenReady);
  }

  @Override
  public void subscribe(
      @Nullable IMediaController caller,
      int seq,
      String parentId,
      @Nullable Bundle libraryParamsBundle) {
    if (caller == null) {
      return;
    }
    if (TextUtils.isEmpty(parentId)) {
      Log.w(TAG, "subscribe(): Ignoring empty parentId");
      return;
    }
    @Nullable
    LibraryParams libraryParams =
        libraryParamsBundle == null ? null : LibraryParams.CREATOR.fromBundle(libraryParamsBundle);
    dispatchSessionTaskWithLibrarySessionCommand(
        caller,
        seq,
        COMMAND_CODE_LIBRARY_SUBSCRIBE,
        (librarySessionImpl, controller) ->
            librarySessionImpl.onSubscribeOnHandler(controller, parentId, libraryParams),
        MediaSessionStub::sendLibraryResultWhenReady);
  }

  @Override
  public void unsubscribe(@Nullable IMediaController caller, int seq, String parentId) {
    if (caller == null) {
      return;
    }
    if (TextUtils.isEmpty(parentId)) {
      Log.w(TAG, "unsubscribe(): Ignoring empty parentId");
      return;
    }
    dispatchSessionTaskWithLibrarySessionCommand(
        caller,
        seq,
        COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
        (librarySessionImpl, controller) ->
            librarySessionImpl.onUnsubscribeOnHandler(controller, parentId),
        MediaSessionStub::sendLibraryResultWhenReady);
  }

  /** Common interface for code snippets to handle all incoming commands from the controller. */
  private interface SessionTask<T, K extends MediaSessionImpl> {
    T run(K sessionImpl, ControllerInfo controller);
  }

  private interface PostSessionTask<T, K extends MediaSessionImpl> {
    void run(K sessionImpl, ControllerInfo controller, int seq, T result);
  }

  private interface MediaItemPlayerTask {
    void run(PlayerWrapper player, List<MediaItem> mediaItems);
  }

  /* package */ static final class Controller2Cb implements ControllerCb {

    private final IMediaController iController;

    public Controller2Cb(IMediaController callback) {
      iController = callback;
    }

    public IBinder getCallbackBinder() {
      return iController.asBinder();
    }

    @Override
    public void onSessionResult(int seq, SessionResult result) throws RemoteException {
      iController.onSessionResult(seq, result.toBundle());
    }

    @Override
    public void onLibraryResult(int seq, LibraryResult<?> result) throws RemoteException {
      iController.onLibraryResult(seq, result.toBundle());
    }

    @Override
    public void onPlayerInfoChanged(
        int seq,
        PlayerInfo playerInfo,
        boolean excludeMediaItems,
        boolean excludeMediaItemsMetadata,
        boolean excludeCues,
        boolean excludeTimeline)
        throws RemoteException {
      iController.onPlayerInfoChanged(
          seq,
          playerInfo.toBundle(
              excludeMediaItems, excludeMediaItemsMetadata, excludeCues, excludeTimeline),
          /* isTimelineExcluded= */ excludeTimeline);
    }

    @Override
    public void setCustomLayout(int seq, List<CommandButton> layout) throws RemoteException {
      iController.onSetCustomLayout(seq, BundleableUtil.toBundleList(layout));
    }

    @Override
    public void onAvailableCommandsChangedFromSession(
        int seq, SessionCommands sessionCommands, Player.Commands playerCommands)
        throws RemoteException {
      iController.onAvailableCommandsChangedFromSession(
          seq, sessionCommands.toBundle(), playerCommands.toBundle());
    }

    @Override
    public void onAvailableCommandsChangedFromPlayer(int seq, Player.Commands availableCommands)
        throws RemoteException {
      iController.onAvailableCommandsChangedFromPlayer(seq, availableCommands.toBundle());
    }

    @Override
    public void sendCustomCommand(int seq, SessionCommand command, Bundle args)
        throws RemoteException {
      iController.onCustomCommand(seq, command.toBundle(), args);
    }

    @SuppressWarnings("nullness:argument") // params can be null.
    @Override
    public void onChildrenChanged(
        int seq, String parentId, int itemCount, @Nullable LibraryParams params)
        throws RemoteException {
      iController.onChildrenChanged(
          seq, parentId, itemCount, params == null ? null : params.toBundle());
    }

    @SuppressWarnings("nullness:argument") // params can be null.
    @Override
    public void onSearchResultChanged(
        int seq, String query, int itemCount, @Nullable LibraryParams params)
        throws RemoteException {
      iController.onSearchResultChanged(
          seq, query, itemCount, params == null ? null : params.toBundle());
    }

    @Override
    public void onDisconnected(int seq) throws RemoteException {
      iController.onDisconnected(seq);
    }

    @Override
    public void onPeriodicSessionPositionInfoChanged(
        int seq, SessionPositionInfo sessionPositionInfo) throws RemoteException {
      iController.onPeriodicSessionPositionInfoChanged(seq, sessionPositionInfo.toBundle());
    }

    @Override
    public void onRenderedFirstFrame(int seq) throws RemoteException {
      iController.onRenderedFirstFrame(seq);
    }

    @Override
    public void onSessionExtrasChanged(int seq, Bundle sessionExtras) throws RemoteException {
      iController.onExtrasChanged(seq, sessionExtras);
    }

    @Override
    public int hashCode() {
      return ObjectsCompat.hash(getCallbackBinder());
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || obj.getClass() != Controller2Cb.class) {
        return false;
      }
      Controller2Cb other = (Controller2Cb) obj;
      return Util.areEqual(getCallbackBinder(), other.getCallbackBinder());
    }
  }
}
