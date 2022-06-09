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

import static androidx.media3.common.Player.COMMAND_CHANGE_MEDIA_ITEMS;
import static androidx.media3.common.Player.COMMAND_PLAY_PAUSE;
import static androidx.media3.common.Player.COMMAND_PREPARE;
import static androidx.media3.common.Player.COMMAND_SEEK_BACK;
import static androidx.media3.common.Player.COMMAND_SEEK_FORWARD;
import static androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT;
import static androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS;
import static androidx.media3.common.Player.COMMAND_SET_MEDIA_ITEM;
import static androidx.media3.common.Player.COMMAND_SET_REPEAT_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SHUFFLE_MODE;
import static androidx.media3.common.Player.COMMAND_SET_SPEED_AND_PITCH;
import static androidx.media3.common.Player.COMMAND_STOP;
import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.common.Player.STATE_IDLE;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.postOrRun;
import static androidx.media3.session.MediaUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES;
import static androidx.media3.session.SessionCommand.COMMAND_CODE_CUSTOM;
import static androidx.media3.session.SessionResult.RESULT_ERROR_UNKNOWN;
import static androidx.media3.session.SessionResult.RESULT_INFO_SKIPPED;
import static androidx.media3.session.SessionResult.RESULT_SUCCESS;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.media.MediaSessionManager;
import androidx.media.MediaSessionManager.RemoteUserInfo;
import androidx.media.VolumeProviderCompat;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Player.RepeatMode;
import androidx.media3.common.Rating;
import androidx.media3.common.Timeline;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerCb;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.session.SessionCommand.CommandCode;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.checkerframework.checker.initialization.qual.Initialized;

