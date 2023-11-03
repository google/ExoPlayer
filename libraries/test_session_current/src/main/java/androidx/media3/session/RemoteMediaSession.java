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
import static androidx.media3.test.session.common.CommonConstants.MEDIA3_SESSION_PROVIDER_SERVICE;
import static androidx.media3.test.session.common.TestUtils.SERVICE_CONNECTION_TIMEOUT_MS;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.DeviceInfo;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.Player.DiscontinuityReason;
import androidx.media3.common.Player.PositionInfo;
import androidx.media3.common.Timeline;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.Log;
import androidx.media3.test.session.common.IRemoteMediaSession;
import androidx.media3.test.session.common.TestUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Represents remote {@link MediaSession} in the service app's {@link MediaSessionProviderService}.
 * Users can run {@link MediaSession} methods remotely with this object.
 */
public class RemoteMediaSession {
  private static final String TAG = "RemoteMediaSession";

  private final Context context;
  private final String sessionId;
  private final Bundle tokenExtras;

  private ServiceConnection serviceConnection;
  private IRemoteMediaSession binder;
  private RemoteMockPlayer remotePlayer;
  private CountDownLatch countDownLatch;

  /** Create a {@link MediaSession} in the service app. Should NOT be called in main thread. */
  public RemoteMediaSession(String sessionId, Context context, @Nullable Bundle tokenExtras)
      throws RemoteException {
    this.sessionId = sessionId;
    this.context = context;
    countDownLatch = new CountDownLatch(1);
    serviceConnection = new MyServiceConnection();
    this.tokenExtras = tokenExtras;

    if (!connect()) {
      assertWithMessage("Failed to connect to the MediaSessionProviderService.").fail();
    }
    create();
  }

  public void cleanUp() {
    try {
      release();
      disconnect();
    } catch (RemoteException e) {
      // Maybe expected if cleanUp() is called already or service is crashed as expected.
    }
  }

  /**
   * Gets {@link RemoteMockPlayer} for interact with the remote MockPlayer. Users can run MockPlayer
   * methods remotely with this object.
   */
  public RemoteMockPlayer getMockPlayer() {
    return remotePlayer;
  }

