/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.google.android.exoplayer2.castdemo;

import android.content.Context;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.View;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.RenderersFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ext.cast.CastPlayer;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlaybackControlView;
import com.google.android.exoplayer2.ui.SimpleExoPlayerView;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.framework.CastContext;

/**
 * Manages players for the ExoPlayer/Cast integration app.
 */
/* package */ final class PlayerManager implements CastPlayer.SessionAvailabilityListener {

  private static final int PLAYBACK_REMOTE = 1;
  private static final int PLAYBACK_LOCAL = 2;

  private static final String USER_AGENT = "ExoCastDemoPlayer";
  private static final DefaultBandwidthMeter BANDWIDTH_METER = new DefaultBandwidthMeter();
  private static final DefaultHttpDataSourceFactory DATA_SOURCE_FACTORY =
      new DefaultHttpDataSourceFactory(USER_AGENT, BANDWIDTH_METER);

  private final SimpleExoPlayerView exoPlayerView;
  private final PlaybackControlView castControlView;
  private final CastContext castContext;
  private final SimpleExoPlayer exoPlayer;
  private final CastPlayer castPlayer;

  private int playbackLocation;
  private CastDemoUtil.Sample currentSample;

  /**
   * @param exoPlayerView The {@link SimpleExoPlayerView} for local playback.
   * @param castControlView The {@link PlaybackControlView} to control remote playback.
   * @param context A {@link Context}.
   */
  public PlayerManager(SimpleExoPlayerView exoPlayerView, PlaybackControlView castControlView,
      Context context) {
    this.exoPlayerView = exoPlayerView;
    this.castControlView = castControlView;
    castContext = CastContext.getSharedInstance(context);

    DefaultTrackSelector trackSelector = new DefaultTrackSelector(BANDWIDTH_METER);
    RenderersFactory renderersFactory = new DefaultRenderersFactory(context, null);
    exoPlayer = ExoPlayerFactory.newSimpleInstance(renderersFactory, trackSelector);
    exoPlayerView.setPlayer(exoPlayer);

    castPlayer = new CastPlayer(castContext);
    castPlayer.setSessionAvailabilityListener(this);
    castControlView.setPlayer(castPlayer);

    setPlaybackLocation(castPlayer.isCastSessionAvailable() ? PLAYBACK_REMOTE : PLAYBACK_LOCAL);
  }

  /**
   * Starts playback of the given sample at the given position.
   *
   * @param currentSample The {@link CastDemoUtil} to play.
   * @param positionMs The position at which playback should start.
   * @param playWhenReady Whether the player should proceed when ready to do so.
   */
  public void setCurrentSample(CastDemoUtil.Sample currentSample, long positionMs,
      boolean playWhenReady) {
    this.currentSample = currentSample;
    if (playbackLocation == PLAYBACK_REMOTE) {
      castPlayer.loadItem(buildMediaQueueItem(currentSample), positionMs);
      castPlayer.setPlayWhenReady(playWhenReady);
    } else /* playbackLocation == PLAYBACK_LOCAL */ {
      exoPlayer.prepare(buildMediaSource(currentSample), true, true);
      exoPlayer.setPlayWhenReady(playWhenReady);
      exoPlayer.seekTo(positionMs);
    }
  }

  /**
   * Dispatches a given {@link KeyEvent} to whichever view corresponds according to the current
   * playback location.
   *
   * @param event The {@link KeyEvent}.
   * @return Whether the event was handled by the target view.
   */
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (playbackLocation == PLAYBACK_REMOTE) {
      return castControlView.dispatchKeyEvent(event);
    } else /* playbackLocation == PLAYBACK_REMOTE */ {
      return exoPlayerView.dispatchKeyEvent(event);
    }
  }

  /**
   * Releases the manager and the players that it holds.
   */
  public void release() {
    castPlayer.setSessionAvailabilityListener(null);
    castPlayer.release();
    exoPlayerView.setPlayer(null);
    exoPlayer.release();
  }

  // CastPlayer.SessionAvailabilityListener implementation.

  @Override
  public void onCastSessionAvailable() {
    setPlaybackLocation(PLAYBACK_REMOTE);
  }

  @Override
  public void onCastSessionUnavailable() {
    setPlaybackLocation(PLAYBACK_LOCAL);
  }

  // Internal methods.

  private static MediaQueueItem buildMediaQueueItem(CastDemoUtil.Sample sample) {
    MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE);
    movieMetadata.putString(MediaMetadata.KEY_TITLE, sample.name);
    MediaInfo mediaInfo = new MediaInfo.Builder(sample.uri)
        .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED).setContentType(sample.mimeType)
        .setMetadata(movieMetadata).build();
    return new MediaQueueItem.Builder(mediaInfo).build();
  }

  private static MediaSource buildMediaSource(CastDemoUtil.Sample sample) {
    Uri uri = Uri.parse(sample.uri);
    switch (sample.mimeType) {
      case CastDemoUtil.MIME_TYPE_SS:
        return new SsMediaSource(uri, DATA_SOURCE_FACTORY,
            new DefaultSsChunkSource.Factory(DATA_SOURCE_FACTORY), null, null);
      case CastDemoUtil.MIME_TYPE_DASH:
        return new DashMediaSource(uri, DATA_SOURCE_FACTORY,
            new DefaultDashChunkSource.Factory(DATA_SOURCE_FACTORY), null, null);
      case CastDemoUtil.MIME_TYPE_HLS:
        return new HlsMediaSource(uri, DATA_SOURCE_FACTORY, null, null);
      case CastDemoUtil.MIME_TYPE_VIDEO_MP4:
        return new ExtractorMediaSource(uri, DATA_SOURCE_FACTORY, new DefaultExtractorsFactory(),
            null, null);
      default: {
        throw new IllegalStateException("Unsupported type: " + sample.mimeType);
      }
    }
  }

  private void setPlaybackLocation(int playbackLocation) {
    if (this.playbackLocation == playbackLocation) {
      return;
    }

    // View management.
    if (playbackLocation == PLAYBACK_LOCAL) {
      exoPlayerView.setVisibility(View.VISIBLE);
      castControlView.hide();
    } else {
      exoPlayerView.setVisibility(View.GONE);
      castControlView.show();
    }

    long playbackPositionMs;
    boolean playWhenReady;
    if (this.playbackLocation == PLAYBACK_LOCAL) {
      playbackPositionMs = exoPlayer.getCurrentPosition();
      playWhenReady = exoPlayer.getPlayWhenReady();
      exoPlayer.stop();
    } else /* this.playbackLocation == PLAYBACK_REMOTE */ {
      playbackPositionMs = castPlayer.getCurrentPosition();
      playWhenReady = castPlayer.getPlayWhenReady();
      castPlayer.stop();
    }

    this.playbackLocation = playbackLocation;
    if (currentSample != null) {
      setCurrentSample(currentSample, playbackPositionMs, playWhenReady);
    }
  }

}
