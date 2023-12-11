/*
 * Copyright 2018 The Android Open Source Project
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

import static androidx.media3.common.Player.COMMAND_GET_TRACKS;
import static androidx.media3.session.MediaSession.ConnectionResult.accept;
import static androidx.media3.test.session.common.CommonConstants.ACTION_MEDIA3_SESSION;
import static androidx.media3.test.session.common.CommonConstants.KEY_AUDIO_ATTRIBUTES;
import static androidx.media3.test.session.common.CommonConstants.KEY_AVAILABLE_COMMANDS;
import static androidx.media3.test.session.common.CommonConstants.KEY_BUFFERED_PERCENTAGE;
import static androidx.media3.test.session.common.CommonConstants.KEY_BUFFERED_POSITION;
import static androidx.media3.test.session.common.CommonConstants.KEY_CONTENT_BUFFERED_POSITION;
import static androidx.media3.test.session.common.CommonConstants.KEY_CONTENT_DURATION;
import static androidx.media3.test.session.common.CommonConstants.KEY_CONTENT_POSITION;
import static androidx.media3.test.session.common.CommonConstants.KEY_CURRENT_AD_GROUP_INDEX;
import static androidx.media3.test.session.common.CommonConstants.KEY_CURRENT_AD_INDEX_IN_AD_GROUP;
import static androidx.media3.test.session.common.CommonConstants.KEY_CURRENT_CUE_GROUP;
import static androidx.media3.test.session.common.CommonConstants.KEY_CURRENT_LIVE_OFFSET;
import static androidx.media3.test.session.common.CommonConstants.KEY_CURRENT_MEDIA_ITEM_INDEX;
import static androidx.media3.test.session.common.CommonConstants.KEY_CURRENT_PERIOD_INDEX;
import static androidx.media3.test.session.common.CommonConstants.KEY_CURRENT_POSITION;
import static androidx.media3.test.session.common.CommonConstants.KEY_CURRENT_TRACKS;
import static androidx.media3.test.session.common.CommonConstants.KEY_DEVICE_INFO;
import static androidx.media3.test.session.common.CommonConstants.KEY_DEVICE_MUTED;
import static androidx.media3.test.session.common.CommonConstants.KEY_DEVICE_VOLUME;
import static androidx.media3.test.session.common.CommonConstants.KEY_DURATION;
import static androidx.media3.test.session.common.CommonConstants.KEY_IS_LOADING;
import static androidx.media3.test.session.common.CommonConstants.KEY_IS_PLAYING_AD;
import static androidx.media3.test.session.common.CommonConstants.KEY_MAX_SEEK_TO_PREVIOUS_POSITION_MS;
import static androidx.media3.test.session.common.CommonConstants.KEY_MEDIA_METADATA;
import static androidx.media3.test.session.common.CommonConstants.KEY_PLAYBACK_PARAMETERS;
import static androidx.media3.test.session.common.CommonConstants.KEY_PLAYBACK_STATE;
import static androidx.media3.test.session.common.CommonConstants.KEY_PLAYBACK_SUPPRESSION_REASON;
import static androidx.media3.test.session.common.CommonConstants.KEY_PLAYER_ERROR;
import static androidx.media3.test.session.common.CommonConstants.KEY_PLAYLIST_METADATA;
import static androidx.media3.test.session.common.CommonConstants.KEY_PLAY_WHEN_READY;
import static androidx.media3.test.session.common.CommonConstants.KEY_REPEAT_MODE;
import static androidx.media3.test.session.common.CommonConstants.KEY_SEEK_BACK_INCREMENT_MS;
import static androidx.media3.test.session.common.CommonConstants.KEY_SEEK_FORWARD_INCREMENT_MS;
import static androidx.media3.test.session.common.CommonConstants.KEY_SHUFFLE_MODE_ENABLED;
import static androidx.media3.test.session.common.CommonConstants.KEY_TIMELINE;
import static androidx.media3.test.session.common.CommonConstants.KEY_TOTAL_BUFFERED_DURATION;
import static androidx.media3.test.session.common.CommonConstants.KEY_TRACK_SELECTION_PARAMETERS;
import static androidx.media3.test.session.common.CommonConstants.KEY_VIDEO_SIZE;
import static androidx.media3.test.session.common.CommonConstants.KEY_VOLUME;
import static androidx.media3.test.session.common.MediaSessionConstants.KEY_AVAILABLE_SESSION_COMMANDS;
import static androidx.media3.test.session.common.MediaSessionConstants.KEY_COMMAND_GET_TASKS_UNAVAILABLE;
import static androidx.media3.test.session.common.MediaSessionConstants.KEY_CONTROLLER;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_COMMAND_GET_TRACKS;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_CONTROLLER_LISTENER_SESSION_REJECTS;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_GET_CUSTOM_LAYOUT;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_GET_SESSION_ACTIVITY;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_IS_SESSION_COMMAND_AVAILABLE;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_MEDIA_CONTROLLER_COMPAT_CALLBACK_WITH_MEDIA_SESSION_TEST;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_ON_TRACKS_CHANGED_VIDEO_TO_AUDIO_TRANSITION;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_ON_VIDEO_SIZE_CHANGED;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_SET_SHOW_PLAY_BUTTON_IF_SUPPRESSED_TO_FALSE;
import static androidx.media3.test.session.common.MediaSessionConstants.TEST_WITH_CUSTOM_COMMANDS;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.Util;
import androidx.media3.session.MediaSession.ControllerInfo;
import androidx.media3.test.session.common.IRemoteMediaSession;
import androidx.media3.test.session.common.MockActivity;
import androidx.media3.test.session.common.TestHandler;
import androidx.media3.test.session.common.TestHandler.TestRunnable;
import androidx.media3.test.session.common.TestUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * A Service that creates {@link MediaSession} and calls its methods according to the client app's
 * requests.
 */