  ////////////////////////////////////////////////////////////////////////////////
  // MediaSession methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Gets {@link SessionToken} from the service app. Should be used after the creation of the
   * session through {@link #create()}.
   *
   * @return A {@link SessionToken} object.
   */
  @Nullable
  public SessionToken getToken() throws RemoteException {
    return SessionToken.fromBundle(binder.getToken(sessionId));
  }

  /**
   * Gets {@link MediaSessionCompat.Token} from the service app. Should be used after the creation
   * of the session through {@link #create()}.
   *
   * @return A {@link SessionToken} object.
   */
  @Nullable
  public MediaSessionCompat.Token getCompatToken() throws RemoteException {
    Bundle bundle = binder.getCompatToken(sessionId);
    bundle.setClassLoader(MediaSession.class.getClassLoader());
    return MediaSessionCompat.Token.fromBundle(bundle);
  }

  public void setSessionPositionUpdateDelayMs(long updateDelayMs) throws RemoteException {
    binder.setSessionPositionUpdateDelayMs(sessionId, updateDelayMs);
  }

  public void setPlayer(Bundle config) throws RemoteException {
    binder.setPlayer(sessionId, config);
  }

  public void broadcastCustomCommand(SessionCommand command, Bundle args) throws RemoteException {
    binder.broadcastCustomCommand(sessionId, command.toBundle(), args);
  }

  public void sendCustomCommand(SessionCommand command, Bundle args) throws RemoteException {
    binder.sendCustomCommand(sessionId, command.toBundle(), args);
  }

  public void release() throws RemoteException {
    binder.release(sessionId);
  }

  public void setAvailableCommands(SessionCommands sessionCommands, Player.Commands playerCommands)
      throws RemoteException {
    binder.setAvailableCommands(sessionId, sessionCommands.toBundle(), playerCommands.toBundle());
  }

  public void setCustomLayout(List<CommandButton> layout) throws RemoteException {
    List<Bundle> bundleList = new ArrayList<>();
    for (CommandButton button : layout) {
      bundleList.add(button.toBundle());
    }
    binder.setCustomLayout(sessionId, bundleList);
  }

  public void setSessionExtras(Bundle extras) throws RemoteException {
    binder.setSessionExtras(sessionId, extras);
  }

  public void setSessionExtras(String controllerKey, Bundle extras) throws RemoteException {
    binder.setSessionExtrasForController(sessionId, controllerKey, extras);
  }

  public void setSessionActivity(PendingIntent sessionActivity) throws RemoteException {
    binder.setSessionActivity(sessionId, sessionActivity);
  }

  ////////////////////////////////////////////////////////////////////////////////
  // RemoteMockPlayer methods
  ////////////////////////////////////////////////////////////////////////////////

  /** RemoteMockPlayer */
  public class RemoteMockPlayer {

    public void notifyPlayerError(@Nullable PlaybackException playerError) throws RemoteException {
      binder.notifyPlayerError(sessionId, playerError == null ? null : playerError.toBundle());
    }

    public void setPlayWhenReady(
        boolean playWhenReady, @Player.PlaybackSuppressionReason int reason)
        throws RemoteException {
      binder.setPlayWhenReady(sessionId, playWhenReady, reason);
    }

    public void setPlaybackState(@Player.State int state) throws RemoteException {
      binder.setPlaybackState(sessionId, state);
    }

    public void setCurrentPosition(long pos) throws RemoteException {
      binder.setCurrentPosition(sessionId, pos);
    }

    public void setBufferedPosition(long pos) throws RemoteException {
      binder.setBufferedPosition(sessionId, pos);
    }

    public void setDuration(long duration) throws RemoteException {
      binder.setDuration(sessionId, duration);
    }

    public void setBufferedPercentage(int bufferedPercentage) throws RemoteException {
      binder.setBufferedPercentage(sessionId, bufferedPercentage);
    }

    public void setTotalBufferedDuration(long totalBufferedDuration) throws RemoteException {
      binder.setTotalBufferedDuration(sessionId, totalBufferedDuration);
    }

    public void setCurrentLiveOffset(long currentLiveOffset) throws RemoteException {
      binder.setCurrentLiveOffset(sessionId, currentLiveOffset);
    }

    public void setContentDuration(long contentDuration) throws RemoteException {
      binder.setContentDuration(sessionId, contentDuration);
    }

    public void setContentPosition(long contentPosition) throws RemoteException {
      binder.setContentPosition(sessionId, contentPosition);
    }

    public void setContentBufferedPosition(long contentBufferedPosition) throws RemoteException {
      binder.setContentBufferedPosition(sessionId, contentBufferedPosition);
    }

    public void setPlaybackParameters(PlaybackParameters playbackParameters)
        throws RemoteException {
      binder.setPlaybackParameters(sessionId, playbackParameters.toBundle());
    }

    public void setIsPlayingAd(boolean isPlayingAd) throws RemoteException {
      binder.setIsPlayingAd(sessionId, isPlayingAd);
    }

    public void setCurrentAdGroupIndex(int currentAdGroupIndex) throws RemoteException {
      binder.setCurrentAdGroupIndex(sessionId, currentAdGroupIndex);
    }

    public void setCurrentAdIndexInAdGroup(int currentAdIndexInAdGroup) throws RemoteException {
      binder.setCurrentAdIndexInAdGroup(sessionId, currentAdIndexInAdGroup);
    }

    public void setVolume(float volume) throws RemoteException {
      binder.setVolume(sessionId, volume);
    }

    public void setDeviceVolume(int volume, @C.VolumeFlags int flags) throws RemoteException {
      binder.setDeviceVolume(sessionId, volume, flags);
    }

    public void decreaseDeviceVolume(@C.VolumeFlags int flags) throws RemoteException {
      binder.decreaseDeviceVolume(sessionId, flags);
    }

    public void increaseDeviceVolume(@C.VolumeFlags int flags) throws RemoteException {
      binder.increaseDeviceVolume(sessionId, flags);
    }

    public void setDeviceMuted(boolean muted, @C.VolumeFlags int flags) throws RemoteException {
      binder.setDeviceMuted(sessionId, muted, flags);
    }

    public void notifyPlayWhenReadyChanged(
        boolean playWhenReady, @Player.PlaybackSuppressionReason int reason)
        throws RemoteException {
      binder.notifyPlayWhenReadyChanged(sessionId, playWhenReady, reason);
    }

    public void notifyPlaybackStateChanged(@Player.State int state) throws RemoteException {
      binder.notifyPlaybackStateChanged(sessionId, state);
    }

    public void notifyIsLoadingChanged(boolean isLoading) throws RemoteException {
      binder.notifyIsLoadingChanged(sessionId, isLoading);
    }

    public void notifyPositionDiscontinuity(
        PositionInfo oldPosition, PositionInfo newPosition, @DiscontinuityReason int reason)
        throws RemoteException {
      binder.notifyPositionDiscontinuity(
          sessionId, oldPosition.toBundle(), newPosition.toBundle(), reason);
    }

    public void notifyPlaybackParametersChanged(PlaybackParameters playbackParameters)
        throws RemoteException {
      binder.notifyPlaybackParametersChanged(sessionId, playbackParameters.toBundle());
    }

    public void notifyMediaItemTransition(int index, @Player.MediaItemTransitionReason int reason)
        throws RemoteException {
      binder.notifyMediaItemTransition(sessionId, index, reason);
    }

    public void notifyAudioAttributesChanged(AudioAttributes audioAttributes)
        throws RemoteException {
      binder.notifyAudioAttributesChanged(sessionId, audioAttributes.toBundle());
    }

    public void notifyAvailableCommandsChanged(Player.Commands commands) throws RemoteException {
      binder.notifyAvailableCommandsChanged(sessionId, commands.toBundle());
    }

    public void setTimeline(Timeline timeline) throws RemoteException {
      binder.setTimeline(sessionId, timeline.toBundle());
    }

    /**
     * Service app will automatically create a timeline of size {@code windowCount}, and sets it to
     * the player.
     *
     * <p>Each item's media ID will be {@link TestUtils#getMediaIdInFakeTimeline(int)}.
     */
    public void createAndSetFakeTimeline(int windowCount) throws RemoteException {
      binder.createAndSetFakeTimeline(sessionId, windowCount);
    }

    public void setPlaylistMetadata(MediaMetadata playlistMetadata) throws RemoteException {
      binder.setPlaylistMetadata(sessionId, playlistMetadata.toBundle());
    }

    public void setRepeatMode(@Player.RepeatMode int repeatMode) throws RemoteException {
      binder.setRepeatMode(sessionId, repeatMode);
    }

    public void setShuffleModeEnabled(boolean shuffleModeEnabled) throws RemoteException {
      binder.setShuffleModeEnabled(sessionId, shuffleModeEnabled);
    }

    public void setCurrentMediaItemIndex(int index) throws RemoteException {
      binder.setCurrentMediaItemIndex(sessionId, index);
    }

    public void setTrackSelectionParameters(TrackSelectionParameters parameters)
        throws RemoteException {
      binder.setTrackSelectionParameters(sessionId, parameters.toBundle());
    }

    public void setMediaMetadata(MediaMetadata mediaMetadata) throws RemoteException {
      binder.setMediaMetadata(sessionId, mediaMetadata.toBundle());
    }

    public void notifyTimelineChanged(@Player.TimelineChangeReason int reason)
        throws RemoteException {
      binder.notifyTimelineChanged(sessionId, reason);
    }

    public void notifyPlaylistMetadataChanged() throws RemoteException {
      binder.notifyPlaylistMetadataChanged(sessionId);
    }

    public void notifyShuffleModeEnabledChanged() throws RemoteException {
      binder.notifyShuffleModeEnabledChanged(sessionId);
    }

    public void notifyRepeatModeChanged() throws RemoteException {
      binder.notifyRepeatModeChanged(sessionId);
    }

    public void notifySeekBackIncrementChanged(long seekBackIncrementMs) throws RemoteException {
      binder.notifySeekBackIncrementChanged(sessionId, seekBackIncrementMs);
    }

    public void notifySeekForwardIncrementChanged(long seekForwardIncrementMs)
        throws RemoteException {
      binder.notifySeekForwardIncrementChanged(sessionId, seekForwardIncrementMs);
    }

    public void notifyVideoSizeChanged(VideoSize videoSize) throws RemoteException {
      binder.notifyVideoSizeChanged(sessionId, videoSize.toBundle());
    }

    public boolean surfaceExists() throws RemoteException {
      return binder.surfaceExists(sessionId);
    }

    public void notifyDeviceVolumeChanged() throws RemoteException {
      binder.notifyDeviceVolumeChanged(sessionId);
    }

    public void notifyVolumeChanged() throws RemoteException {
      binder.notifyVolumeChanged(sessionId);
    }

    public void notifyCuesChanged(CueGroup cueGroup) throws RemoteException {
      binder.notifyCuesChanged(sessionId, cueGroup.toBundle());
    }

    public void notifyDeviceInfoChanged(DeviceInfo deviceInfo) throws RemoteException {
      binder.notifyDeviceInfoChanged(sessionId, deviceInfo.toBundle());
    }

    public void notifyMediaMetadataChanged(MediaMetadata mediaMetadata) throws RemoteException {
      binder.notifyMediaMetadataChanged(sessionId, mediaMetadata.toBundle());
    }

    public void notifyRenderedFirstFrame() throws RemoteException {
      binder.notifyRenderedFirstFrame(sessionId);
    }

    public void notifyMaxSeekToPreviousPositionChanged(long maxSeekToPreviousPositionMs)
        throws RemoteException {
      binder.notifyMaxSeekToPreviousPositionChanged(sessionId, maxSeekToPreviousPositionMs);
    }

    public void notifyTrackSelectionParametersChanged(TrackSelectionParameters parameters)
        throws RemoteException {
      binder.notifyTrackSelectionParametersChanged(sessionId, parameters.toBundle());
    }

    public void notifyTracksChanged(Tracks tracks) throws RemoteException {
      binder.notifyTracksChanged(sessionId, tracks.toBundle());
    }
  }

  ////////////////////////////////////////////////////////////////////////////////
  // Non-public methods
  ////////////////////////////////////////////////////////////////////////////////

  /**
   * Connects to service app's MediaSessionProviderService. Should NOT be called in main thread.
   *
   * @return true if connected successfully, false if failed to connect.
   */
  private boolean connect() {
    Intent intent = new Intent(ACTION_MEDIA3_SESSION);
    intent.setComponent(MEDIA3_SESSION_PROVIDER_SERVICE);

    boolean bound = false;
    try {
      bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    } catch (RuntimeException e) {
      Log.e(TAG, "Failed binding to the MediaSessionProviderService of the service app", e);
    }

    if (bound) {
      try {
        countDownLatch.await(SERVICE_CONNECTION_TIMEOUT_MS, MILLISECONDS);
      } catch (InterruptedException e) {
        Log.e(TAG, "InterruptedException while waiting for onServiceConnected.", e);
      }
    }
    return binder != null;
  }

  /** Disconnects from service app's MediaSessionProviderService. */
  private void disconnect() {
    if (serviceConnection != null) {
      context.unbindService(serviceConnection);
    }
    serviceConnection = null;
  }

  /**
   * Create a {@link MediaSession} in the service app. Should be used after successful connection
   * through {@link #connect}.
   */
  private void create() throws RemoteException {
    binder.create(sessionId, tokenExtras);
    remotePlayer = new RemoteMockPlayer();
  }

  class MyServiceConnection implements ServiceConnection {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      Log.d(TAG, "Connected to service app's MediaSessionProviderService.");
      binder = IRemoteMediaSession.Stub.asInterface(service);
      countDownLatch.countDown();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
      Log.d(TAG, "Disconnected from the service.");
    }
  }

