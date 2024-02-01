/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.google.android.exoplayer2.source.hls.e2etest;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SeekParameters;
import com.google.android.exoplayer2.robolectric.PlaybackOutput;
import com.google.android.exoplayer2.robolectric.ShadowMediaCodecConfig;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.testutil.CapturingRenderersFactory;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/** End-to-end tests using HLS samples. */
@RunWith(AndroidJUnit4.class)
public final class HlsPlaybackTest {

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void webvttStandaloneSubtitlesFile() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(
                new HlsMediaSource.Factory(new DefaultDataSource.Factory(applicationContext))
                    .experimentalParseSubtitlesDuringExtraction(true))
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(
        MediaItem.fromUri("asset:///media/hls/standalone-webvtt/multivariant_playlist.m3u8"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/hls/standalone-webvtt.dump");
  }

  @Test
  public void ttmlInMp4() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(
                new HlsMediaSource.Factory(new DefaultDataSource.Factory(applicationContext))
                    .experimentalParseSubtitlesDuringExtraction(true))
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(
        MediaItem.fromUri("asset:///media/hls/ttml-in-mp4/multivariant_playlist.m3u8"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/hls/ttml-in-mp4.dump");
  }

  /**
   * This test and {@link #cea608_parseDuringExtraction()} use the same output dump file, to
   * demonstrate the flag has no effect on the resulting subtitles.
   */
  @Test
  public void cea608_parseDuringRendering() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(
                new HlsMediaSource.Factory(new DefaultDataSource.Factory(applicationContext))
                    .experimentalParseSubtitlesDuringExtraction(false))
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/hls/cea608/manifest.m3u8"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/hls/cea608.dump");
  }

  /**
   * This test and {@link #cea608_parseDuringRendering()} use the same output dump file, to
   * demonstrate the flag has no effect on the resulting subtitles.
   */
  @Test
  public void cea608_parseDuringExtraction() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(
                new HlsMediaSource.Factory(new DefaultDataSource.Factory(applicationContext))
                    .experimentalParseSubtitlesDuringExtraction(true))
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/hls/cea608/manifest.m3u8"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/hls/cea608.dump");
  }

  @Test
  public void multiSegment_withSeekToPrevSyncFrame_startsRenderingAtBeginningOfSegment()
      throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setMediaSourceFactory(
                new HlsMediaSource.Factory(new DefaultDataSource.Factory(applicationContext))
                    .experimentalParseSubtitlesDuringExtraction(true))
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .setLoadControl(
                new DefaultLoadControl.Builder()
                    .setBackBuffer(
                        /* backBufferDurationMs= */ 10000, /* retainBackBufferFromKeyframe= */ true)
                    .build())
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);
    // Play media fully (with back buffer) to ensure we have all the segment data available.
    player.setMediaItem(MediaItem.fromUri("asset:///media/hls/multi-segment/playlist.m3u8"));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);

    // Seek to beginning of second segment (at 500ms according to playlist)
    player.setSeekParameters(SeekParameters.PREVIOUS_SYNC);
    player.seekTo(600);
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    // Output only starts at 550ms (the first sample in the second segment)
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/hls/multi-segment-with-seek.dump");
  }
}
