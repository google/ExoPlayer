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

import android.content.Context;
import android.graphics.Bitmap;
import android.text.SpannableString;
import androidx.annotation.Nullable;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.Effect;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.Consumer;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.test.utils.TextureBitmapReader;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/** Utilities for effects tests. */
@UnstableApi
/* package */ class EffectsTestUtil {

  /**
   * Gets the {@link Bitmap}s (generated at the timestamps in {@code presentationTimesUs}) from the
   * {@link TextureBitmapReader}, and asserts that they are equal to files stored in the {@code
   * goldenFileAssetPath} with the same {@code testId}.
   *
   * <p>Tries to save the {@link Bitmap}s as PNGs to the {@link Context#getCacheDir() cache
   * directory}.
   */
  public static void getAndAssertOutputBitmaps(
      TextureBitmapReader textureBitmapReader,
      List<Long> presentationTimesUs,
      String testId,
      String goldenFileAssetPath)
      throws IOException {
    for (int i = 0; i < presentationTimesUs.size(); i++) {
      long presentationTimeUs = presentationTimesUs.get(i);
      Bitmap actualBitmap = textureBitmapReader.getBitmapAtPresentationTimeUs(presentationTimeUs);
      Bitmap expectedBitmap =
          readBitmap(
              Util.formatInvariant("%s/pts_%d.png", goldenFileAssetPath, presentationTimeUs));
      maybeSaveTestBitmap(
          testId, String.valueOf(presentationTimeUs), actualBitmap, /* path= */ null);
      float averagePixelAbsoluteDifference =
          getBitmapAveragePixelAbsoluteDifferenceArgb8888(expectedBitmap, actualBitmap, testId);
      assertThat(averagePixelAbsoluteDifference)
          .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE);
    }
  }

  /**
   * Generates and processes a frame for each timestamp in {@code presentationTimesUs} through a
   * {@link DefaultVideoFrameProcessor}, applying the given {@link GlEffect}, and outputting the
   * resulting frame to the provided {@link TextureBitmapReader}.
   *
   * <p>The generated frames have their associated timestamps overlaid on them.
   *
   * @param frameWidth The width of the generated frames.
   * @param frameHeight The height of the generated frames.
   * @param presentationTimesUs The timestamps of the generated frames, in microseconds.
   * @param glEffect The effect to apply to the frames.
   * @param textSpanConsumer A {@link Consumer} used to set the spans that styles the text overlaid
   *     onto the frames.
   */
  // MoreExecutors.directExecutor() pattern is consistent with our codebase.
  @SuppressWarnings("StaticImportPreferred")
  public static ImmutableList<Long> generateAndProcessFrames(
      int frameWidth,
      int frameHeight,
      List<Long> presentationTimesUs,
      GlEffect glEffect,
      TextureBitmapReader textureBitmapReader,
      Consumer<SpannableString> textSpanConsumer)
      throws Exception {
    ImmutableList.Builder<Long> actualPresentationTimesUs = new ImmutableList.Builder<>();
    @Nullable DefaultVideoFrameProcessor defaultVideoFrameProcessor = null;

    try {
      AtomicReference<@NullableType VideoFrameProcessingException>
          videoFrameProcessingExceptionReference = new AtomicReference<>();
      BlankFrameProducer blankFrameProducer = new BlankFrameProducer(frameWidth, frameHeight);
      CountDownLatch videoFrameProcessorReadyCountDownLatch = new CountDownLatch(1);
      CountDownLatch videoFrameProcessingEndedCountDownLatch = new CountDownLatch(1);

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
                          videoFrameProcessorReadyCountDownLatch.countDown();
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
                              textSpanConsumer.accept(text);
                              return text;
                            }
                          })),
                  glEffect),
              new FrameInfo.Builder(ColorInfo.SDR_BT709_LIMITED, frameWidth, frameHeight).build());
      videoFrameProcessorReadyCountDownLatch.await();
      checkNoVideoFrameProcessingExceptionIsThrown(videoFrameProcessingExceptionReference);
      blankFrameProducer.produceBlankFrames(presentationTimesUs);
      defaultVideoFrameProcessor.signalEndOfInput();
      videoFrameProcessingEndedCountDownLatch.await();
      checkNoVideoFrameProcessingExceptionIsThrown(videoFrameProcessingExceptionReference);
    } finally {
      if (defaultVideoFrameProcessor != null) {
        defaultVideoFrameProcessor.release();
      }
    }
    return actualPresentationTimesUs.build();
  }

  private static void checkNoVideoFrameProcessingExceptionIsThrown(
      AtomicReference<@NullableType VideoFrameProcessingException>
          videoFrameProcessingExceptionReference)
      throws Exception {
    @Nullable
    Exception videoFrameProcessingException = videoFrameProcessingExceptionReference.get();
    if (videoFrameProcessingException != null) {
      throw videoFrameProcessingException;
    }
  }

  private EffectsTestUtil() {}
}