public class MediaSessionProviderService extends Service {

  public static final String KEY_ENABLE_FAKE_MEDIA_NOTIFICATION_MANAGER_CONTROLLER =
      "key_enable_fake_media_notification_manager_controller";
  private static final String TAG = "MSProviderService";

  private Map<String, MediaSession> sessionMap = new HashMap<>();
  private RemoteMediaSessionStub sessionBinder;

  private TestHandler handler;

  @Override
  public void onCreate() {
    super.onCreate();
    sessionBinder = new RemoteMediaSessionStub();
    handler = new TestHandler(getMainLooper());
  }

  @Override
  public IBinder onBind(Intent intent) {
    if (ACTION_MEDIA3_SESSION.equals(intent.getAction())) {
      return sessionBinder;
    }
    return null;
  }

  @Override
  public void onDestroy() {
    for (MediaSession session : sessionMap.values()) {
      session.release();
    }
  }

  private class RemoteMediaSessionStub extends IRemoteMediaSession.Stub {

    private void runOnHandler(TestRunnable runnable) throws RemoteException {
      try {
        handler.postAndSync(runnable);
      } catch (Exception e) {
        Log.e(TAG, "Exception thrown while waiting for handler", e);
        throw new RemoteException("Unexpected exception");
      }
    }

    private <V> V runOnHandler(Callable<V> callable) throws RemoteException {
      try {
        return handler.postAndSync(callable);
      } catch (Exception e) {
        Log.e(TAG, "Exception thrown while waiting for handler", e);
        throw new RemoteException("Unexpected exception");
      }
    }

