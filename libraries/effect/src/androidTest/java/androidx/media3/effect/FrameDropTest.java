/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.effect;

import static androidx.media3.common.VideoFrameProcessor.INPUT_TYPE_SURFACE;
import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.TextureBitmapReader;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Tests for {@link FrameDropEffect}. */
@RunWith(AndroidJUnit4.class)
public class FrameDropTest {
  @Rule public final TestName testName = new TestName();

  private static final String ASSET_PATH = "media/bitmap/FrameDropTest";
  private static final int BLANK_FRAME_WIDTH = 100;
  private static final int BLANK_FRAME_HEIGHT = 50;

  private @MonotonicNonNull String testId;
  private @MonotonicNonNull TextureBitmapReader textureBitmapReader;
  private @MonotonicNonNull DefaultVideoFrameProcessor defaultVideoFrameProcessor;

  @EnsuresNonNull({"textureBitmapReader", "testId"})
  @Before
  public void setUp() {
    textureBitmapReader = new TextureBitmapReader();
    testId = testName.getMethodName();
  }

  @After
  public void tearDown() {
    checkNotNull(defaultVideoFrameProcessor).release();
  }

  @Test
  @RequiresNonNull({"textureBitmapReader", "testId"})
  public void frameDrop_withDefaultStrategy_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    ImmutableList<Long> frameTimesUs =
        ImmutableList.of(0L, 16_000L, 32_000L, 48_000L, 58_000L, 71_000L, 86_000L);

    ImmutableList<Long> actualPresentationTimesUs =
        processFramesToEndOfStream(
            frameTimesUs, FrameDropEffect.createDefaultFrameDropEffect(/* targetFrameRate= */ 30));

