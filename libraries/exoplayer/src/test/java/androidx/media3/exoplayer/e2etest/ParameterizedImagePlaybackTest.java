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
package androidx.media3.exoplayer.e2etest;

import static com.google.common.truth.Truth.assertThat;
import static org.robolectric.annotation.GraphicsMode.Mode.NATIVE;

import android.content.Context;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.Clock;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.test.utils.CapturingRenderersFactory;
import androidx.media3.test.utils.DumpFileAsserts;
import androidx.media3.test.utils.FakeClock;
import androidx.media3.test.utils.robolectric.PlaybackOutput;
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper;
import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameter;
import org.robolectric.ParameterizedRobolectricTestRunner.Parameters;
import org.robolectric.annotation.GraphicsMode;

/** Parameterized end-to-end tests using image samples. */
@RunWith(ParameterizedRobolectricTestRunner.class)
@GraphicsMode(value = NATIVE)
public class ParameterizedImagePlaybackTest {
  @Parameter public Set<String> inputFiles;

  @Parameters(name = "{0}")
  public static List<Set<String>> mediaSamples() {
    // Robolectric's ShadowNativeBitmapFactory doesn't support decoding HEIF format, so we don't
    // test that here.
    // TODO b/300457060 - Find out why jpegs cause flaky failures in this test and then add jpegs to
    // this list if possible.
    return new ArrayList<>(
        Collections2.filter(
            Sets.powerSet(
                ImmutableSet.of(
                    "bitmap/input_images/media3test.png",
                    "bmp/non-motion-photo-shortened-cropped.bmp",
                    "png/non-motion-photo-shortened.png",
                    "webp/ic_launcher_round.webp")),
            /* predicate= */ input -> !input.isEmpty()));
  }

  @Test
  public void test() throws Exception {
    Context applicationContext = ApplicationProvider.getApplicationContext();
    CapturingRenderersFactory renderersFactory = new CapturingRenderersFactory(applicationContext);
    Clock clock = new FakeClock(/* isAutoAdvancing= */ true);
    ExoPlayer player =
        new ExoPlayer.Builder(applicationContext, renderersFactory).setClock(clock).build();
    PlaybackOutput playbackOutput = PlaybackOutput.register(player, renderersFactory);
    List<String> sortedInputFiles = new ArrayList<>(inputFiles);
    Collections.sort(sortedInputFiles);
    List<MediaItem> mediaItems = new ArrayList<>(inputFiles.size());
    long totalDurationMs = 0;
    long currentDurationMs = 3 * C.MILLIS_PER_SECOND;
    for (String inputFile : sortedInputFiles) {
      mediaItems.add(
          new MediaItem.Builder()
              .setUri("asset:///media/" + inputFile)
              .setImageDurationMs(currentDurationMs)
              .build());
      totalDurationMs += currentDurationMs;
      if (currentDurationMs < 5 * C.MILLIS_PER_SECOND) {
        currentDurationMs += C.MILLIS_PER_SECOND;
      }
    }
    player.setMediaItems(mediaItems);
    player.prepare();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY);
    long playerStartedMs = clock.elapsedRealtime();
    player.play();
    TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_ENDED);
    long playbackDurationMs = clock.elapsedRealtime() - playerStartedMs;
    player.release();
    assertThat(playbackDurationMs).isAtLeast(totalDurationMs);
    DumpFileAsserts.assertOutput(
        applicationContext,
        playbackOutput,
        "playbackdumps/image/" + generateName(sortedInputFiles) + ".dump");
  }

  private static String generateName(List<String> sortedInputFiles) {
    StringBuilder name = new StringBuilder();
    for (String inputFile : sortedInputFiles) {
      name.append(inputFile, inputFile.lastIndexOf("/") + 1, inputFile.length()).append("+");
    }
    name.setLength(name.length() - 1);
    return name.toString();
  }
}
