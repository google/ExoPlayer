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

package com.google.android.exoplayer2.session.vct.common;

import android.content.ComponentName;

/** Common constants for testing purpose. */
public class CommonConstants {

  public static final String SUPPORT_APP_PACKAGE_NAME = "com.google.android.exoplayer2.session.vct";

  public static final ComponentName MEDIA2_SESSION_PROVIDER_SERVICE =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME,
          "com.google.android.exoplayer2.session.MediaSessionProviderService");
  public static final ComponentName MEDIA2_CONTROLLER_PROVIDER_SERVICE =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME,
          "com.google.android.exoplayer2.session.MediaControllerProviderService");

  public static final ComponentName MEDIA_SESSION_COMPAT_PROVIDER_SERVICE =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME,
          "com.google.android.exoplayer2.session.MediaSessionCompatProviderService");
  public static final ComponentName MEDIA_CONTROLLER_COMPAT_PROVIDER_SERVICE =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME,
          "com.google.android.exoplayer2.session.MediaControllerCompatProviderService");
  public static final ComponentName MEDIA_BROWSER_COMPAT_PROVIDER_SERVICE =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME,
          "com.google.android.exoplayer2.session.MediaBrowserCompatProviderService");

  public static final ComponentName MOCK_MEDIA2_SESSION_SERVICE =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME,
          "com.google.android.exoplayer2.session.MockMediaSessionService");
  public static final ComponentName MOCK_MEDIA2_LIBRARY_SERVICE =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME,
          "com.google.android.exoplayer2.session.MockMediaLibraryService");
  public static final ComponentName MOCK_MEDIA_BROWSER_SERVICE_COMPAT =
      new ComponentName(
          SUPPORT_APP_PACKAGE_NAME,
          "com.google.android.exoplayer2.session.MockMediaBrowserServiceCompat");

  public static final String ACTION_MEDIA2_SESSION =
      "com.google.android.exoplayer2.session.vct.action.MEDIA2_SESSION";
  public static final String ACTION_MEDIA2_CONTROLLER =
      "com.google.android.exoplayer2.session.vct.action.MEDIA2_CONTROLLER";
  public static final String ACTION_MEDIA_SESSION_COMPAT =
      "com.google.android.exoplayer2.session.vct.action.MEDIA_SESSION_COMPAT";
  public static final String ACTION_MEDIA_CONTROLLER_COMPAT =
      "com.google.android.exoplayer2.session.vct.action.MEDIA_CONTROLLER_COMPAT";
  public static final String ACTION_MEDIA_BROWSER_COMPAT =
      "com.google.android.exoplayer2.session.vct.action.MEDIA_BROWSER_COMPAT";

  // Keys for arguments.
  public static final String KEY_PLAYER_ERROR = "playerError";
  public static final String KEY_AUDIO_ATTRIBUTES = "audioAttributes";
  public static final String KEY_TIMELINE = "timeline";
  public static final String KEY_CURRENT_WINDOW_INDEX = "currentWindowIndex";
  public static final String KEY_CURRENT_PERIOD_INDEX = "currentPeriodIndex";
  public static final String KEY_DURATION = "duration";
  public static final String KEY_CURRENT_POSITION = "currentPosition";
  public static final String KEY_BUFFERED_POSITION = "bufferedPosition";
  public static final String KEY_BUFFERED_PERCENTAGE = "bufferedPercentage";
  public static final String KEY_TOTAL_BUFFERED_DURATION = "totalBufferedDuration";
  public static final String KEY_CURRENT_LIVE_OFFSET = "currentLiveOffset";
  public static final String KEY_CONTENT_DURATION = "contentDuration";
  public static final String KEY_CONTENT_POSITION = "contentPosition";
  public static final String KEY_CONTENT_BUFFERED_POSITION = "contentBufferedPosition";
  public static final String KEY_PLAYBACK_PARAMETERS = "playbackParameters";
  public static final String KEY_MEDIA_ITEM = "mediaItem";
  public static final String KEY_PLAYLIST_METADATA = "playlistMetadata";
  public static final String KEY_ARGUMENTS = "arguments";
  public static final String KEY_DEVICE_INFO = "deviceInfo";
  public static final String KEY_DEVICE_VOLUME = "deviceVolume";
  public static final String KEY_DEVICE_MUTED = "deviceMuted";
  public static final String KEY_VIDEO_SIZE = "videoSize";
  public static final String KEY_VOLUME = "volume";
  public static final String KEY_PLAY_WHEN_READY = "playWhenReady";
  public static final String KEY_PLAYBACK_SUPPRESSION_REASON = "playbackSuppressionReason";
  public static final String KEY_PLAYBACK_STATE = "playbackState";
  public static final String KEY_IS_PLAYING = "isPlaying";
  public static final String KEY_IS_LOADING = "isLoading";
  public static final String KEY_REPEAT_MODE = "repeatMode";
  public static final String KEY_SHUFFLE_MODE_ENABLED = "shuffleModeEnabled";
  public static final String KEY_IS_PLAYING_AD = "isPlayingAd";
  public static final String KEY_CURRENT_AD_GROUP_INDEX = "currentAdGroupIndex";
  public static final String KEY_CURRENT_AD_INDEX_IN_AD_GROUP = "currentAdIndexInAdGroup";

  // SessionCompat arguments
  public static final String KEY_SESSION_COMPAT_TOKEN = "sessionCompatToken";
  public static final String KEY_PLAYBACK_STATE_COMPAT = "playbackStateCompat";
  public static final String KEY_METADATA_COMPAT = "metadataCompat";
  public static final String KEY_QUEUE = "queue";

  // Default test name
  public static final String DEFAULT_TEST_NAME = "defaultTestName";

  private CommonConstants() {}
}
