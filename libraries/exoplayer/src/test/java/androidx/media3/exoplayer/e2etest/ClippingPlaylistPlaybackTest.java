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
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2;
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

/** End-to-end tests for playlists with clipped media. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class ClippingPlaylistPlaybackTest {

  private static final String TEST_MP4_URI = "asset:///media/mp4/sample.mp4";

  @ParameterizedRobolectricTestRunner.Parameters(name = "{0}_{1}")
  public static List<ClippingConfig[]> configs() {
    return Sets.cartesianProduct(
            /* firstItemConfig */ ImmutableSet.of(
                new ClippingConfig("start", /* startMs= */ 0, /* endMs= */ 500),
                new ClippingConfig("middle", /* startMs= */ 300, /* endMs= */ 700),
                new ClippingConfig("end", /* startMs= */ 500, /* endMs= */ C.TIME_END_OF_SOURCE)),
            /* secondItemConfig */ ImmutableSet.of(
                new ClippingConfig("start", /* startMs= */ 0, /* endMs= */ 500),
                new ClippingConfig("middle", /* startMs= */ 300, /* endMs= */ 700),
                new ClippingConfig("end", /* startMs= */ 500, /* endMs= */ C.TIME_END_OF_SOURCE)))
        .stream()
        .map(s -> s.toArray(new ClippingConfig[0]))
        .collect(Collectors.toList());
  }

  @ParameterizedRobolectricTestRunner.Parameter(0)
  public ClippingConfig firstItemConfig;

  @ParameterizedRobolectricTestRunner.Parameter(1)
  public ClippingConfig secondItemConfig;

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void playbackWithClippingMediaSources() throws Exception {
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

    player.addMediaItem(
        new MediaItem.Builder()
            .setUri(TEST_MP4_URI)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(firstItemConfig.startMs)
                    .setEndPositionMs(firstItemConfig.endMs)
                    .build())
            .build());
    player.addMediaItem(
        new MediaItem.Builder()
            .setUri(TEST_MP4_URI)
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(secondItemConfig.startMs)
                    .setEndPositionMs(secondItemConfig.endMs)
                    .build())
            .build());
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/clipping/" + firstItemConfig.name + "_" + secondItemConfig.name + ".dump");
  }

  @Test
  public void playbackWithClippingMediaSourcesInConcatenatingMediaSource2() throws Exception {
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

    player.addMediaSource(
        new ConcatenatingMediaSource2.Builder()
            .useDefaultMediaSourceFactory(applicationContext)
            .add(
                new MediaItem.Builder()
                    .setUri(TEST_MP4_URI)
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(firstItemConfig.startMs)
                            .setEndPositionMs(firstItemConfig.endMs)
                            .build())
                    .build(),
                /* initialPlaceholderDurationMs= */ firstItemConfig.endMs == C.TIME_END_OF_SOURCE
                    ? 1
                    : C.TIME_UNSET)
            .add(
                new MediaItem.Builder()
                    .setUri(TEST_MP4_URI)
                    .setClippingConfiguration(
                        new MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(secondItemConfig.startMs)
                            .setEndPositionMs(secondItemConfig.endMs)
                            .build())
                    .build(),
                /* initialPlaceholderDurationMs= */ secondItemConfig.endMs == C.TIME_END_OF_SOURCE
                    ? 1
                    : C.TIME_UNSET)
            .build());
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    // Intentionally uses the same dump files as for the test above because the renderer output
    // should not be affected by combining all sources in a ConcatenatingMediaSource2.
    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/clipping/" + firstItemConfig.name + "_" + secondItemConfig.name + ".dump");
  }

  private static final class ClippingConfig {

    public final String name;
    public final long startMs;
    public final long endMs;

    public ClippingConfig(String name, long startMs, long endMs) {
      this.name = name;
      this.startMs = startMs;
      this.endMs = endMs;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