// Getting the commands from MediaControllerCompat'
/* package */ class MediaSessionLegacyStub extends MediaSessionCompat.Callback {

  private static final String TAG = "MediaSessionLegacyStub";

  private static final String DEFAULT_MEDIA_SESSION_TAG_PREFIX = "androidx.media3.session.id";
  private static final String DEFAULT_MEDIA_SESSION_TAG_DELIM = ".";

  // Used to call onDisconnected() after the timeout.
  private static final int DEFAULT_CONNECTION_TIMEOUT_MS = 300_000; // 5 min.

  private final ConnectedControllersManager<RemoteUserInfo> connectedControllersManager;

  private final MediaSessionImpl sessionImpl;
  private final MediaSessionManager sessionManager;
  private final ControllerCb controllerLegacyCbForBroadcast;
  private final ConnectionTimeoutHandler connectionTimeoutHandler;
  private final MediaPlayPauseKeyHandler mediaPlayPauseKeyHandler;
  private final MediaSessionCompat sessionCompat;
  @Nullable private VolumeProviderCompat volumeProviderCompat;

  private volatile long connectionTimeoutMs;

  public MediaSessionLegacyStub(
      MediaSessionImpl session,
      ComponentName mbrComponent,
      PendingIntent mediaButtonIntent,
      Handler handler) {
    sessionImpl = session;
    Context context = sessionImpl.getContext();
    sessionManager = MediaSessionManager.getSessionManager(context);
    controllerLegacyCbForBroadcast = new ControllerLegacyCbForBroadcast();
    connectionTimeoutHandler =
        new ConnectionTimeoutHandler(session.getApplicationHandler().getLooper());
    mediaPlayPauseKeyHandler =
        new MediaPlayPauseKeyHandler(session.getApplicationHandler().getLooper());
    connectedControllersManager = new ConnectedControllersManager<>(session);
    connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;

    String sessionCompatId =
        TextUtils.join(
            DEFAULT_MEDIA_SESSION_TAG_DELIM,
            new String[] {DEFAULT_MEDIA_SESSION_TAG_PREFIX, session.getId()});
    sessionCompat =
        new MediaSessionCompat(
            context,
            sessionCompatId,
            mbrComponent,
            mediaButtonIntent,
            session.getToken().getExtras());

    @Nullable PendingIntent sessionActivity = session.getSessionActivity();
    if (sessionActivity != null) {
      sessionCompat.setSessionActivity(sessionActivity);
    }

    sessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

    @SuppressWarnings("nullness:assignment")
    @Initialized
    MediaSessionLegacyStub thisRef = this;
    sessionCompat.setCallback(thisRef, handler);
  }

  /** Starts to receive commands. */
  public void start() {
    sessionCompat.setActive(true);
  }

  public void release() {
    sessionCompat.release();
  }

  public MediaSessionCompat getSessionCompat() {
    return sessionCompat;
  }

  @Override
  public void onCommand(String commandName, @Nullable Bundle args, @Nullable ResultReceiver cb) {
    checkStateNotNull(commandName);
    if (TextUtils.equals(MediaConstants.SESSION_COMMAND_REQUEST_SESSION3_TOKEN, commandName)
        && cb != null) {
      cb.send(RESULT_SUCCESS, sessionImpl.getToken().toBundle());
      return;
    }
    SessionCommand command = new SessionCommand(commandName, /* extras= */ Bundle.EMPTY);
    dispatchSessionTaskWithSessionCommand(
        command,
        controller -> {
          ListenableFuture<SessionResult> future =
              sessionImpl.onCustomCommandOnHandler(
                  controller, command, args == null ? Bundle.EMPTY : args);
          if (cb != null) {
            sendCustomCommandResultWhenReady(cb, future);
          } else {
            ignoreFuture(future);
          }
        });
  }

  @Override
  public void onCustomAction(String action, @Nullable Bundle args) {
    SessionCommand command = new SessionCommand(action, /* extras= */ Bundle.EMPTY);
    dispatchSessionTaskWithSessionCommand(
        command,
        controller ->
            ignoreFuture(
                sessionImpl.onCustomCommandOnHandler(
                    controller, command, args != null ? args : Bundle.EMPTY)));
  }

  @Override
  public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
    @Nullable KeyEvent keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
    if (keyEvent == null || keyEvent.getAction() != KeyEvent.ACTION_DOWN) {
      return false;
    }
    RemoteUserInfo remoteUserInfo = sessionCompat.getCurrentControllerInfo();
    int keyCode = keyEvent.getKeyCode();
    switch (keyCode) {
      case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
      case KeyEvent.KEYCODE_HEADSETHOOK:
        if (keyEvent.getRepeatCount() == 0) {
          if (mediaPlayPauseKeyHandler.hasPendingMediaPlayPauseKey()) {
            mediaPlayPauseKeyHandler.clearPendingMediaPlayPauseKey();
            onSkipToNext();
          } else {
            mediaPlayPauseKeyHandler.addPendingMediaPlayPauseKey(remoteUserInfo);
          }
        } else {
          // Consider long-press as a single tap. Handle immediately.
          handleMediaPlayPauseOnHandler(remoteUserInfo);
        }
        return true;
      default:
        // If another key is pressed within double tap timeout, consider the pending
        // pending play/pause as a single tap to handle media keys in order.
        if (mediaPlayPauseKeyHandler.hasPendingMediaPlayPauseKey()) {
          handleMediaPlayPauseOnHandler(remoteUserInfo);
        }
        break;
    }
    return false;
  }

  private void handleMediaPlayPauseOnHandler(RemoteUserInfo remoteUserInfo) {
    mediaPlayPauseKeyHandler.clearPendingMediaPlayPauseKey();
    if (sessionImpl.getPlayerWrapper().getPlayWhenReady()) {
      dispatchSessionTaskWithPlayerCommand(
          COMMAND_PLAY_PAUSE,
          (controller) -> sessionImpl.getPlayerWrapper().pause(),
          remoteUserInfo);
    } else {
      dispatchSessionTaskWithPlayerCommand(
          COMMAND_PLAY_PAUSE,
          (controller) -> {
            PlayerWrapper playerWrapper = sessionImpl.getPlayerWrapper();
            @Player.State int playbackState = playerWrapper.getPlaybackState();
            if (playbackState == STATE_IDLE) {
              playerWrapper.prepare();
            } else if (playbackState == STATE_ENDED) {
              playerWrapper.seekTo(
                  playerWrapper.getCurrentMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
            }
            playerWrapper.play();
          },
          remoteUserInfo);
    }
  }

  @Override
  public void onPrepare() {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_PREPARE,
        controller -> sessionImpl.getPlayerWrapper().prepare(),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onPrepareFromMediaId(String mediaId, @Nullable Bundle extras) {
    handleMediaRequest(
        createMediaItemForMediaRequest(
            mediaId, /* mediaUri= */ null, /* searchQuery= */ null, extras),
        /* play= */ false);
  }

  @Override
  public void onPrepareFromSearch(String query, @Nullable Bundle extras) {
    handleMediaRequest(
        createMediaItemForMediaRequest(/* mediaId= */ null, /* mediaUri= */ null, query, extras),
        /* play= */ false);
  }

  @Override
  public void onPrepareFromUri(Uri mediaUri, @Nullable Bundle extras) {
    handleMediaRequest(
        createMediaItemForMediaRequest(
            /* mediaId= */ null, mediaUri, /* searchQuery= */ null, extras),
        /* play= */ false);
  }

  @Override
  public void onPlay() {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_PLAY_PAUSE,
        controller -> {
          PlayerWrapper playerWrapper = sessionImpl.getPlayerWrapper();
          @Player.State int playbackState = playerWrapper.getPlaybackState();
          if (playbackState == Player.STATE_IDLE) {
            playerWrapper.prepare();
          } else if (playbackState == Player.STATE_ENDED) {
            playerWrapper.seekTo(
                playerWrapper.getCurrentMediaItemIndex(), /* positionMs= */ C.TIME_UNSET);
          }
          playerWrapper.play();
        },
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onPlayFromMediaId(String mediaId, @Nullable Bundle extras) {
    handleMediaRequest(
        createMediaItemForMediaRequest(
            mediaId, /* mediaUri= */ null, /* searchQuery= */ null, extras),
        /* play= */ true);
  }

  @Override
  public void onPlayFromSearch(String query, @Nullable Bundle extras) {
    handleMediaRequest(
        createMediaItemForMediaRequest(/* mediaId= */ null, /* mediaUri= */ null, query, extras),
        /* play= */ true);
  }

  @Override
  public void onPlayFromUri(Uri mediaUri, @Nullable Bundle extras) {
    handleMediaRequest(
        createMediaItemForMediaRequest(
            /* mediaId= */ null, mediaUri, /* searchQuery= */ null, extras),
        /* play= */ true);
  }

  @Override
  public void onPause() {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_PLAY_PAUSE,
        controller -> sessionImpl.getPlayerWrapper().pause(),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onStop() {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_STOP,
        controller -> sessionImpl.getPlayerWrapper().stop(),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onSeekTo(long pos) {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
        controller -> sessionImpl.getPlayerWrapper().seekTo(pos),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onSkipToNext() {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_NEXT,
        controller -> sessionImpl.getPlayerWrapper().seekToNext(),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onSkipToPrevious() {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_PREVIOUS,
        controller -> sessionImpl.getPlayerWrapper().seekToPrevious(),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onSetPlaybackSpeed(float speed) {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SET_SPEED_AND_PITCH,
        controller -> sessionImpl.getPlayerWrapper().setPlaybackSpeed(speed),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onSkipToQueueItem(long queueId) {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SEEK_TO_MEDIA_ITEM,
        controller -> {
          PlayerWrapper playerWrapper = sessionImpl.getPlayerWrapper();
          // Use queueId as an index as we've published {@link QueueItem} as so.
          // see: {@link MediaUtils#convertToQueueItemList}.
          playerWrapper.seekToDefaultPosition((int) queueId);
        },
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onFastForward() {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SEEK_FORWARD,
        controller -> sessionImpl.getPlayerWrapper().seekForward(),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onRewind() {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SEEK_BACK,
        controller -> sessionImpl.getPlayerWrapper().seekBack(),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onSetRating(RatingCompat ratingCompat) {
    onSetRating(ratingCompat, null);
  }

  @Override
  public void onSetRating(RatingCompat ratingCompat, @Nullable Bundle unusedExtras) {
    @Nullable Rating rating = MediaUtils.convertToRating(ratingCompat);
    if (rating == null) {
      Log.w(TAG, "Ignoring invalid RatingCompat " + ratingCompat);
      return;
    }
    dispatchSessionTaskWithSessionCommand(
        SessionCommand.COMMAND_CODE_SESSION_SET_RATING,
        controller -> {
          @Nullable MediaItem currentItem = sessionImpl.getPlayerWrapper().getCurrentMediaItem();
          if (currentItem == null) {
            return;
          }
          // MediaControllerCompat#setRating doesn't return a value.
          ignoreFuture(sessionImpl.onSetRatingOnHandler(controller, currentItem.mediaId, rating));
        });
  }

  @Override
  public void onSetCaptioningEnabled(boolean enabled) {
    // no-op
  }

  @Override
  public void onSetRepeatMode(@PlaybackStateCompat.RepeatMode int playbackStateCompatRepeatMode) {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SET_REPEAT_MODE,
        controller ->
            sessionImpl
                .getPlayerWrapper()
                .setRepeatMode(MediaUtils.convertToRepeatMode(playbackStateCompatRepeatMode)),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onSetShuffleMode(@PlaybackStateCompat.ShuffleMode int shuffleMode) {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SET_SHUFFLE_MODE,
        controller ->
            sessionImpl
                .getPlayerWrapper()
                .setShuffleModeEnabled(MediaUtils.convertToShuffleModeEnabled(shuffleMode)),
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onAddQueueItem(@Nullable MediaDescriptionCompat description) {
    handleOnAddQueueItem(description, /* index= */ C.INDEX_UNSET);
  }

  @Override
  public void onAddQueueItem(@Nullable MediaDescriptionCompat description, int index) {
    handleOnAddQueueItem(description, index);
  }

  @Override
  public void onRemoveQueueItem(@Nullable MediaDescriptionCompat description) {
    if (description == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        controller -> {
          @Nullable String mediaId = description.getMediaId();
          if (TextUtils.isEmpty(mediaId)) {
            Log.w(TAG, "onRemoveQueueItem(): Media ID shouldn't be null");
            return;
          }
          Timeline timeline = sessionImpl.getPlayerWrapper().getCurrentTimeline();
          Timeline.Window window = new Timeline.Window();
          for (int i = 0; i < timeline.getWindowCount(); i++) {
            MediaItem mediaItem = timeline.getWindow(i, window).mediaItem;
            if (TextUtils.equals(mediaItem.mediaId, mediaId)) {
              sessionImpl.getPlayerWrapper().removeMediaItem(i);
              return;
            }
          }
        },
        sessionCompat.getCurrentControllerInfo());
  }

  @Override
  public void onRemoveQueueItemAt(int index) {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        controller -> {
          if (index < 0) {
            Log.w(TAG, "onRemoveQueueItem(): index shouldn't be negative");
            return;
          }
          sessionImpl.getPlayerWrapper().removeMediaItem(index);
        },
        sessionCompat.getCurrentControllerInfo());
  }

  public ControllerCb getControllerLegacyCbForBroadcast() {
    return controllerLegacyCbForBroadcast;
  }

  public ConnectedControllersManager<RemoteUserInfo> getConnectedControllersManager() {
    return connectedControllersManager;
  }

  private void dispatchSessionTaskWithPlayerCommand(
      @Player.Command int command, SessionTask task, @Nullable RemoteUserInfo remoteUserInfo) {
    if (sessionImpl.isReleased()) {
      return;
    }
    if (remoteUserInfo == null) {
      Log.d(TAG, "RemoteUserInfo is null, ignoring command=" + command);
      return;
    }
    postOrRun(
        sessionImpl.getApplicationHandler(),
        () -> {
          if (sessionImpl.isReleased()) {
            return;
          }
          if (!sessionCompat.isActive()) {
            Log.w(
                TAG,
                "Ignore incoming player command before initialization. command="
                    + command
                    + ", pid="
                    + remoteUserInfo.getPid());
            return;
          }
          @Nullable ControllerInfo controller = tryGetController(remoteUserInfo);
          if (controller == null) {
            // Failed to get controller since connection was rejected.
            return;
          }
          if (!connectedControllersManager.isPlayerCommandAvailable(controller, command)) {
            return;
          }
          int resultCode = sessionImpl.onPlayerCommandRequestOnHandler(controller, command);
          if (resultCode != RESULT_SUCCESS) {
            // Don't run rejected command.
            return;
          }

          try {
            task.run(controller);
          } catch (RemoteException e) {
            // Currently it's TransactionTooLargeException or DeadSystemException.
            // We'd better to leave log for those cases because
            //   - TransactionTooLargeException means that we may need to fix our code.
            //     (e.g. add pagination or special way to deliver Bitmap)
            //   - DeadSystemException means that errors around it can be ignored.
            Log.w(TAG, "Exception in " + controller, e);
          }
        });
  }

  private void dispatchSessionTaskWithSessionCommand(
      @CommandCode int commandCode, SessionTask task) {
    dispatchSessionTaskWithSessionCommandInternal(
        null, commandCode, task, sessionCompat.getCurrentControllerInfo());
  }

  private void dispatchSessionTaskWithSessionCommand(
      SessionCommand sessionCommand, SessionTask task) {
    dispatchSessionTaskWithSessionCommandInternal(
        sessionCommand, COMMAND_CODE_CUSTOM, task, sessionCompat.getCurrentControllerInfo());
  }

  private void dispatchSessionTaskWithSessionCommandInternal(
      @Nullable SessionCommand sessionCommand,
      @CommandCode int commandCode,
      SessionTask task,
      @Nullable RemoteUserInfo remoteUserInfo) {
    if (remoteUserInfo == null) {
      Log.d(
          TAG,
          "RemoteUserInfo is null, ignoring command="
              + (sessionCommand == null ? commandCode : sessionCommand));
      return;
    }
    postOrRun(
        sessionImpl.getApplicationHandler(),
        () -> {
          if (sessionImpl.isReleased()) {
            return;
          }
          if (!sessionCompat.isActive()) {
            Log.w(
                TAG,
                "Ignore incoming session command before initialization. command="
                    + (sessionCommand == null ? commandCode : sessionCommand.customAction)
                    + ", pid="
                    + remoteUserInfo.getPid());
            return;
          }
          @Nullable ControllerInfo controller = tryGetController(remoteUserInfo);
          if (controller == null) {
            // Failed to get controller since connection was rejected.
            return;
          }
          if (sessionCommand != null) {
            if (!connectedControllersManager.isSessionCommandAvailable(
                controller, sessionCommand)) {
              return;
            }
          } else {
            if (!connectedControllersManager.isSessionCommandAvailable(controller, commandCode)) {
              return;
            }
          }
          try {
            task.run(controller);
          } catch (RemoteException e) {
            // Currently it's TransactionTooLargeException or DeadSystemException.
            // We'd better to leave log for those cases because
            //   - TransactionTooLargeException means that we may need to fix our code.
            //     (e.g. add pagination or special way to deliver Bitmap)
            //   - DeadSystemException means that errors around it can be ignored.
            Log.w(TAG, "Exception in " + controller, e);
          }
        });
  }

  @Nullable
  private ControllerInfo tryGetController(RemoteUserInfo remoteUserInfo) {
    @Nullable ControllerInfo controller = connectedControllersManager.getController(remoteUserInfo);
    if (controller == null) {
      // Try connect.
      ControllerCb controllerCb = new ControllerLegacyCb(remoteUserInfo);
      controller =
          new ControllerInfo(
              remoteUserInfo,
              ControllerInfo.LEGACY_CONTROLLER_VERSION,
              sessionManager.isTrustedForMediaControl(remoteUserInfo),
              controllerCb,
              /* connectionHints= */ Bundle.EMPTY);
      MediaSession.ConnectionResult connectionResult = sessionImpl.onConnectOnHandler(controller);
      if (!connectionResult.isAccepted) {
        try {
          controllerCb.onDisconnected(/* seq= */ 0);
        } catch (RemoteException e) {
          // Controller may have died prematurely.
        }
        return null;
      }
      connectedControllersManager.addController(
          controller.getRemoteUserInfo(),
          controller,
          connectionResult.availableSessionCommands,
          connectionResult.availablePlayerCommands);
    }
    // Reset disconnect timeout.
    connectionTimeoutHandler.disconnectControllerAfterTimeout(controller, connectionTimeoutMs);

    return controller;
  }

  public void setLegacyControllerDisconnectTimeoutMs(long timeoutMs) {
    connectionTimeoutMs = timeoutMs;
  }

  private void handleMediaRequest(MediaItem mediaItem, boolean play) {
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_SET_MEDIA_ITEM,
        controller -> {
          ListenableFuture<List<MediaItem>> mediaItemsFuture =
              sessionImpl.onAddMediaItemsOnHandler(controller, ImmutableList.of(mediaItem));
          Futures.addCallback(
              mediaItemsFuture,
              new FutureCallback<List<MediaItem>>() {
                @Override
                public void onSuccess(List<MediaItem> mediaItems) {
                  postOrRun(
                      sessionImpl.getApplicationHandler(),
                      () -> {
                        Player player = sessionImpl.getPlayerWrapper();
                        player.setMediaItems(mediaItems);
                        @Player.State int playbackState = player.getPlaybackState();
                        if (playbackState == Player.STATE_IDLE) {
                          player.prepare();
                        } else if (playbackState == Player.STATE_ENDED) {
                          player.seekTo(/* positionMs= */ C.TIME_UNSET);
                        }
                        if (play) {
                          player.play();
                        }
                      });
                }

                @Override
                public void onFailure(Throwable t) {
                  // Do nothing, the session is free to ignore these requests.
                }
              },
              MoreExecutors.directExecutor());
        },
        sessionCompat.getCurrentControllerInfo());
  }

  private void handleOnAddQueueItem(@Nullable MediaDescriptionCompat description, int index) {
    if (description == null) {
      return;
    }
    dispatchSessionTaskWithPlayerCommand(
        COMMAND_CHANGE_MEDIA_ITEMS,
        controller -> {
          @Nullable String mediaId = description.getMediaId();
          if (TextUtils.isEmpty(mediaId)) {
            Log.w(TAG, "onAddQueueItem(): Media ID shouldn't be empty");
            return;
          }
          MediaItem mediaItem = MediaUtils.convertToMediaItem(description);
          ListenableFuture<List<MediaItem>> mediaItemsFuture =
              sessionImpl.onAddMediaItemsOnHandler(controller, ImmutableList.of(mediaItem));
          Futures.addCallback(
              mediaItemsFuture,
              new FutureCallback<List<MediaItem>>() {
                @Override
                public void onSuccess(List<MediaItem> mediaItems) {
                  postOrRun(
                      sessionImpl.getApplicationHandler(),
                      () -> {
                        if (index == C.INDEX_UNSET) {
                          sessionImpl.getPlayerWrapper().addMediaItems(mediaItems);
                        } else {
                          sessionImpl.getPlayerWrapper().addMediaItems(index, mediaItems);
                        }
                      });
                }

                @Override
                public void onFailure(Throwable t) {
                  // Do nothing, the session is free to ignore these requests.
                }
              },
              MoreExecutors.directExecutor());
        },
        sessionCompat.getCurrentControllerInfo());
  }

  private static void sendCustomCommandResultWhenReady(
      ResultReceiver receiver, ListenableFuture<SessionResult> future) {
    future.addListener(
        () -> {
          SessionResult result;
          try {
            result = checkNotNull(future.get(), "SessionResult must not be null");
          } catch (CancellationException unused) {
            result = new SessionResult(RESULT_INFO_SKIPPED);
          } catch (ExecutionException | InterruptedException unused) {
            result = new SessionResult(RESULT_ERROR_UNKNOWN);
          }
          receiver.send(result.resultCode, result.extras);
        },
        MoreExecutors.directExecutor());
  }

  private static <T> void ignoreFuture(Future<T> unused) {
    // no-op
  }

  @SuppressWarnings("nullness:argument") // MediaSessionCompat didn't annotate @Nullable.
  private static void setMetadata(
      MediaSessionCompat sessionCompat, @Nullable MediaMetadataCompat metadataCompat) {
    sessionCompat.setMetadata(metadataCompat);
  }

  @SuppressWarnings("nullness:argument") // MediaSessionCompat didn't annotate @Nullable.
  private static void setQueue(MediaSessionCompat sessionCompat, @Nullable List<QueueItem> queue) {
    sessionCompat.setQueue(queue);
  }

  @SuppressWarnings("nullness:argument") // MediaSessionCompat didn't annotate @Nullable.
  private static void setQueueTitle(
      MediaSessionCompat sessionCompat, @Nullable CharSequence title) {
    sessionCompat.setQueueTitle(title);
  }

  private static MediaItem createMediaItemForMediaRequest(
      @Nullable String mediaId,
      @Nullable Uri mediaUri,
      @Nullable String searchQuery,
      @Nullable Bundle extras) {
    return new MediaItem.Builder()
        .setMediaId(mediaId == null ? MediaItem.DEFAULT_MEDIA_ID : mediaId)
        .setRequestMetadata(
            new MediaItem.RequestMetadata.Builder()
                .setMediaUri(mediaUri)
                .setSearchQuery(searchQuery)
                .setExtras(extras)
                .build())
        .build();
  }

  /* @FunctionalInterface */
  private interface SessionTask {

    void run(ControllerInfo controller) throws RemoteException;
  }

  private static final class ControllerLegacyCb implements ControllerCb {

    private final RemoteUserInfo remoteUserInfo;

    public ControllerLegacyCb(RemoteUserInfo remoteUserInfo) {
      this.remoteUserInfo = remoteUserInfo;
    }

    @Override
    public int hashCode() {
      return ObjectsCompat.hash(remoteUserInfo);
    }

    @Override
    public boolean equals(@Nullable Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || obj.getClass() != ControllerLegacyCb.class) {
        return false;
      }
      ControllerLegacyCb other = (ControllerLegacyCb) obj;
      return Util.areEqual(remoteUserInfo, other.remoteUserInfo);
    }
  }

  private final class ControllerLegacyCbForBroadcast implements ControllerCb {

    @Nullable private MediaItem currentMediaItemForMetadataUpdate;

    private long durationMsForMetadataUpdate;

    public ControllerLegacyCbForBroadcast() {
      durationMsForMetadataUpdate = C.TIME_UNSET;
    }

    @Override
    public void onDisconnected(int seq) throws RemoteException {
      // Calling MediaSessionCompat#release() is already done in release().
    }

    @Override
    public void onPlayerChanged(
        int seq, @Nullable PlayerWrapper oldPlayerWrapper, PlayerWrapper newPlayerWrapper)
        throws RemoteException {
      // Tells the playlist change first, so current media item index change notification
      // can point to the valid current media item in the playlist.
      Timeline newTimeline = newPlayerWrapper.getCurrentTimeline();
      if (oldPlayerWrapper == null
          || !Util.areEqual(oldPlayerWrapper.getCurrentTimeline(), newTimeline)) {
        onTimelineChanged(seq, newTimeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED);
      }
      MediaMetadata newPlaylistMetadata = newPlayerWrapper.getPlaylistMetadata();
      if (oldPlayerWrapper == null
          || !Util.areEqual(oldPlayerWrapper.getPlaylistMetadata(), newPlaylistMetadata)) {
        onPlaylistMetadataChanged(seq, newPlaylistMetadata);
      }
      MediaMetadata newMediaMetadata = newPlayerWrapper.getMediaMetadata();
      if (oldPlayerWrapper == null
          || !Util.areEqual(oldPlayerWrapper.getMediaMetadata(), newMediaMetadata)) {
        onMediaMetadataChanged(seq, newMediaMetadata);
      }
      if (oldPlayerWrapper == null
          || oldPlayerWrapper.getShuffleModeEnabled() != newPlayerWrapper.getShuffleModeEnabled()) {
        onShuffleModeEnabledChanged(seq, newPlayerWrapper.getShuffleModeEnabled());
      }
      if (oldPlayerWrapper == null
          || oldPlayerWrapper.getRepeatMode() != newPlayerWrapper.getRepeatMode()) {
        onRepeatModeChanged(seq, newPlayerWrapper.getRepeatMode());
      }

      // Forcefully update playback info to update VolumeProviderCompat attached to the
      // old player.
      onDeviceInfoChanged(seq, newPlayerWrapper.getDeviceInfo());

      // Rest of changes are all notified via PlaybackStateCompat.
      @Nullable MediaItem newMediaItem = newPlayerWrapper.getCurrentMediaItem();
      if (oldPlayerWrapper == null
          || !Util.areEqual(oldPlayerWrapper.getCurrentMediaItem(), newMediaItem)) {
        // Note: This will update both PlaybackStateCompat and metadata.
        onMediaItemTransition(
            seq, newMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED);
      } else {
        // If PlaybackStateCompat isn't updated by above if-statement, forcefully update
        // PlaybackStateCompat to tell the latest position and its event
        // time. This would also update playback speed, buffering state, player state, and error.
        sessionCompat.setPlaybackState(newPlayerWrapper.createPlaybackStateCompat());
      }
    }

    @Override
    public void onPlayerError(int seq, @Nullable PlaybackException playerError) {
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void setCustomLayout(int seq, List<CommandButton> layout) {
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onSessionExtrasChanged(int seq, Bundle sessionExtras) {
      sessionImpl.getSessionCompat().setExtras(sessionExtras);
    }

    @Override
    public void onPlayWhenReadyChanged(
        int seq, boolean playWhenReady, @Player.PlaybackSuppressionReason int reason)
        throws RemoteException {
      // Note: This method does not use any of the given arguments.
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onPlaybackSuppressionReasonChanged(
        int seq, @Player.PlaybackSuppressionReason int reason) throws RemoteException {
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onPlaybackStateChanged(
        int seq, @Player.State int state, @Nullable PlaybackException playerError)
        throws RemoteException {
      // Note: This method does not use any of the given arguments.
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onIsPlayingChanged(int seq, boolean isPlaying) throws RemoteException {
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onPositionDiscontinuity(
        int seq,
        PositionInfo oldPosition,
        PositionInfo newPosition,
        @DiscontinuityReason int reason)
        throws RemoteException {
      // Note: This method does not use any of the given arguments.
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onPlaybackParametersChanged(int seq, PlaybackParameters playbackParameters)
        throws RemoteException {
      // Note: This method does not use any of the given arguments.
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onMediaItemTransition(
        int seq, @Nullable MediaItem mediaItem, @Player.MediaItemTransitionReason int reason)
        throws RemoteException {
      updateMetadataIfChanged();
      if (mediaItem == null) {
        sessionCompat.setRatingType(RatingCompat.RATING_NONE);
      } else {
        sessionCompat.setRatingType(
            MediaUtils.getRatingCompatStyle(mediaItem.mediaMetadata.userRating));
      }
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onTimelineChanged(
        int seq, Timeline timeline, @Player.TimelineChangeReason int reason)
        throws RemoteException {
      if (timeline.isEmpty()) {
        setQueue(sessionCompat, null);
        return;
      }
      List<MediaItem> mediaItemList = MediaUtils.convertToMediaItemList(timeline);
      List<QueueItem> queueItemList = MediaUtils.convertToQueueItemList(mediaItemList);
      if (Util.SDK_INT < 21) {
        // In order to avoid TransactionTooLargeException for below API 21, we need to
        // cut the list so that it doesn't exceed the binder transaction limit.
        List<QueueItem> truncatedList =
            MediaUtils.truncateListBySize(queueItemList, TRANSACTION_SIZE_LIMIT_IN_BYTES);
        if (truncatedList.size() != timeline.getWindowCount()) {
          Log.i(
              TAG,
              "Sending " + truncatedList.size() + " items out of " + timeline.getWindowCount());
        }
        sessionCompat.setQueue(truncatedList);
      } else {
        // Framework MediaSession#setQueue() uses ParceledListSlice,
        // which means we can safely send long lists.
        sessionCompat.setQueue(queueItemList);
      }

      // Duration might be unknown at onMediaItemTransition and become available afterward.
      updateMetadataIfChanged();
    }

    @Override
    public void onPlaylistMetadataChanged(int seq, MediaMetadata playlistMetadata)
        throws RemoteException {
      // Since there is no 'queue metadata', only set title of the queue.
      @Nullable CharSequence queueTitle = sessionCompat.getController().getQueueTitle();
      @Nullable CharSequence newTitle = playlistMetadata.title;
      if (!TextUtils.equals(queueTitle, newTitle)) {
        setQueueTitle(sessionCompat, newTitle);
      }
    }

    @Override
    public void onShuffleModeEnabledChanged(int seq, boolean shuffleModeEnabled)
        throws RemoteException {
      sessionImpl
          .getSessionCompat()
          .setShuffleMode(MediaUtils.convertToPlaybackStateCompatShuffleMode(shuffleModeEnabled));
    }

    @Override
    public void onRepeatModeChanged(int seq, @RepeatMode int repeatMode) throws RemoteException {
      sessionImpl
          .getSessionCompat()
          .setRepeatMode(MediaUtils.convertToPlaybackStateCompatRepeatMode(repeatMode));
    }

    @Override
    public void onAudioAttributesChanged(int seq, AudioAttributes audioAttributes) {
      @DeviceInfo.PlaybackType
      int playbackType = sessionImpl.getPlayerWrapper().getDeviceInfo().playbackType;
      if (playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL) {
        int legacyStreamType = MediaUtils.getLegacyStreamType(audioAttributes);
        sessionCompat.setPlaybackToLocal(legacyStreamType);
      }
    }

    @Override
    public void onDeviceInfoChanged(int seq, DeviceInfo deviceInfo) {
      PlayerWrapper player = sessionImpl.getPlayerWrapper();
      volumeProviderCompat = player.createVolumeProviderCompat();
      if (volumeProviderCompat == null) {
        int streamType = MediaUtils.getLegacyStreamType(player.getAudioAttributes());
        sessionCompat.setPlaybackToLocal(streamType);
      } else {
        sessionCompat.setPlaybackToRemote(volumeProviderCompat);
      }
    }

    @Override
    public void onDeviceVolumeChanged(int seq, int volume, boolean muted) {
      if (volumeProviderCompat != null) {
        volumeProviderCompat.setCurrentVolume(muted ? 0 : volume);
      }
    }

    @Override
    public void onPeriodicSessionPositionInfoChanged(
        int unusedSeq, SessionPositionInfo unusedSessionPositionInfo) throws RemoteException {
      sessionImpl
          .getSessionCompat()
          .setPlaybackState(sessionImpl.getPlayerWrapper().createPlaybackStateCompat());
    }

    @Override
    public void onMediaMetadataChanged(int seq, MediaMetadata mediaMetadata) {
      // Metadata change will be notified by onMediaItemTransition.
    }

    private void updateMetadataIfChanged() {
      @Nullable MediaItem currentMediaItem = sessionImpl.getPlayerWrapper().getCurrentMediaItem();
      long durationMs = sessionImpl.getPlayerWrapper().getDuration();

      if (ObjectsCompat.equals(currentMediaItemForMetadataUpdate, currentMediaItem)
          && durationMsForMetadataUpdate == durationMs) {
        return;
      }

      currentMediaItemForMetadataUpdate = currentMediaItem;
      durationMsForMetadataUpdate = durationMs;

      if (currentMediaItem == null) {
        setMetadata(sessionCompat, /* metadataCompat= */ null);
      } else {
        sessionCompat.setMetadata(
            MediaUtils.convertToMediaMetadataCompat(currentMediaItem, durationMs));
      }
    }
  }

  private class ConnectionTimeoutHandler extends Handler {

    private static final int MSG_CONNECTION_TIMED_OUT = 1001;

    public ConnectionTimeoutHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      ControllerInfo controller = (ControllerInfo) msg.obj;
      if (connectedControllersManager.isConnected(controller)) {
        try {
          checkStateNotNull(controller.getControllerCb()).onDisconnected(/* seq= */ 0);
        } catch (RemoteException e) {
          // Controller may have died prematurely.
        }
        connectedControllersManager.removeController(controller);
      }
    }

    public void disconnectControllerAfterTimeout(
        ControllerInfo controller, long disconnectTimeoutMs) {
      removeMessages(MSG_CONNECTION_TIMED_OUT, controller);
      Message msg = obtainMessage(MSG_CONNECTION_TIMED_OUT, controller);
      sendMessageDelayed(msg, disconnectTimeoutMs);
    }
  }

  private class MediaPlayPauseKeyHandler extends Handler {

    private static final int MSG_DOUBLE_TAP_TIMED_OUT = 1002;

    public MediaPlayPauseKeyHandler(Looper looper) {
      super(looper);
    }

    @Override
    public void handleMessage(Message msg) {
      RemoteUserInfo remoteUserInfo = (RemoteUserInfo) msg.obj;
      handleMediaPlayPauseOnHandler(remoteUserInfo);
    }

    public void addPendingMediaPlayPauseKey(RemoteUserInfo remoteUserInfo) {
      Message msg = obtainMessage(MSG_DOUBLE_TAP_TIMED_OUT, remoteUserInfo);
      sendMessageDelayed(msg, ViewConfiguration.getDoubleTapTimeout());
    }

    public void clearPendingMediaPlayPauseKey() {
      removeMessages(MSG_DOUBLE_TAP_TIMED_OUT);
    }

    public boolean hasPendingMediaPlayPauseKey() {
      return hasMessages(MSG_DOUBLE_TAP_TIMED_OUT);
    }
  }
}
