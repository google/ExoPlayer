/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.media3.transformer.mh.performance;

import static androidx.media3.common.MimeTypes.VIDEO_H264;
import static androidx.media3.common.Player.STATE_ENDED;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888Image;
import static androidx.media3.test.utils.BitmapPixelTestUtil.createArgb8888BitmapFromRgba8888ImageBuffer;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Effect;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.ConditionVariable;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.TextOverlay;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.util.EventLogger;
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Test for {@link ExoPlayer#setVideoEffects}. */
@RunWith(Enclosed.class)
public class EffectPlaybackTest {

  private static final String TEST_DIRECTORY = "test-generated-goldens/ExoPlayerPlaybackTest";
  private static final String MP4_ASSET_URI_STRING = "asset:///media/mp4/sample.mp4";
  private static final Format MP4_ASSET_FORMAT =
      new Format.Builder()
          .setSampleMimeType(VIDEO_H264)
          .setWidth(1080)
          .setHeight(720)
          .setFrameRate(29.97f)
          .setCodecs("avc1.64001F")
          .build();
  private static final int MP4_ASSET_FRAMES = 30;
  private static final Size MP4_ASSET_VIDEO_SIZE =
      new Size(MP4_ASSET_FORMAT.width, MP4_ASSET_FORMAT.height);
  private static final long TEST_TIMEOUT_MS = 10_000;

  /**
   * The test asserts the first frame is rendered for {@link Player#setPlayWhenReady playWhenReady}
   * is set to either {@code true} or {@code false}.
   */
  @RunWith(Parameterized.class)
  public static class RenderedFirstFrameTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private @MonotonicNonNull ExoPlayer player;
    private @MonotonicNonNull ImageReader outputImageReader;

    @Parameter public boolean playWhenReady;

    @Parameters(name = "playWhenReady={0}")
    public static ImmutableList<Boolean> parameters() {
      return ImmutableList.of(true, false);
    }

    @After
    public void tearDown() {
      instrumentation.runOnMainSync(() -> release(player, outputImageReader));
    }

    @Test
    public void exoplayerEffectsPreviewTest_ensuresFirstFrameRendered() throws Exception {
      assumeTrue(Util.SDK_INT >= 18);

      String testId =
          Util.formatInvariant(
              "exoplayerEffectsPreviewTest_withPlayWhenReady[%b]_ensuresFirstFrameRendered",
              playWhenReady);
      AtomicReference<Bitmap> renderedFirstFrameBitmap = new AtomicReference<>();
      ConditionVariable hasRenderedFirstFrameCondition = new ConditionVariable();
      outputImageReader =
          ImageReader.newInstance(
              MP4_ASSET_VIDEO_SIZE.getWidth(),
              MP4_ASSET_VIDEO_SIZE.getHeight(),
              PixelFormat.RGBA_8888,
              /* maxImages= */ 1);

      instrumentation.runOnMainSync(
          () -> {
            player = new ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();

            checkStateNotNull(outputImageReader);
            outputImageReader.setOnImageAvailableListener(
                imageReader -> {
                  try (Image image = imageReader.acquireLatestImage()) {
                    renderedFirstFrameBitmap.set(createArgb8888BitmapFromRgba8888Image(image));
                  }
                  hasRenderedFirstFrameCondition.open();
                },
                Util.createHandlerForCurrentOrMainLooper());

            setOutputSurfaceAndSizeOnPlayer(
                player, outputImageReader.getSurface(), MP4_ASSET_VIDEO_SIZE);

            player.setPlayWhenReady(playWhenReady);
            player.setVideoEffects(ImmutableList.of(createTimestampOverlay()));

            // Adding an EventLogger to use its log output in case the test fails.
            player.addAnalyticsListener(new EventLogger());
            player.setMediaItem(MediaItem.fromUri(MP4_ASSET_URI_STRING));
            player.prepare();
          });

      if (!hasRenderedFirstFrameCondition.block(TEST_TIMEOUT_MS)) {
        throw new TimeoutException(
            Util.formatInvariant("First frame not rendered in %d ms.", TEST_TIMEOUT_MS));
      }

      assertThat(renderedFirstFrameBitmap.get()).isNotNull();
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(
              /* expected= */ readBitmap(TEST_DIRECTORY + "/first_frame.png"),
              /* actual= */ renderedFirstFrameBitmap.get(),
              testId);
      assertThat(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
      // TODO: b/315800590 - Verify onFirstFrameRendered is invoked only once.
    }
  }

  /** Playback test for {@link Effect}-enabled playback. */
  public static class PlaybackTest {
    @Rule public final TestName testName = new TestName();
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private @MonotonicNonNull ExoPlayer player;
    private @MonotonicNonNull ImageReader outputImageReader;
    private @MonotonicNonNull String testId;

    @Before
    public void setUpTestId() {
      testId = testName.getMethodName();
    }

    @After
    public void tearDown() {
      instrumentation.runOnMainSync(() -> release(player, outputImageReader));
    }

    @Test
    public void exoplayerEffectsPreviewTest_ensuresAllFramesRendered() throws Exception {
      // Internal reference: b/264252759.
      assumeTrue(
          "This test should run on real devices because OpenGL to ImageReader rendering is not"
              + " always reliable on emulators.",
          !Util.isRunningOnEmulator());
      assumeTrue(Util.SDK_INT >= 18);

      ArrayList<BitmapPixelTestUtil.ImageBuffer> readImageBuffers = new ArrayList<>();
      AtomicInteger renderedFramesCount = new AtomicInteger();
      ConditionVariable playerEnded = new ConditionVariable();
      ConditionVariable readAllOutputFrames = new ConditionVariable();
      // Setting maxImages=5 ensures image reader gets all rendered frames from VideoFrameProcessor.
      // Using maxImages=5 runs successfully on a Pixel3.
      outputImageReader =
          ImageReader.newInstance(
              MP4_ASSET_VIDEO_SIZE.getWidth(),
              MP4_ASSET_VIDEO_SIZE.getHeight(),
              PixelFormat.RGBA_8888,
              /* maxImages= */ 5);

      instrumentation.runOnMainSync(
          () -> {
            player = new ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build();

            checkStateNotNull(outputImageReader);
            outputImageReader.setOnImageAvailableListener(
                imageReader -> {
                  try (Image image = imageReader.acquireNextImage()) {
                    readImageBuffers.add(
                        BitmapPixelTestUtil.copyByteBufferFromRbga8888Image(image));
                  }
                  if (renderedFramesCount.incrementAndGet() == MP4_ASSET_FRAMES) {
                    readAllOutputFrames.open();
                  }
                },
                Util.createHandlerForCurrentOrMainLooper());

            setOutputSurfaceAndSizeOnPlayer(
                player, outputImageReader.getSurface(), MP4_ASSET_VIDEO_SIZE);
            player.setPlayWhenReady(true);
            player.setVideoEffects(ImmutableList.of(createTimestampOverlay()));

            // Adding an EventLogger to use its log output in case the test fails.
            player.addAnalyticsListener(new EventLogger());
            player.addListener(
                new Player.Listener() {
                  @Override
                  public void onPlaybackStateChanged(@Player.State int playbackState) {
                    if (playbackState == STATE_ENDED) {
                      playerEnded.open();
                    }
                  }
                });
            player.setMediaItem(MediaItem.fromUri(MP4_ASSET_URI_STRING));
            player.prepare();
          });

      if (!playerEnded.block(TEST_TIMEOUT_MS)) {
        throw new TimeoutException(
            Util.formatInvariant("Playback not ended in %d ms.", TEST_TIMEOUT_MS));
      }

      if (!readAllOutputFrames.block(TEST_TIMEOUT_MS)) {
        throw new TimeoutException(
            Util.formatInvariant(
                "Haven't received all frames in %d ms after playback ends.", TEST_TIMEOUT_MS));
      }

      ArrayList<Float> averagePixelDifferences =
          new ArrayList<>(/* initialCapacity= */ readImageBuffers.size());
      for (int i = 0; i < readImageBuffers.size(); i++) {
        Bitmap actualBitmap = createArgb8888BitmapFromRgba8888ImageBuffer(readImageBuffers.get(i));
        float averagePixelAbsoluteDifference =
            getBitmapAveragePixelAbsoluteDifferenceArgb8888(
                /* expected= */ readBitmap(
                    Util.formatInvariant("%s/%s/frame_%d.png", TEST_DIRECTORY, testId, i)),
                /* actual= */ actualBitmap,
                /* testId= */ Util.formatInvariant("%s_frame_%d", testId, i));
        averagePixelDifferences.add(averagePixelAbsoluteDifference);
      }

      for (int i = 0; i < averagePixelDifferences.size(); i++) {
        float averagePixelDifference = averagePixelDifferences.get(i);
        assertWithMessage(
                Util.formatInvariant(
                    "Frame %d with average pixel difference %f. ", i, averagePixelDifference))
            .that(averagePixelDifference)
            .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
      }
    }
  }

  @Nullable
  private static MediaCodecVideoRenderer findVideoRenderer(ExoPlayer player) {
    for (int i = 0; i < player.getRendererCount(); i++) {
      if (player.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
        Renderer renderer = player.getRenderer(i);
        if (renderer instanceof MediaCodecVideoRenderer) {
          return (MediaCodecVideoRenderer) renderer;
        }
      }
    }
    return null;
  }

  private static void setOutputSurfaceAndSizeOnPlayer(
      ExoPlayer player, Surface outputSurface, Size outputSize) {
    // We need to access renderer directly because ExoPlayer.setVideoEffects() doesn't support
    // output to a Surface. When using ImageReader, we need to manually set output resolution on
    // the renderer directly.
    MediaCodecVideoRenderer videoRenderer = checkNotNull(findVideoRenderer(player));
    player
        .createMessage(videoRenderer)
        .setType(Renderer.MSG_SET_VIDEO_OUTPUT)
        .setPayload(outputSurface)
        .send();
    player
        .createMessage(videoRenderer)
        .setType(Renderer.MSG_SET_VIDEO_OUTPUT_RESOLUTION)
        .setPayload(outputSize)
        .send();
  }

  /** Creates an {@link OverlayEffect} that draws the timestamp onto frames. */
  private static OverlayEffect createTimestampOverlay() {
    return new OverlayEffect(
        ImmutableList.of(
            new TextOverlay() {
              @Override
              public SpannableString getText(long presentationTimeUs) {
                SpannableString text = new SpannableString(String.valueOf(presentationTimeUs));
                text.setSpan(
                    new ForegroundColorSpan(Color.WHITE),
                    /* start= */ 0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(
                    new AbsoluteSizeSpan(/* size= */ 96),
                    /* start= */ 0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(
                    new TypefaceSpan(/* family= */ "sans-serif"),
                    /* start= */ 0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                return text;
              }
            }));
  }

  private static void release(@Nullable Player player, @Nullable ImageReader imageReader) {
    if (player != null) {
      player.release();
    }
    if (imageReader != null) {
      imageReader.close();
    }
  }
}
