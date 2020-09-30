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
package com.google.android.exoplayer2.e2etest;

import android.graphics.SurfaceTexture;
import android.view.Surface;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.robolectric.PlaybackOutput;
import com.google.android.exoplayer2.robolectric.ShadowMediaCodecConfig;
import com.google.android.exoplayer2.testutil.AutoAdvancingFakeClock;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.TestExoPlayer;
import com.google.common.collect.ImmutableList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.Config;

/** End-to-end tests using TS samples. */
// TODO(b/143232359): Remove once https://issuetracker.google.com/143232359 is resolved.
@Config(sdk = 29)
@RunWith(ParameterizedRobolectricTestRunner.class)
public class TsPlaybackTest {

  @Parameters(name = "{0}")
  public static ImmutableList<String[]> params() {
    return ImmutableList.of(
        new String[] {"bbb_2500ms.ts"},
        new String[] {"elephants_dream.mpg"},
        new String[] {"sample.ac3"},
        new String[] {"sample_ac3.ts"},
        new String[] {"sample.ac4"},
        new String[] {"sample_ac4.ts"},
        new String[] {"sample.adts"},
        new String[] {"sample_ait.ts"},
        new String[] {"sample_cbs_truncated.adts"},
        new String[] {"sample.eac3"},
        new String[] {"sample_eac3joc.ec3"},
        new String[] {"sample_eac3joc.ts"},
        new String[] {"sample_eac3.ts"},
        new String[] {"sample_h262_mpeg_audio.ps"},
        new String[] {"sample_h262_mpeg_audio.ts"},
        new String[] {"sample_h263.ts"},
        new String[] {"sample_h264_dts_audio.ts"},
        new String[] {"sample_h264_mpeg_audio.ts"},
        new String[] {"sample_h264_no_access_unit_delimiters.ts"},
        new String[] {"sample_h265.ts"},
        new String[] {"sample_latm.ts"},
        new String[] {"sample_scte35.ts"},
        new String[] {"sample_with_id3.adts"},
        new String[] {"sample_with_junk"});
  }

  @Parameter public String inputFile;

  @Rule
  public ShadowMediaCodecConfig mediaCodecConfig =
      ShadowMediaCodecConfig.forAllSupportedMimeTypes();

  @Test
  public void test() throws Exception {
    SimpleExoPlayer player =
        new SimpleExoPlayer.Builder(ApplicationProvider.getApplicationContext())
            .setClock(new AutoAdvancingFakeClock())
            .build();
    player.setVideoSurface(new Surface(new SurfaceTexture(/* texName= */ 1)));
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, mediaCodecConfig);

    player.setMediaItem(MediaItem.fromUri("asset:///media/ts/" + inputFile));
    player.prepare();
    player.play();
    TestExoPlayer.runUntilPlaybackState(player, Player.STATE_ENDED);
    player.release();

    DumpFileAsserts.assertOutput(
        ApplicationProvider.getApplicationContext(),
        playbackOutput,
        "playbackdumps/ts/" + inputFile + ".dump");
  }
}
