/*
 * Copyright (C) 2020 The Android Open Source Project
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
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.PlaybackOutput;
import androidx.media3.test.utils.robolectric.ShadowMediaCodecConfig;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;

/** End-to-end tests using MKV samples. */
@RunWith(ParameterizedRobolectricTestRunner.class)
public final class MkvPlaybackTest {
  @Parameters(name = "{0}")
  public static ImmutableList<String> mediaSamples() {
    return ImmutableList.of(
        "sample.mkv",
        "sample_with_htc_rotation_track_name.mkv",
        "sample_with_ssa_subtitles.mkv",
        "sample_with_null_terminated_ssa_subtitles.mkv",
        "sample_with_overlapping_ssa_subtitles.mkv",
        "sample_with_srt.mkv",
        "sample_with_null_terminated_srt.mkv",
        "sample_with_overlapping_srt.mkv",
        "sample_with_vtt_subtitles.mkv",
        "sample_with_null_terminated_vtt_subtitles.mkv");
  }

  @ParameterizedRobolectricTestRunner.Parameter public String inputFile;

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void test() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory capturingRenderersFactory =
        new CapturingRenderersFactory(applicationContext);
    // TODO: b/289916598 - Remove this when transcoding is the default.
    DefaultExtractorsFactory extractorsFactory =
        new DefaultExtractorsFactory().setTextTrackTranscodingEnabled(true);
    DefaultMediaSourceFactory mediaSourceFactory =
        new DefaultMediaSourceFactory(applicationContext, extractorsFactory);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, capturingRenderersFactory, mediaSourceFactory)
            .setClock(new FakeClock(/* isAutoAdvancing= */ true))
            .build();
    Surface surface = new Surface(new SurfaceTexture(/* texName= */ 1));
    player.setVideoSurface(surface);
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, capturingRenderersFactory);

    player.setMediaItem(MediaItem.fromUri("asset:///media/mkv/" + inputFile));
    player.prepare();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();
    surface.release();

    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/mkv/" + inputFile + ".dump");
  }
}
