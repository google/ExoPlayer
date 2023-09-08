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

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.annotation.GraphicsMode.Mode.NATIVE;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.robolectric.PlaybackOutput;
import com.google.android.exoplayer2.robolectric.TestPlayerRunHelper;
import com.google.android.exoplayer2.testutil.CapturingRenderersFactory;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.util.Clock;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.GraphicsMode;

/** End-to-end tests using image samples. */
@RunWith(ParameterizedRobolectricTestRunner.class)
@GraphicsMode(value = NATIVE)
public class ImagePlaybackTest {

  @Parameter public String inputFile;

  @Parameters(name = "{0}")
  public static ImmutableList<String> mediaSamples() {
    // TODO(b/289989736): When extraction for other types of images is implemented, add those image
    //   types to this list.
    // Robolectric's NativeShadowBitmapFactory doesn't support decoding HEIF format, so we don't
    // test that format here.
    return ImmutableList.of(
        "png/non-motion-photo-shortened.png", "jpeg/non-motion-photo-shortened.jpg");
  }

  @Test
  public void test() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory renderersFactory =
        new CapturingRenderersFactory(applicationContext, /* addImageRenderer= */ true);
    Clock clock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory).setClock(clock).build();
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);
    long durationMs = 5 * C.MILLIS_PER_SECOND;
    player.setMediaItem(
        new MediaItem.Builder()
            .setUri("asset:///media/" + inputFile)
            .setImageDurationMs(durationMs)
            .build());
    player.prepare();

    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long playerStartedMs = clock.elapsedRealtime();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    long playbackDurationMs = clock.elapsedRealtime() - playerStartedMs;
    player.release();

    assertThat(playbackDurationMs).isEqualTo(durationMs);
    DumpFileAsserts.assertOutput(
        applicationContext, playbackOutput, "playbackdumps/" + inputFile + ".dump");
  }
}
