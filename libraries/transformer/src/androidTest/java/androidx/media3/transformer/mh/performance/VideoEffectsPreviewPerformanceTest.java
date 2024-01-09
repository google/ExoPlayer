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
package androidx.media3.transformer.mh.performance;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.transformer.AndroidTestUtil.MP4_ASSET_URI_STRING;
import static com.google.common.truth.Truth.assertThat;

import android.app.Instrumentation;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.view.Surface;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.transformer.PlayerTestListener;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Range;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Performance tests for the effects previewing pipeline in ExoPlayer. */
@RunWith(AndroidJUnit4.class)
public class VideoEffectsPreviewPerformanceTest {

  private static final long TEST_TIMEOUT_MS = 10_000;
  private static final long MEDIA_ITEM_CLIP_DURATION_MS = 500;

  private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
  private @MonotonicNonNull ExoPlayer player;

  @After
  public void tearDown() {
    instrumentation.runOnMainSync(
        () -> {
          if (player != null) {
            player.release();
          }
        });
  }

  /**
   * This test guards against performance regressions in the effects preview pipeline that format
   * switches do not cause the player to either stall or drop frames.
   */
  @Test
  public void exoplayerEffectsPreviewTest() throws PlaybackException, TimeoutException {
    PlayerTestListener listener = new PlayerTestListener(TEST_TIMEOUT_MS);
    instrumentation.runOnMainSync(
        () -> {
          player = new ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();
          // Set a surface on the player even though there is no UI on this test. We need a surface
          // otherwise the player will skip/drop video frames.
          player.setVideoSurface(new Surface(new SurfaceTexture(0)));
          player.setPlayWhenReady(false);
          player.setVideoEffects(ImmutableList.of());
          player.addListener(listener);
          player.addAnalyticsListener(listener);
          // Adding an EventLogger to use its log output in case the test fails.
          player.addAnalyticsListener(new EventLogger());
          MediaItem mediaItem = getClippedMediaItem(MP4_ASSET_URI_STRING);
          // Use the same media item so that format changes do not force exoplayer to re-init codecs
          // between item transitions.
          player.addMediaItems(ImmutableList.of(mediaItem, mediaItem, mediaItem, mediaItem));
          player.prepare();
        });

    listener.waitUntilPlayerReady();

    AtomicLong playbackStartTimeMs = new AtomicLong();
    instrumentation.runOnMainSync(
        () -> {
          playbackStartTimeMs.set(SystemClock.elapsedRealtime());
          checkNotNull(player).play();
        });

    listener.waitUntilPlayerEnded();
    long playbackDurationMs = SystemClock.elapsedRealtime() - playbackStartTimeMs.get();

    // Playback realtime should take 2 seconds, plus/minus error margin.
    assertThat(playbackDurationMs).isIn(Range.closed(1950L, 2060L));
    DecoderCounters decoderCounters = checkNotNull(listener.getDecoderCounters());
    assertThat(decoderCounters.droppedBufferCount).isEqualTo(0);
    assertThat(decoderCounters.skippedInputBufferCount).isEqualTo(0);
    assertThat(decoderCounters.skippedOutputBufferCount).isEqualTo(0);
  }

  private static MediaItem getClippedMediaItem(String uri) {
    return new MediaItem.Builder()
        .setUri(uri)
        .setClippingConfiguration(
            new MediaItem.ClippingConfiguration.Builder()
                .setEndPositionMs(MEDIA_ITEM_CLIP_DURATION_MS)
                .build())
        .build();
  }
}
