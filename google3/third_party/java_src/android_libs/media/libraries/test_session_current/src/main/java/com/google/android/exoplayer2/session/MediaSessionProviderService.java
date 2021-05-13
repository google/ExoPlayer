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

package com.google.android.exoplayer2.session;

import static com.google.android.exoplayer2.session.vct.common.CommonConstants.ACTION_MEDIA2_SESSION;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_AUDIO_ATTRIBUTES;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_BUFFERED_PERCENTAGE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_BUFFERED_POSITION;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CONTENT_BUFFERED_POSITION;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CONTENT_DURATION;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CONTENT_POSITION;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CURRENT_AD_GROUP_INDEX;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CURRENT_AD_INDEX_IN_AD_GROUP;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CURRENT_LIVE_OFFSET;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CURRENT_PERIOD_INDEX;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CURRENT_POSITION;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_CURRENT_WINDOW_INDEX;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_DEVICE_INFO;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_DEVICE_MUTED;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_DEVICE_VOLUME;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_DURATION;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_IS_LOADING;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_IS_PLAYING;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_IS_PLAYING_AD;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_MEDIA_ITEM;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_PLAYBACK_PARAMETERS;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_PLAYBACK_STATE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_PLAYBACK_SUPPRESSION_REASON;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_PLAYER_ERROR;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_PLAYLIST_METADATA;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_PLAY_WHEN_READY;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_REPEAT_MODE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_SHUFFLE_MODE_ENABLED;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_TIMELINE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_TOTAL_BUFFERED_DURATION;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_VIDEO_SIZE;
import static com.google.android.exoplayer2.session.vct.common.CommonConstants.KEY_VOLUME;
import static com.google.android.exoplayer2.session.vct.common.MediaSessionConstants.TEST_CONTROLLER_CALLBACK_SESSION_REJECTS;
import static com.google.android.exoplayer2.session.vct.common.MediaSessionConstants.TEST_GET_SESSION_ACTIVITY;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.MediaMetadata;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Player.DiscontinuityReason;
import com.google.android.exoplayer2.Player.PositionInfo;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.device.DeviceInfo;
import com.google.android.exoplayer2.session.MediaSession.ControllerInfo;
import com.google.android.exoplayer2.session.vct.common.IRemoteMediaSession;
import com.google.android.exoplayer2.session.vct.common.MockActivity;
import com.google.android.exoplayer2.session.vct.common.TestHandler;
import com.google.android.exoplayer2.session.vct.common.TestHandler.TestRunnable;
import com.google.android.exoplayer2.session.vct.common.TestUtils;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.video.VideoSize;
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
  private static final String TAG = "MediaSessionProviderService";

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
    if (ACTION_MEDIA2_SESSION.equals(intent.getAction())) {
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

    private void runOnHandler(@NonNull TestRunnable runnable) throws RemoteException {
      try {
        handler.postAndSync(runnable);
      } catch (Exception e) {
        Log.e(TAG, "Exception thrown while waiting for handler", e);
        throw new RemoteException("Unexpected exception");
      }
    }

    private <V> V runOnHandler(@NonNull Callable<V> callable) throws RemoteException {
      try {
        return handler.postAndSync(callable);
      } catch (Exception e) {
        Log.e(TAG, "Exception thrown while waiting for handler", e);
        throw new RemoteException("Unexpected exception");
      }
    }

    @Override
    public void create(String sessionId, Bundle tokenExtras) throws RemoteException {
      MediaSession.Builder builder =
          new MediaSession.Builder(
                  MediaSessionProviderService.this,
                  new MockPlayer.Builder().setApplicationLooper(handler.getLooper()).build())
              .setId(sessionId);

      if (tokenExtras != null) {
        builder.setExtras(tokenExtras);
      }

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
                    /* flags= */ 0);
            builder.setSessionActivity(pendingIntent);
            break;
          }
        case TEST_CONTROLLER_CALLBACK_SESSION_REJECTS:
          {
            builder.setSessionCallback(
                new MediaSession.SessionCallback() {
                  @Nullable
                  @Override
                  public MediaSession.ConnectResult onConnect(
                      MediaSession session, ControllerInfo controller) {
                    return null;
                  }
                });
            break;
          }
      }

      runOnHandler(
          () -> {
            MediaSession session = builder.build();
            session.setSessionPositionUpdateDelayMs(0L);
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
    public void setPlayer(String sessionId, @NonNull Bundle config) throws RemoteException {
      runOnHandler(
          () -> {
            config.setClassLoader(MediaSession.class.getClassLoader());
            MediaSession session = sessionMap.get(sessionId);
            session.setPlayer(createMockPlayer(config));
          });
    }

    private SessionPlayer createMockPlayer(Bundle config) {
      MockPlayer player = new MockPlayer.Builder().build();
      player.playerError =
          BundleableUtils.fromNullableBundle(
              ExoPlaybackException.CREATOR, config.getBundle(KEY_PLAYER_ERROR), player.playerError);
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
      player.playbackParameters =
          BundleableUtils.fromNullableBundle(
              PlaybackParameters.CREATOR,
              config.getBundle(KEY_PLAYBACK_PARAMETERS),
              player.playbackParameters);
      player.timeline =
          BundleableUtils.fromNullableBundle(
              Timeline.CREATOR, config.getBundle(KEY_TIMELINE), player.timeline);
      player.currentWindowIndex =
          config.getInt(KEY_CURRENT_WINDOW_INDEX, player.currentWindowIndex);
      player.currentPeriodIndex =
          config.getInt(KEY_CURRENT_PERIOD_INDEX, player.currentPeriodIndex);
      player.currentMediaItem =
          BundleableUtils.fromNullableBundle(
              MediaItem.CREATOR, config.getBundle(KEY_MEDIA_ITEM), player.currentMediaItem);
      player.playlistMetadata =
          BundleableUtils.fromNullableBundle(
              MediaMetadata.CREATOR,
              config.getBundle(KEY_PLAYLIST_METADATA),
              player.playlistMetadata);
      player.videoSize =
          BundleableUtils.fromNullableBundle(
              VideoSize.CREATOR, config.getBundle(KEY_VIDEO_SIZE), player.videoSize);
      player.volume = config.getFloat(KEY_VOLUME, player.volume);
      player.audioAttributes =
          BundleableUtils.fromNullableBundle(
              AudioAttributes.CREATOR,
              config.getBundle(KEY_AUDIO_ATTRIBUTES),
              player.audioAttributes);
      player.deviceInfo =
          BundleableUtils.fromNullableBundle(
              DeviceInfo.CREATOR, config.getBundle(KEY_DEVICE_INFO), player.deviceInfo);
      player.deviceVolume = config.getInt(KEY_DEVICE_VOLUME, player.deviceVolume);
      player.deviceMuted = config.getBoolean(KEY_DEVICE_MUTED, player.deviceMuted);
      player.playWhenReady = config.getBoolean(KEY_PLAY_WHEN_READY, player.playWhenReady);
      player.playbackSuppressionReason =
          config.getInt(KEY_PLAYBACK_SUPPRESSION_REASON, player.playbackSuppressionReason);
      player.playbackState = config.getInt(KEY_PLAYBACK_STATE, player.playbackState);
      player.isPlaying = config.getBoolean(KEY_IS_PLAYING, player.isPlaying);
      player.isLoading = config.getBoolean(KEY_IS_LOADING, player.isLoading);
      player.repeatMode = config.getInt(KEY_REPEAT_MODE, player.repeatMode);
      player.shuffleModeEnabled =
          config.getBoolean(KEY_SHUFFLE_MODE_ENABLED, player.shuffleModeEnabled);
      return player;
    }

    @Override
    public void broadcastCustomCommand(String sessionId, Bundle command, Bundle args)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            session.broadcastCustomCommand(SessionCommand.CREATOR.fromBundle(command), args);
          });
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void sendCustomCommand(String sessionId, Bundle controller, Bundle command, Bundle args)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            ControllerInfo info = MediaTestUtils.getTestControllerInfo(session);
            session.sendCustomCommand(info, SessionCommand.CREATOR.fromBundle(command), args);
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
        String sessionId, Bundle controller, Bundle sessionCommands, Bundle playerCommands)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            ControllerInfo info = MediaTestUtils.getTestControllerInfo(session);
            session.setAvailableCommands(
                info,
                SessionCommands.CREATOR.fromBundle(sessionCommands),
                Player.Commands.CREATOR.fromBundle(playerCommands));
          });
    }

    @Override
    @SuppressWarnings("FutureReturnValueIgnored")
    public void setCustomLayout(String sessionId, Bundle controller, List<Bundle> layout)
        throws RemoteException {
      if (layout == null) {
        return;
      }
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            ControllerInfo info = MediaTestUtils.getTestControllerInfo(session);
            List<CommandButton> buttons = new ArrayList<>();
            for (Bundle bundle : layout) {
              buttons.add(CommandButton.CREATOR.fromBundle(bundle));
            }
            session.setCustomLayout(info, buttons);
          });
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
            ExoPlaybackException playerError =
                BundleableUtils.fromNullableBundle(
                    ExoPlaybackException.CREATOR, playerErrorBundle, player.playerError);
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
          PlaybackParameters.CREATOR.fromBundle(playbackParametersBundle);
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
    public void notifyIsPlayingChanged(String sessionId, boolean isPlaying) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.notifyIsPlayingChanged(isPlaying);
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
                PositionInfo.CREATOR.fromBundle(oldPositionBundle),
                PositionInfo.CREATOR.fromBundle(newPositionBundle),
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
                PlaybackParameters.CREATOR.fromBundle(playbackParametersBundle));
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
            player.currentMediaItem = mediaItem;
            player.notifyMediaItemTransition(mediaItem, reason);
          });
    }

    @Override
    public void notifyAudioAttributesChanged(
        @NonNull String sessionId, @NonNull Bundle audioAttributesBundle) throws RemoteException {
      AudioAttributes audioAttributes = AudioAttributes.CREATOR.fromBundle(audioAttributesBundle);
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
            player.timeline = Timeline.CREATOR.fromBundle(timelineBundle);
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
                  MediaTestUtils.createConvergedMediaItem(
                      TestUtils.getMediaIdInFakeTimeline(windowIndex)));
            }
            player.timeline = new PlaylistTimeline(mediaItems);
          });
    }

    @Override
    public void setPlaylistMetadata(String sessionId, Bundle playlistMetadataBundle)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.playlistMetadata = MediaMetadata.CREATOR.fromBundle(playlistMetadataBundle);
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
    public void setCurrentWindowIndex(String sessionId, int index) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.currentWindowIndex = index;
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
                BundleableUtils.fromNullableBundle(
                    Player.Commands.CREATOR, commandsBundle, Player.Commands.EMPTY));
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
    public void notifyVideoSizeChanged(String sessionId, Bundle videoSize) throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            VideoSize videoSizeObj = VideoSize.CREATOR.fromBundle(videoSize);
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
    public void notifyDeviceVolumeChanged(String sessionId, int volume, boolean muted)
        throws RemoteException {
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.deviceVolume = volume;
            player.deviceMuted = muted;
            player.notifyDeviceVolumeChanged();
          });
    }

    @Override
    public void notifyDeviceInfoChanged(String sessionId, @NonNull Bundle deviceInfoBundle)
        throws RemoteException {
      DeviceInfo deviceInfo = DeviceInfo.CREATOR.fromBundle(deviceInfoBundle);
      runOnHandler(
          () -> {
            MediaSession session = sessionMap.get(sessionId);
            MockPlayer player = (MockPlayer) session.getPlayer();
            player.deviceInfo = deviceInfo;
            player.notifyDeviceInfoChanged();
          });
    }
  }
}
