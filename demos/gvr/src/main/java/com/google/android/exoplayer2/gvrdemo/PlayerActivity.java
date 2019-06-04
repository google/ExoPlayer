/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.google.android.exoplayer2.gvrdemo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.widget.Toast;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.C.ContentType;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackPreparer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.gvr.GvrPlayerActivity;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector.MappedTrackInfo;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.EventLogger;
import com.google.android.exoplayer2.util.Util;

/** An activity that plays media using {@link SimpleExoPlayer}. */
public class PlayerActivity extends GvrPlayerActivity implements PlaybackPreparer {

  public static final String EXTENSION_EXTRA = "extension";

  public static final String SPHERICAL_STEREO_MODE_EXTRA = "spherical_stereo_mode";
  public static final String SPHERICAL_STEREO_MODE_MONO = "mono";
  public static final String SPHERICAL_STEREO_MODE_TOP_BOTTOM = "top_bottom";
  public static final String SPHERICAL_STEREO_MODE_LEFT_RIGHT = "left_right";

  private DataSource.Factory dataSourceFactory;
  private SimpleExoPlayer player;
  private MediaSource mediaSource;
  private DefaultTrackSelector trackSelector;
  private TrackGroupArray lastSeenTrackGroupArray;

  private boolean startAutoPlay;
  private int startWindow;
  private long startPosition;

  // Activity lifecycle

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    String userAgent = Util.getUserAgent(this, "ExoPlayerDemo");
    dataSourceFactory =
        new DefaultDataSourceFactory(this, new DefaultHttpDataSourceFactory(userAgent));

    String sphericalStereoMode = getIntent().getStringExtra(SPHERICAL_STEREO_MODE_EXTRA);
    if (sphericalStereoMode != null) {
      int stereoMode;
      if (SPHERICAL_STEREO_MODE_MONO.equals(sphericalStereoMode)) {
        stereoMode = C.STEREO_MODE_MONO;
      } else if (SPHERICAL_STEREO_MODE_TOP_BOTTOM.equals(sphericalStereoMode)) {
        stereoMode = C.STEREO_MODE_TOP_BOTTOM;
      } else if (SPHERICAL_STEREO_MODE_LEFT_RIGHT.equals(sphericalStereoMode)) {
        stereoMode = C.STEREO_MODE_LEFT_RIGHT;
      } else {
        showToast(R.string.error_unrecognized_stereo_mode);
        finish();
        return;
      }
      setDefaultStereoMode(stereoMode);
    }

    clearStartPosition();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (Util.SDK_INT <= 23 || player == null) {
      initializePlayer();
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    if (Util.SDK_INT <= 23) {
      releasePlayer();
    }
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  // PlaybackControlView.PlaybackPreparer implementation

  @Override
  public void preparePlayback() {
    initializePlayer();
  }

  // Internal methods

  private void initializePlayer() {
    if (player == null) {
      Intent intent = getIntent();
      Uri uri = intent.getData();
      if (!Util.checkCleartextTrafficPermitted(uri)) {
        showToast(R.string.error_cleartext_not_permitted);
        return;
      }

      DefaultRenderersFactory renderersFactory = new DefaultRenderersFactory(this);

      trackSelector = new DefaultTrackSelector(new AdaptiveTrackSelection.Factory());
      lastSeenTrackGroupArray = null;

      player =
          ExoPlayerFactory.newSimpleInstance(/* context= */ this, renderersFactory, trackSelector);
      player.addListener(new PlayerEventListener());
      player.setPlayWhenReady(startAutoPlay);
      player.addAnalyticsListener(new EventLogger(trackSelector));
      setPlayer(player);

      mediaSource = buildMediaSource(uri, intent.getStringExtra(EXTENSION_EXTRA));
    }
    boolean haveStartPosition = startWindow != C.INDEX_UNSET;
    if (haveStartPosition) {
      player.seekTo(startWindow, startPosition);
    }
    player.prepare(mediaSource, !haveStartPosition, false);
  }

  private MediaSource buildMediaSource(Uri uri, @Nullable String overrideExtension) {
    @ContentType int type = Util.inferContentType(uri, overrideExtension);
    switch (type) {
      case C.TYPE_DASH:
        return new DashMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
      case C.TYPE_SS:
        return new SsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
      case C.TYPE_HLS:
        return new HlsMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
      case C.TYPE_OTHER:
        return new ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(uri);
      default:
        throw new IllegalStateException("Unsupported type: " + type);
    }
  }

  private void releasePlayer() {
    if (player != null) {
      updateStartPosition();
      player.release();
      player = null;
      mediaSource = null;
      trackSelector = null;
    }
  }

  private void updateStartPosition() {
    if (player != null) {
      startAutoPlay = player.getPlayWhenReady();
      startWindow = player.getCurrentWindowIndex();
      startPosition = Math.max(0, player.getContentPosition());
    }
  }

  private void clearStartPosition() {
    startAutoPlay = true;
    startWindow = C.INDEX_UNSET;
    startPosition = C.TIME_UNSET;
  }

  private void showToast(int messageId) {
    showToast(getString(messageId));
  }

  private void showToast(String message) {
    Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
  }

  private class PlayerEventListener implements Player.EventListener {

    @Override
    public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {}

    @Override
    public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
      if (player.getPlaybackError() != null) {
        // The user has performed a seek whilst in the error state. Update the resume position so
        // that if the user then retries, playback resumes from the position to which they seeked.
        updateStartPosition();
      }
    }

    @Override
    public void onPlayerError(ExoPlaybackException e) {
      updateStartPosition();
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
      if (trackGroups != lastSeenTrackGroupArray) {
        MappedTrackInfo mappedTrackInfo = trackSelector.getCurrentMappedTrackInfo();
        if (mappedTrackInfo != null) {
          if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_VIDEO)
              == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
            showToast(R.string.error_unsupported_video);
          }
          if (mappedTrackInfo.getTypeSupport(C.TRACK_TYPE_AUDIO)
              == MappedTrackInfo.RENDERER_SUPPORT_UNSUPPORTED_TRACKS) {
            showToast(R.string.error_unsupported_audio);
          }
        }
        lastSeenTrackGroupArray = trackGroups;
      }
    }
  }
}