  /**
   * Builder to build a {@link Bundle} which represents a configuration of {@link Player} in order
   * to create a new mock player in the service app. The bundle can be passed to {@link
   * #setPlayer(Bundle)}.
   */
  public static final class MockPlayerConfigBuilder {

    private final Bundle bundle;

    public MockPlayerConfigBuilder() {
      bundle = new Bundle();
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setPlayerError(@Nullable PlaybackException playerError) {
      if (playerError != null) {
        bundle.putBundle(KEY_PLAYER_ERROR, playerError.toBundle());
      }
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setDuration(long duration) {
      bundle.putLong(KEY_DURATION, duration);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setCurrentPosition(long pos) {
      bundle.putLong(KEY_CURRENT_POSITION, pos);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setBufferedPosition(long buffPos) {
      bundle.putLong(KEY_BUFFERED_POSITION, buffPos);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setBufferedPercentage(int bufferedPercentage) {
      bundle.putInt(KEY_BUFFERED_PERCENTAGE, bufferedPercentage);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setTotalBufferedDuration(long totalBufferedDuration) {
      bundle.putLong(KEY_TOTAL_BUFFERED_DURATION, totalBufferedDuration);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setCurrentLiveOffset(long currentLiveOffset) {
      bundle.putLong(KEY_CURRENT_LIVE_OFFSET, currentLiveOffset);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setContentDuration(long contentDuration) {
      bundle.putLong(KEY_CONTENT_DURATION, contentDuration);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setContentPosition(long contentPosition) {
      bundle.putLong(KEY_CONTENT_POSITION, contentPosition);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setContentBufferedPosition(long contentBufferedPosition) {
      bundle.putLong(KEY_CONTENT_BUFFERED_POSITION, contentBufferedPosition);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setIsPlayingAd(boolean isPlayingAd) {
      bundle.putBoolean(KEY_IS_PLAYING_AD, isPlayingAd);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setCurrentAdGroupIndex(int currentAdGroupIndex) {
      bundle.putInt(KEY_CURRENT_AD_GROUP_INDEX, currentAdGroupIndex);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setCurrentAdIndexInAdGroup(int currentAdIndexInAdGroup) {
      bundle.putInt(KEY_CURRENT_AD_INDEX_IN_AD_GROUP, currentAdIndexInAdGroup);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setPlaybackParameters(PlaybackParameters playbackParameters) {
      bundle.putBundle(KEY_PLAYBACK_PARAMETERS, playbackParameters.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setAudioAttributes(AudioAttributes audioAttributes) {
      bundle.putBundle(KEY_AUDIO_ATTRIBUTES, audioAttributes.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setTimeline(Timeline timeline) {
      bundle.putBundle(KEY_TIMELINE, timeline.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setCurrentMediaItemIndex(int index) {
      bundle.putInt(KEY_CURRENT_MEDIA_ITEM_INDEX, index);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setCurrentPeriodIndex(int index) {
      bundle.putInt(KEY_CURRENT_PERIOD_INDEX, index);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setPlaylistMetadata(MediaMetadata playlistMetadata) {
      bundle.putBundle(KEY_PLAYLIST_METADATA, playlistMetadata.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setVideoSize(VideoSize videoSize) {
      bundle.putBundle(KEY_VIDEO_SIZE, videoSize.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setVolume(float volume) {
      bundle.putFloat(KEY_VOLUME, volume);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setCurrentCues(CueGroup cueGroup) {
      bundle.putBundle(KEY_CURRENT_CUE_GROUP, cueGroup.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setDeviceInfo(DeviceInfo deviceInfo) {
      bundle.putBundle(KEY_DEVICE_INFO, deviceInfo.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setDeviceVolume(int volume) {
      bundle.putInt(KEY_DEVICE_VOLUME, volume);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setDeviceMuted(boolean muted) {
      bundle.putBoolean(KEY_DEVICE_MUTED, muted);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setPlayWhenReady(boolean playWhenReady) {
      bundle.putBoolean(KEY_PLAY_WHEN_READY, playWhenReady);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setPlaybackSuppressionReason(
        @Player.PlaybackSuppressionReason int playbackSuppressionReason) {
      bundle.putInt(KEY_PLAYBACK_SUPPRESSION_REASON, playbackSuppressionReason);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setPlaybackState(@Player.State int state) {
      bundle.putInt(KEY_PLAYBACK_STATE, state);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setIsLoading(boolean isLoading) {
      bundle.putBoolean(KEY_IS_LOADING, isLoading);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setRepeatMode(@Player.RepeatMode int repeatMode) {
      bundle.putInt(KEY_REPEAT_MODE, repeatMode);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setShuffleModeEnabled(boolean shuffleModeEnabled) {
      bundle.putBoolean(KEY_SHUFFLE_MODE_ENABLED, shuffleModeEnabled);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setSeekBackIncrement(long seekBackIncrementMs) {
      bundle.putLong(KEY_SEEK_BACK_INCREMENT_MS, seekBackIncrementMs);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setSeekForwardIncrement(long seekForwardIncrementMs) {
      bundle.putLong(KEY_SEEK_FORWARD_INCREMENT_MS, seekForwardIncrementMs);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setMediaMetadata(MediaMetadata mediaMetadata) {
      bundle.putBundle(KEY_MEDIA_METADATA, mediaMetadata.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setMaxSeekToPreviousPositionMs(
        long maxSeekToPreviousPositionMs) {
      bundle.putLong(KEY_MAX_SEEK_TO_PREVIOUS_POSITION_MS, maxSeekToPreviousPositionMs);
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setTrackSelectionParameters(
        TrackSelectionParameters parameters) {
      bundle.putBundle(KEY_TRACK_SELECTION_PARAMETERS, parameters.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setCurrentTracks(Tracks tracks) {
      bundle.putBundle(KEY_CURRENT_TRACKS, tracks.toBundle());
      return this;
    }

    @CanIgnoreReturnValue
    public MockPlayerConfigBuilder setAvailableCommands(Player.Commands availableCommands) {
      bundle.putBundle(KEY_AVAILABLE_COMMANDS, availableCommands.toBundle());
      return this;
    }

    public Bundle build() {
      return bundle;
    }
  }
}