    assertThat(actualPresentationTimesUs).containsExactly(0L, 32_000L, 71_000L).inOrder();
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId);
  }

  @Test
  @RequiresNonNull({"textureBitmapReader", "testId"})
  public void frameDrop_withSimpleStrategy_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    ImmutableList<Long> frameTimesUs =
        ImmutableList.of(0L, 250_000L, 500_000L, 750_000L, 1_000_000L, 1_500_000L);

    ImmutableList<Long> actualPresentationTimesUs =
        processFramesToEndOfStream(
            frameTimesUs,
            FrameDropEffect.createSimpleFrameDropEffect(
                /* expectedFrameRate= */ 6, /* targetFrameRate= */ 2));

    assertThat(actualPresentationTimesUs).containsExactly(0L, 750_000L).inOrder();
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId);
  }

  @Test
  @RequiresNonNull({"textureBitmapReader", "testId"})
  public void frameDrop_withSimpleStrategy_outputsAllFrames() throws Exception {
    ImmutableList<Long> frameTimesUs = ImmutableList.of(0L, 333_333L, 666_667L);

    ImmutableList<Long> actualPresentationTimesUs =
        processFramesToEndOfStream(
            frameTimesUs,
            FrameDropEffect.createSimpleFrameDropEffect(
                /* expectedFrameRate= */ 3, /* targetFrameRate= */ 3));

    assertThat(actualPresentationTimesUs).containsExactly(0L, 333_333L, 666_667L).inOrder();
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId);
  }

  private static void getAndAssertOutputBitmaps(
      TextureBitmapReader textureBitmapReader, List<Long> presentationTimesUs, String testId)
      throws IOException {
    for (int i = 0; i < presentationTimesUs.size(); i++) {
      long presentationTimeUs = presentationTimesUs.get(i);
      Bitmap actualBitmap = textureBitmapReader.getBitmapAtPresentationTimeUs(presentationTimeUs);
      Bitmap expectedBitmap =
          readBitmap(Util.formatInvariant("%s/pts_%d.png", ASSET_PATH, presentationTimeUs));
      maybeSaveTestBitmap(
          testId, String.valueOf(presentationTimeUs), actualBitmap, /* path= */ null);
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
      assertThat(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
    }
  }

  @EnsuresNonNull("defaultVideoFrameProcessor")
  private ImmutableList<Long> processFramesToEndOfStream(
      List<Long> inputPresentationTimesUs, FrameDropEffect frameDropEffect) throws Exception {
    AtomicReference<@NullableType VideoFrameProcessingException>
        videoFrameProcessingExceptionReference = new AtomicReference<>();
    BlankFrameProducer blankFrameProducer =
        new BlankFrameProducer(BLANK_FRAME_WIDTH, BLANK_FRAME_HEIGHT);
    CountDownLatch videoFrameProcessorReadyCountDownLatch = new CountDownLatch(1);
    CountDownLatch videoFrameProcessingEndedCountDownLatch = new CountDownLatch(1);
    ImmutableList.Builder<Long> actualPresentationTimesUs = new ImmutableList.Builder<>();

    defaultVideoFrameProcessor =
        checkNotNull(
            new DefaultVideoFrameProcessor.Factory.Builder()
                .setTextureOutput(
                    (textureProducer, outputTexture, presentationTimeUs, token) -> {
                      checkNotNull(textureBitmapReader)
                          .readBitmap(outputTexture, presentationTimeUs);
                      textureProducer.releaseOutputTexture(presentationTimeUs);
                    },
                    /* textureOutputCapacity= */ 1)
                .build()
                .create(
                    getApplicationContext(),
                    DebugViewProvider.NONE,
                    /* inputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
                    /* outputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
                    /* renderFramesAutomatically= */ true,
                    MoreExecutors.directExecutor(),
                    new VideoFrameProcessor.Listener() {
                      @Override
                      public void onInputStreamRegistered(
                          @VideoFrameProcessor.InputType int inputType,
                          List<Effect> effects,
                          FrameInfo frameInfo) {
                        videoFrameProcessorReadyCountDownLatch.countDown();
                      }

                      @Override
                      public void onOutputSizeChanged(int width, int height) {}

                      @Override
                      public void onOutputFrameAvailableForRendering(long presentationTimeUs) {
                        actualPresentationTimesUs.add(presentationTimeUs);
                      }

                      @Override
                      public void onError(VideoFrameProcessingException exception) {
                        videoFrameProcessingExceptionReference.set(exception);
                        videoFrameProcessingEndedCountDownLatch.countDown();
                      }

                      @Override
                      public void onEnded() {
                        videoFrameProcessingEndedCountDownLatch.countDown();
                      }
                    }));

    defaultVideoFrameProcessor.getTaskExecutor().submit(blankFrameProducer::configureGlObjects);
    // A frame needs to be registered despite not queuing any external input to ensure
    // that the video frame processor knows about the stream offset.
    checkNotNull(defaultVideoFrameProcessor)
        .registerInputStream(
            INPUT_TYPE_SURFACE,
            /* effects= */ ImmutableList.of(
                (GlEffect) (context, useHdr) -> blankFrameProducer,
                // Use an overlay effect to generate bitmaps with timestamps on it.
                new OverlayEffect(
                    ImmutableList.of(
                        new TextOverlay() {
                          @Override
                          public SpannableString getText(long presentationTimeUs) {
                            SpannableString text =
                                new SpannableString(String.valueOf(presentationTimeUs));
                            text.setSpan(
                                new ForegroundColorSpan(Color.BLACK),
                                /* start= */ 0,
                                text.length(),
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            text.setSpan(
                                new AbsoluteSizeSpan(/* size= */ 24),
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
                        })),
                frameDropEffect),
            new FrameInfo.Builder(BLANK_FRAME_WIDTH, BLANK_FRAME_HEIGHT).build());
    videoFrameProcessorReadyCountDownLatch.await();
    blankFrameProducer.produceBlankFrames(inputPresentationTimesUs);
    defaultVideoFrameProcessor.signalEndOfInput();
    videoFrameProcessingEndedCountDownLatch.await();
    @Nullable
    Exception videoFrameProcessingException = videoFrameProcessingExceptionReference.get();
    if (videoFrameProcessingException != null) {
      throw videoFrameProcessingException;
    }
    return actualPresentationTimesUs.build();
  }
}
