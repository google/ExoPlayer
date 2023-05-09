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
package androidx.media3.test.session.common;

import android.os.Bundle;
import android.os.ResultReceiver;

interface IRemoteMediaSession {

  void create(String sessionId, in Bundle tokenExtras);

  // MediaSession Methods
  Bundle getToken(String sessionId);
  Bundle getCompatToken(String sessionId);
  void setSessionPositionUpdateDelayMs(String sessionId, long updateDelayMs);
  void setPlayer(String sessionId, in Bundle playerBundle);
  void broadcastCustomCommand(String sessionId, in Bundle command, in Bundle args);
  void sendCustomCommand(String sessionId, in Bundle command, in Bundle args);
  void release(String sessionId);
  void setAvailableCommands(String sessionId, in Bundle sessionCommands, in Bundle playerCommands);
  void setCustomLayout(String sessionId, in List<Bundle> layout);
  void setSessionExtras(String sessionId, in Bundle extras);
  void setSessionExtrasForController(String sessionId, in String controllerKey, in Bundle extras);
  void setSessionActivity(String sessionId, in PendingIntent sessionActivity);

  // Player Methods
  void setPlayWhenReady(String sessionId, boolean playWhenReady, int reason);
  void setPlaybackState(String sessionId, int state);
  void setCurrentPosition(String sessionId, long pos);
  void setBufferedPosition(String sessionId, long pos);
  void setDuration(String sessionId, long duration);
  void setBufferedPercentage(String sessionId, int bufferedPercentage);
  void setTotalBufferedDuration(String sessionId, long totalBufferedDuration);
  void setCurrentLiveOffset(String sessionId, long currentLiveOffset);
  void setContentDuration(String sessionId, long contentDuration);
  void setContentPosition(String sessionId, long contentPosition);
  void setContentBufferedPosition(String sessionId, long contentBufferedPosition);
  void setPlaybackParameters(String sessionId, in Bundle playbackParametersBundle);
  void setIsPlayingAd(String sessionId, boolean isPlayingAd);
  void setCurrentAdGroupIndex(String sessionId, int currentAdGroupIndex);
  void setCurrentAdIndexInAdGroup(String sessionId, int currentAdIndexInAdGroup);
  void setVolume(String sessionId, float volume);
  void setDeviceVolume(String sessionId, int volume, int flags);
  void decreaseDeviceVolume(String sessionId, int flags);
  void increaseDeviceVolume(String sessionId, int flags);
  void setDeviceMuted(String sessionId, boolean muted, int flags);
  void notifyPlayerError(String sessionId, in Bundle playerErrorBundle);
  void notifyPlayWhenReadyChanged(String sessionId, boolean playWhenReady, int reason);
  void notifyPlaybackStateChanged(String sessionId, int state);
  void notifyIsLoadingChanged(String sessionId, boolean isLoading);
  void notifyPositionDiscontinuity(String sessionId,
      in Bundle oldPositionBundle, in Bundle newPositionBundle, int reason);
  void notifyPlaybackParametersChanged(String sessionId, in Bundle playbackParametersBundle);
  void notifyMediaItemTransition(String sessionId, int index, int reason);
  void notifyAudioAttributesChanged(String sessionId, in Bundle audioAttributes);
  void notifyVideoSizeChanged(String sessionId, in Bundle videoSize);
  void notifyAvailableCommandsChanged(String sessionId, in Bundle commandsBundle);
  boolean surfaceExists(String sessionId);

  void setTimeline(String sessionId, in Bundle timeline);
  void createAndSetFakeTimeline(String sessionId, int windowCount);
  void setMediaMetadata(String sessionId, in Bundle metadata);
  void setPlaylistMetadata(String sessionId, in Bundle metadata);
  void setShuffleModeEnabled(String sessionId, boolean shuffleMode);
  void setRepeatMode(String sessionId, int repeatMode);
  void setCurrentMediaItemIndex(String sessionId, int index);
  void setTrackSelectionParameters(String sessionId, in Bundle parameters);
  void notifyTimelineChanged(String sessionId, int reason);
  void notifyPlaylistMetadataChanged(String sessionId);
  void notifyShuffleModeEnabledChanged(String sessionId);
  void notifyRepeatModeChanged(String sessionId);
  void notifySeekBackIncrementChanged(String sessionId, long seekBackIncrementMs);
  void notifySeekForwardIncrementChanged(String sessionId, long seekForwardIncrementMs);
  void notifyDeviceVolumeChanged(String sessionId);
  void notifyVolumeChanged(String sessionId);
  void notifyCuesChanged(String sessionId, in Bundle cueGroup);
  void notifyDeviceInfoChanged(String sessionId, in Bundle deviceInfo);
  void notifyMediaMetadataChanged(String sessionId, in Bundle mediaMetadata);
  void notifyRenderedFirstFrame(String sessionId);
  void notifyMaxSeekToPreviousPositionChanged(String sessionid, long maxSeekToPreviousPositionMs);
  void notifyTrackSelectionParametersChanged(String sessionId, in Bundle parameters);
  void notifyTracksChanged(String sessionId, in Bundle tracks);
}
