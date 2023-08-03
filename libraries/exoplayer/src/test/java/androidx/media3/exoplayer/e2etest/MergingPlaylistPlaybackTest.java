/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.exoplayer.e2etest;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ClippingMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.FilteringMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.PlaybackOutput;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;

/** End-to-end tests for playlists with merged media. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class MergingPlaylistPlaybackTest {

  private static final String TEST_MP4_URI = "asset:///media/mp4/sample.mp4";

  @ParameterizedRobolectricTestRunner.Parameters(
      name = "videoPrimary({0}), first({1}_{2}), second({3}_{4})")
  public static List<Boolean[]> configs() {
    return Sets.cartesianProduct(
            /* videoIsPrimaryMergedSource */ ImmutableSet.of(true, false),
            /* firstItemVideoClipped */ ImmutableSet.of(true, false),
            /* firstItemAudioClipped */ ImmutableSet.of(true, false),
            /* secondItemVideoClipped */ ImmutableSet.of(true, false),
            /* secondItemAudioClipped */ ImmutableSet.of(true, false))
        .stream()
        .map(s -> s.toArray(new Boolean[0]))
        .collect(Collectors.toList());
  }

  @ParameterizedRobolectricTestRunner.Parameter(0)
  public Boolean videoIsPrimaryMergedSource;

  @ParameterizedRobolectricTestRunner.Parameter(1)
  public Boolean firstItemVideoClipped;

  @ParameterizedRobolectricTestRunner.Parameter(2)
  public Boolean firstItemAudioClipped;

  @ParameterizedRobolectricTestRunner.Parameter(3)
  public Boolean secondItemVideoClipped;

  @ParameterizedRobolectricTestRunner.Parameter(4)
  public Boolean secondItemAudioClipped;

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void transitionBetweenDifferentMergeConfigurations() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.addMediaSource(createMergingMediaSource(firstItemVideoClipped, firstItemAudioClipped));
    player.addMediaSource(createMergingMediaSource(secondItemVideoClipped, secondItemAudioClipped));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/merging/"
            + (videoIsPrimaryMergedSource ? "video" : "audio")
            + "_"
            + firstItemVideoClipped
            + "_"
            + firstItemAudioClipped
            + "_"
            + secondItemVideoClipped
            + "_"
            + secondItemAudioClipped
            + ".dump");
  }

  @Test
  public void multipleRepetitionsOfSameMergeConfiguration() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    for (int i = 0; i < 5; i++) {
      player.addMediaSource(createMergingMediaSource(firstItemVideoClipped, firstItemAudioClipped));
    }
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/merging/repeat_"
            + (videoIsPrimaryMergedSource ? "video" : "audio")
            + "_"
            + firstItemVideoClipped
            + "_"
            + firstItemAudioClipped
            + ".dump");
  }

  private MergingMediaSource createMergingMediaSource(boolean videoClipped, boolean audioClipped) {
    MediaSource.Factory mediaSourceFactory =
        new DefaultMediaSourceFactory((Context) ApplicationProvider.getApplicationContext());
    MediaSource videoSource =
        new FilteringMediaSource(
            mediaSourceFactory.createMediaSource(MediaItem.fromUri(TEST_MP4_URI)),
            C.TRACK_TYPE_VIDEO);
    MediaSource audioSource =
        new FilteringMediaSource(
            mediaSourceFactory.createMediaSource(MediaItem.fromUri(TEST_MP4_URI)),
            C.TRACK_TYPE_AUDIO);
    if (videoClipped) {
      videoSource =
          new ClippingMediaSource(
              videoSource, /* startPositionUs= */ 300_000, /* endPositionUs= */ 600_000);
    }
    if (audioClipped) {
      audioSource =
          new ClippingMediaSource(
              audioSource, /* startPositionUs= */ 500_000, /* endPositionUs= */ 800_000);
    }
    return new MergingMediaSource(
        /* adjustPeriodTimeOffsets= */ true,
        /* clipDurations= */ true,
        videoIsPrimaryMergedSource
            ? new MediaSource[] {videoSource, audioSource}
            : new MediaSource[] {audioSource, videoSource});
  }
}