    @Override
    public void create(String sessionId, Bundle tokenExtras) throws RemoteException {
      if (tokenExtras == null) {
        tokenExtras = Bundle.EMPTY;
      }
      boolean useFakeMediaNotificationManagerController =
          tokenExtras.getBoolean(
              KEY_ENABLE_FAKE_MEDIA_NOTIFICATION_MANAGER_CONTROLLER, /* defaultValue= */ false);
      MockPlayer mockPlayer =
          new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build();
      MediaSession.Builder builder =
          new MediaSession.Builder(MediaSessionProviderService.this, mockPlayer).setId(sessionId);

      builder.setExtras(tokenExtras);
      switch (sessionId) {
        case TEST_GET_SESSION_ACTIVITY:
          {
            Intent sessionActivity =
                new Intent(MediaSessionProviderService.this, MockActivity.class);
            PendingIntent pendingIntent =
                PendingIntent.getActivity(
                    MediaSessionProviderService.this,
                    /* requestCode= */ 0,
                    sessionActivity,
                    Util.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            builder.setSessionActivity(pendingIntent);
            break;
          }
        case TEST_GET_CUSTOM_LAYOUT:
          {
            builder.setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    return accept(
                        new SessionCommands.Builder()
                            .add(new SessionCommand("command1", Bundle.EMPTY))
                            .add(new SessionCommand("command2", Bundle.EMPTY))
                            .build(),
                        Player.Commands.EMPTY);
                  }
                });
            break;
          }
        case TEST_WITH_CUSTOM_COMMANDS:
          {
            SessionCommands availableSessionCommands =
                new SessionCommands.Builder()
                    .add(new SessionCommand("action1", Bundle.EMPTY))
                    .add(new SessionCommand("action2", Bundle.EMPTY))
                    .build();
            builder.setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    return accept(availableSessionCommands, Player.Commands.EMPTY);
                  }
                });
            break;
          }
        case TEST_CONTROLLER_LISTENER_SESSION_REJECTS:
          {
            builder.setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    return MediaSession.ConnectionResult.reject();
                  }
                });
            break;
          }
        case TEST_IS_SESSION_COMMAND_AVAILABLE:
          {
            SessionCommands availableSessionCommands =
                SessionCommands.fromBundle(tokenExtras.getBundle(KEY_AVAILABLE_SESSION_COMMANDS));
            builder.setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    return accept(availableSessionCommands, Player.Commands.EMPTY);
                  }
                });
            break;
          }
        case TEST_COMMAND_GET_TRACKS:
          {
            ImmutableList<Tracks.Group> trackGroups =
                ImmutableList.of(
                    new Tracks.Group(
                        new TrackGroup(new Format.Builder().setChannelCount(2).build()),
                        /* adaptiveSupported= */ false,
                        /* trackSupport= */ new int[1],
                        /* trackSelected= */ new boolean[1]));
            mockPlayer.currentTracks = new Tracks(trackGroups);
            builder.setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    Player.Commands.Builder commandBuilder =
                        new Player.Commands.Builder().addAllCommands();
                    if (controller
                        .getConnectionHints()
                        .getBoolean(KEY_COMMAND_GET_TASKS_UNAVAILABLE, /* defaultValue= */ false)) {
                      commandBuilder.remove(COMMAND_GET_TRACKS);
                    }
                    return accept(SessionCommands.EMPTY, commandBuilder.build());
                  }
                });
            break;
          }
        case TEST_ON_TRACKS_CHANGED_VIDEO_TO_AUDIO_TRANSITION:
        case TEST_ON_VIDEO_SIZE_CHANGED:
          {
            mockPlayer.videoSize = MediaTestUtils.createDefaultVideoSize();
            mockPlayer.currentTracks = MediaTestUtils.createDefaultVideoTracks();
            break;
          }
        case TEST_SET_SHOW_PLAY_BUTTON_IF_SUPPRESSED_TO_FALSE:
          {
            builder.setShowPlayButtonIfPlaybackIsSuppressed(false);
            break;
          }
        case TEST_MEDIA_CONTROLLER_COMPAT_CALLBACK_WITH_MEDIA_SESSION_TEST:
          {
            builder.setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    MediaSession.ConnectionResult connectionResult =
                        MediaSession.Callback.super.onConnect(session, controller);
                    SessionCommands availableSessionCommands =
                        connectionResult.availableSessionCommands;
                    if (session.isMediaNotificationController(controller)) {
                      availableSessionCommands =
                          connectionResult
                              .availableSessionCommands
                              .buildUpon()
                              .add(new SessionCommand("command1", Bundle.EMPTY))
                              .build();
                    }
                    return accept(
                        availableSessionCommands, connectionResult.availablePlayerCommands);
                  }
                });
            break;
          }
        default: // fall out
      }

      runOnHandler(
          () -> {
            MediaSession session = builder.build();
            session.setSessionPositionUpdateDelayMs(0L);
            if (useFakeMediaNotificationManagerController) {
              Bundle connectionHints = new Bundle();
              connectionHints.putBoolean(
                  MediaController.KEY_MEDIA_NOTIFICATION_CONTROLLER_FLAG, true);
              //noinspection unused
              ListenableFuture<MediaController> unusedFuture =
                  new MediaController.Builder(getApplicationContext(), session.getToken())
                      .setListener(
                          new MediaController.Listener() {
                            @Override
                            public void onDisconnected(MediaController controller) {
                              controller.release();
                            }
                          })
                      .setConnectionHints(connectionHints)
                      .buildAsync();
            }
            sessionMap.put(sessionId, session);
          });
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MediaSession methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public Bundle getToken(String sessionId) throws RemoteException {
      return runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            return session.getToken().toBundle();
          });
    }

    @Override
    public Bundle getCompatToken(String sessionId) throws RemoteException {
      return runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            return session.getSessionCompat().getSessionToken().toBundle();
          });
    }

    @Override
    public void setSessionPositionUpdateDelayMs(String sessionId, long updateDelayMs)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            session.setSessionPositionUpdateDelayMs(updateDelayMs);
          });
    }

    @Override
    public void setPlayer(String sessionId, Bundle config) throws RemoteException {
      runOnHandler(
          () -> {
            config.setClassLoader(MediaSession.class.getClassLoader());
            MediaSession session = sessionMap.get(sessionId);
            session.setPlayer(createMockPlayer(config));
          });
    }

    private Player createMockPlayer(Bundle config) {
      MockPlayer player = new MockPlayer.Builder().build();
      @Nullable Bundle playerErrorBundle = config.getBundle(KEY_PLAYER_ERROR);
      if (playerErrorBundle != null) {
        player.playerError = PlaybackException.fromBundle(playerErrorBundle);
      }
      player.currentPosition = config.getLong(KEY_CURRENT_POSITION, player.currentPosition);
      player.bufferedPosition = config.getLong(KEY_BUFFERED_POSITION, player.bufferedPosition);
      player.bufferedPercentage = config.getInt(KEY_BUFFERED_PERCENTAGE, player.bufferedPercentage);
      player.duration = config.getLong(KEY_DURATION, player.duration);
      player.totalBufferedDuration =
          config.getLong(KEY_TOTAL_BUFFERED_DURATION, player.totalBufferedDuration);
      player.currentLiveOffset = config.getLong(KEY_CURRENT_LIVE_OFFSET, player.currentLiveOffset);
      player.contentDuration = config.getLong(KEY_CONTENT_DURATION, player.contentDuration);
      player.contentPosition = config.getLong(KEY_CONTENT_POSITION, player.contentPosition);
      player.contentBufferedPosition =
          config.getLong(KEY_CONTENT_BUFFERED_POSITION, player.contentBufferedPosition);
      player.isPlayingAd = config.getBoolean(KEY_IS_PLAYING_AD, player.isPlayingAd);
      player.currentAdGroupIndex =
          config.getInt(KEY_CURRENT_AD_GROUP_INDEX, player.currentAdGroupIndex);
      player.currentAdIndexInAdGroup =
          config.getInt(KEY_CURRENT_AD_INDEX_IN_AD_GROUP, player.currentAdIndexInAdGroup);
      @Nullable Bundle playbackParametersBundle = config.getBundle(KEY_PLAYBACK_PARAMETERS);
      if (playbackParametersBundle != null) {
        player.playbackParameters = PlaybackParameters.fromBundle(playbackParametersBundle);
      }
      @Nullable Bundle timelineBundle = config.getBundle(KEY_TIMELINE);
      if (timelineBundle != null) {
        player.timeline = Timeline.fromBundle(timelineBundle);
      }
      player.currentMediaItemIndex =
          config.getInt(KEY_CURRENT_MEDIA_ITEM_INDEX, player.currentMediaItemIndex);
      player.currentPeriodIndex =
          config.getInt(KEY_CURRENT_PERIOD_INDEX, player.currentPeriodIndex);
      @Nullable Bundle playlistMetadataBundle = config.getBundle(KEY_PLAYLIST_METADATA);
      if (playlistMetadataBundle != null) {
        player.playlistMetadata = MediaMetadata.fromBundle(playlistMetadataBundle);
      }
      @Nullable Bundle videoSizeBundle = config.getBundle(KEY_VIDEO_SIZE);
      if (videoSizeBundle != null) {
        player.videoSize = VideoSize.fromBundle(videoSizeBundle);
      }
      player.volume = config.getFloat(KEY_VOLUME, player.volume);
      @Nullable Bundle audioAttributesBundle = config.getBundle(KEY_AUDIO_ATTRIBUTES);
      if (audioAttributesBundle != null) {
        player.audioAttributes = AudioAttributes.fromBundle(audioAttributesBundle);
      }
      Bundle cueGroupBundle = config.getBundle(KEY_CURRENT_CUE_GROUP);
      player.cueGroup =
          cueGroupBundle == null ? CueGroup.EMPTY_TIME_ZERO : CueGroup.fromBundle(cueGroupBundle);
      @Nullable Bundle deviceInfoBundle = config.getBundle(KEY_DEVICE_INFO);
      if (deviceInfoBundle != null) {
        player.deviceInfo = DeviceInfo.fromBundle(deviceInfoBundle);
      }
      player.deviceVolume = config.getInt(KEY_DEVICE_VOLUME, player.deviceVolume);
      player.deviceMuted = config.getBoolean(KEY_DEVICE_MUTED, player.deviceMuted);
      player.playWhenReady = config.getBoolean(KEY_PLAY_WHEN_READY, player.playWhenReady);
      player.playbackSuppressionReason =
          config.getInt(KEY_PLAYBACK_SUPPRESSION_REASON, player.playbackSuppressionReason);
      player.playbackState = config.getInt(KEY_PLAYBACK_STATE, player.playbackState);
      player.isLoading = config.getBoolean(KEY_IS_LOADING, player.isLoading);
      player.repeatMode = config.getInt(KEY_REPEAT_MODE, player.repeatMode);
      player.shuffleModeEnabled =
          config.getBoolean(KEY_SHUFFLE_MODE_ENABLED, player.shuffleModeEnabled);
      player.seekBackIncrementMs =
          config.getLong(KEY_SEEK_BACK_INCREMENT_MS, player.seekBackIncrementMs);
      player.seekForwardIncrementMs =
          config.getLong(KEY_SEEK_FORWARD_INCREMENT_MS, player.seekForwardIncrementMs);
      @Nullable Bundle mediaMetadataBundle = config.getBundle(KEY_MEDIA_METADATA);
      if (mediaMetadataBundle != null) {
        player.mediaMetadata = MediaMetadata.fromBundle(mediaMetadataBundle);
      }
      player.maxSeekToPreviousPositionMs =
          config.getLong(KEY_MAX_SEEK_TO_PREVIOUS_POSITION_MS, player.maxSeekToPreviousPositionMs);
      @Nullable Bundle currentTracksBundle = config.getBundle(KEY_CURRENT_TRACKS);
      if (currentTracksBundle != null) {
        player.currentTracks = Tracks.fromBundle(currentTracksBundle);
      }
      @Nullable
      Bundle trackSelectionParametersBundle = config.getBundle(KEY_TRACK_SELECTION_PARAMETERS);
      if (trackSelectionParametersBundle != null) {
        player.trackSelectionParameters =
            TrackSelectionParameters.fromBundle(trackSelectionParametersBundle);
      }
      @Nullable Bundle availableCommandsBundle = config.getBundle(KEY_AVAILABLE_COMMANDS);
      if (availableCommandsBundle != null) {
        player.commands = Player.Commands.fromBundle(availableCommandsBundle);
      }
      return player;
    }

    @Override
    public void broadcastCustomCommand(String sessionId, Bundle command, Bundle args)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            session.broadcastCustomCommand(SessionCommand.fromBundle(command), args);
          });
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void sendCustomCommand(String sessionId, Bundle command, Bundle args)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            List<ControllerInfo> controllerInfos = MediaTestUtils.getTestControllerInfos(session);
            if (controllerInfos.isEmpty()) {
              Log.e(
                  TAG,
                  "No connected controllers to receive custom command. sessionId=" + sessionId);
            }
            for (ControllerInfo info : controllerInfos) {
              session.sendCustomCommand(info, SessionCommand.fromBundle(command), args);
            }
          });
    }

    @Override
    public void release(String sessionId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            session.release();
          });
    }

    @Override
    public void setAvailableCommands(
        String sessionId, Bundle sessionCommands, Bundle playerCommands) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            List<ControllerInfo> controllerInfos = MediaTestUtils.getTestControllerInfos(session);
            if (controllerInfos.isEmpty()) {
              Log.e(
                  TAG,
                  "No connected controllers to receive available commands. sessionId=" + sessionId);
            }
            for (ControllerInfo info : controllerInfos) {
              session.setAvailableCommands(
                  info,
                  SessionCommands.fromBundle(sessionCommands),
                  Player.Commands.fromBundle(playerCommands));
            }
          });
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void setCustomLayout(String sessionId, List<Bundle> layout) throws RemoteException {
      if (layout == null) {
        return;
      }
      runOnHandler(
          () -> {
            ImmutableList.Builder<CommandButton> builder = new ImmutableList.Builder<>();
            for (Bundle bundle : layout) {
              builder.add(CommandButton.fromBundle(bundle));
            }
            MediaSession session = sessionMap.get(sessionId);
            session.setCustomLayout(builder.build());
          });
    }

    @Override
    public void setSessionExtras(String sessionId, Bundle extras) throws RemoteException {
      runOnHandler(() -> sessionMap.get(sessionId).setSessionExtras(extras));
    }

    @Override
    public void setSessionExtrasForController(String sessionId, String controllerKey, Bundle extras)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession mediaSession = sessionMap.get(sessionId);
            for (ControllerInfo controllerInfo : mediaSession.getConnectedControllers()) {
              if (controllerInfo
                  .getConnectionHints()
                  .getString(KEY_CONTROLLER, /* defaultValue= */ "")
                  .equals(controllerKey)) {
                mediaSession.setSessionExtras(controllerInfo, extras);
                break;
              }
            }
          });
    }

    @Override
    public void setSessionActivity(String sessionId, PendingIntent sessionActivity)
        throws RemoteException {
      runOnHandler(() -> sessionMap.get(sessionId).setSessionActivity(sessionActivity));
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MockPlayer methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void notifyPlayerError(String sessionId, @Nullable Bundle playerErrorBundle)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            @Nullable
            PlaybackException playerError =
                playerErrorBundle == null
                    ? player.playerError
                    : PlaybackException.fromBundle(playerErrorBundle);
            player.notifyPlayerError(playerError);
          });
    }

    @Override
    public void setPlayWhenReady(String sessionId, boolean playWhenReady, int reason)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.playWhenReady = playWhenReady;
            player.playbackSuppressionReason = reason;
          });
    }

    @Override
    public void setPlaybackState(String sessionId, int state) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.playbackState = state;
          });
    }

    @Override
    public void setCurrentPosition(String sessionId, long pos) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.currentPosition = pos;
          });
    }

    @Override
    public void setBufferedPosition(String sessionId, long pos) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.bufferedPosition = pos;
          });
    }

    @Override
    public void setDuration(String sessionId, long duration) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.duration = duration;
          });
    }

    @Override
    public void setBufferedPercentage(String sessionId, int bufferedPercentage)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.bufferedPercentage = bufferedPercentage;
          });
    }

    @Override
    public void setTotalBufferedDuration(String sessionId, long totalBufferedDuration)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.totalBufferedDuration = totalBufferedDuration;
          });
    }

    @Override
    public void setCurrentLiveOffset(String sessionId, long currentLiveOffset)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.currentLiveOffset = currentLiveOffset;
          });
    }

    @Override
    public void setContentDuration(String sessionId, long contentDuration) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.contentDuration = contentDuration;
          });
    }

    @Override
    public void setContentPosition(String sessionId, long contentPosition) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.contentPosition = contentPosition;
          });
    }

    @Override
    public void setContentBufferedPosition(String sessionId, long contentBufferedPosition)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.contentBufferedPosition = contentBufferedPosition;
          });
    }

    @Override
    public void setPlaybackParameters(String sessionId, Bundle playbackParametersBundle)
        throws RemoteException {
      PlaybackParameters playbackParameters =
          PlaybackParameters.fromBundle(playbackParametersBundle);
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.setPlaybackParameters(playbackParameters);
          });
    }

    @Override
    public void setIsPlayingAd(String sessionId, boolean isPlayingAd) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.isPlayingAd = isPlayingAd;
          });
    }

    @Override
    public void setCurrentAdGroupIndex(String sessionId, int currentAdGroupIndex)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.currentAdGroupIndex = currentAdGroupIndex;
          });
    }

    @Override
    public void setDeviceVolume(String sessionId, int volume, int flags) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.setDeviceVolume(volume, flags);
          });
    }

    @Override
    public void decreaseDeviceVolume(String sessionId, int flags) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.decreaseDeviceVolume(flags);
          });
    }

    @Override
    public void increaseDeviceVolume(String sessionId, int flags) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.increaseDeviceVolume(flags);
          });
    }

    @Override
    public void setDeviceMuted(String sessionId, boolean muted, int flags) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.setDeviceMuted(muted, flags);
          });
    }

    @Override
    public void setCurrentAdIndexInAdGroup(String sessionId, int currentAdIndexInAdGroup)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.currentAdIndexInAdGroup = currentAdIndexInAdGroup;
          });
    }

    @Override
    public void notifyPlayWhenReadyChanged(
        String sessionId, boolean playWhenReady, @Player.PlaybackSuppressionReason int reason)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyPlayWhenReadyChanged(playWhenReady, reason);
          });
    }

    @Override
    public void notifyPlaybackStateChanged(String sessionId, @Player.State int state)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyPlaybackStateChanged(state);
          });
    }

    @Override
    public void notifyIsLoadingChanged(String sessionId, boolean isLoading) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyIsLoadingChanged(isLoading);
          });
    }

    @Override
    public void notifyPositionDiscontinuity(
        String sessionId,
        Bundle oldPositionBundle,
        Bundle newPositionBundle,
        @DiscontinuityReason int reason)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyPositionDiscontinuity(
                PositionInfo.fromBundle(oldPositionBundle),
                PositionInfo.fromBundle(newPositionBundle),
                reason);
          });
    }

    @Override
    public void notifyPlaybackParametersChanged(String sessionId, Bundle playbackParametersBundle)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyPlaybackParametersChanged(
                PlaybackParameters.fromBundle(playbackParametersBundle));
          });
    }

    @Override
    public void notifyMediaItemTransition(
        String sessionId, int index, @Player.MediaItemTransitionReason int reason)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            Timeline.Window window = new Timeline.Window();
            @Nullable
            MediaItem mediaItem =
                index == C.INDEX_UNSET ? null : player.timeline.getWindow(index, window).mediaItem;
            player.notifyMediaItemTransition(mediaItem, reason);
          });
    }

    @Override
    public void notifyAudioAttributesChanged(String sessionId, Bundle audioAttributesBundle)
        throws RemoteException {
      AudioAttributes audioAttributes = AudioAttributes.fromBundle(audioAttributesBundle);
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.audioAttributes = audioAttributes;
            player.notifyAudioAttributesChanged(audioAttributes);
          });
    }

    ////////////////////////////////////////////////////////////////////////////////
    // MockPlaylistAgent methods
    ////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setTimeline(String sessionId, Bundle timelineBundle) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.timeline = Timeline.fromBundle(timelineBundle);
            List<MediaItem> mediaItems = new ArrayList<>();
            for (int i = 0; i < player.timeline.getWindowCount(); i++) {
              mediaItems.add(
                  player.timeline.getWindow(/* windowIndex= */ i, new Timeline.Window()).mediaItem);
            }
            player.mediaItems.clear();
            player.mediaItems.addAll(mediaItems);
          });
    }

    @Override
    public void createAndSetFakeTimeline(String sessionId, int windowCount) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();

            List<MediaItem> mediaItems = new ArrayList<>();
            for (int windowIndex = 0; windowIndex < windowCount; windowIndex++) {
              mediaItems.add(
                  MediaTestUtils.createMediaItem(TestUtils.getMediaIdInFakeTimeline(windowIndex)));
            }
            player.mediaItems.clear();
            player.mediaItems.addAll(mediaItems);
            player.timeline = new PlaylistTimeline(mediaItems);
          });
    }

    @Override
    public void setMediaMetadata(String sessionId, Bundle metadataBundle) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.mediaMetadata = MediaMetadata.fromBundle(metadataBundle);
          });
    }

    @Override
    public void setPlaylistMetadata(String sessionId, Bundle playlistMetadataBundle)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.playlistMetadata = MediaMetadata.fromBundle(playlistMetadataBundle);
          });
    }

    @Override
    public void setShuffleModeEnabled(String sessionId, boolean shuffleModeEnabled)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.shuffleModeEnabled = shuffleModeEnabled;
          });
    }

    @Override
    public void setRepeatMode(String sessionId, int repeatMode) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.repeatMode = repeatMode;
          });
    }

    @Override
    public void setCurrentMediaItemIndex(String sessionId, int index) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.currentMediaItemIndex = index;
          });
    }

    @Override
    public void setTrackSelectionParameters(String sessionId, Bundle parameters)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.trackSelectionParameters = TrackSelectionParameters.fromBundle(parameters);
          });
    }

    @Override
    public void setVolume(String sessionId, float volume) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.setVolume(volume);
          });
    }

    @Override
    public void notifyAvailableCommandsChanged(String sessionId, Bundle commandsBundle)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyAvailableCommandsChanged(
                commandsBundle == null
                    ? Player.Commands.EMPTY
                    : Player.Commands.fromBundle(commandsBundle));
          });
    }

    @Override
    public void notifyTimelineChanged(String sessionId, @Player.TimelineChangeReason int reason)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyTimelineChanged(reason);
          });
    }

    @Override
    public void notifyPlaylistMetadataChanged(String sessionId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyPlaylistMetadataChanged();
          });
    }

    @Override
    public void notifyShuffleModeEnabledChanged(String sessionId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyShuffleModeEnabledChanged();
          });
    }

    @Override
    public void notifyRepeatModeChanged(String sessionId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyRepeatModeChanged();
          });
    }

    @Override
    public void notifySeekBackIncrementChanged(String sessionId, long seekBackIncrementMs)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.seekBackIncrementMs = seekBackIncrementMs;
            player.notifySeekBackIncrementChanged();
          });
    }

    @Override
    public void notifySeekForwardIncrementChanged(String sessionId, long seekForwardIncrementMs)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.seekForwardIncrementMs = seekForwardIncrementMs;
            player.notifySeekForwardIncrementChanged();
          });
    }

    @Override
    public void notifyVideoSizeChanged(String sessionId, Bundle videoSize) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            VideoSize videoSizeObj = VideoSize.fromBundle(videoSize);
            player.notifyVideoSizeChanged(videoSizeObj);
          });
    }

    @Override
    public boolean surfaceExists(String sessionId) throws RemoteException {
      return runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            return player.surfaceExists();
          });
    }

    @Override
    public void notifyVolumeChanged(String sessionId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyVolumeChanged();
          });
    }

    @Override
    public void notifyDeviceVolumeChanged(String sessionId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyDeviceVolumeChanged();
          });
    }

    @Override
    public void notifyCuesChanged(String sessionId, Bundle cueGroupBundle) throws RemoteException {
      CueGroup cueGroup = CueGroup.fromBundle(cueGroupBundle);
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.cueGroup = cueGroup;
            player.notifyCuesChanged();
          });
    }

    @Override
    public void notifyDeviceInfoChanged(String sessionId, Bundle deviceInfoBundle)
        throws RemoteException {
      DeviceInfo deviceInfo = DeviceInfo.fromBundle(deviceInfoBundle);
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.deviceInfo = deviceInfo;
            player.notifyDeviceInfoChanged();
          });
    }

    @Override
    public void notifyMediaMetadataChanged(String sessionId, Bundle mediaMetadataBundle)
        throws RemoteException {
      MediaMetadata mediaMetadata = MediaMetadata.fromBundle(mediaMetadataBundle);
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.mediaMetadata = mediaMetadata;
            player.notifyMediaMetadataChanged();
          });
    }

    @Override
    public void notifyRenderedFirstFrame(String sessionId) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyRenderedFirstFrame();
          });
    }

    @Override
    public void notifyMaxSeekToPreviousPositionChanged(
        String sessionId, long maxSeekToPreviousPositionMs) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.maxSeekToPreviousPositionMs = maxSeekToPreviousPositionMs;
            player.notifyMaxSeekToPreviousPositionChanged();
          });
    }

    @Override
    public void notifyTrackSelectionParametersChanged(String sessionId, Bundle parametersBundle)
        throws RemoteException {
      TrackSelectionParameters parameters = TrackSelectionParameters.fromBundle(parametersBundle);
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.trackSelectionParameters = parameters;
            player.notifyTrackSelectionParametersChanged();
          });
    }

    @Override
    public void notifyTracksChanged(String sessionId, Bundle tracksBundle) throws RemoteException {
      Tracks tracks = Tracks.fromBundle(tracksBundle);
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.currentTracks = tracks;
            player.notifyTracksChanged();
          });
    }
  }
}
