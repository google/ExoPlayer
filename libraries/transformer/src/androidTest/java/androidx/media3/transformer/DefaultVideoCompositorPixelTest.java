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
package androidx.media3.transformer;

import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmap;
import static androidx.media3.test.utils.VideoFrameProcessorTestRunner.VIDEO_FRAME_PROCESSING_WAIT_MS;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.graphics.Bitmap;
import android.opengl.EGLContext;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DefaultGlObjectsProvider;
import androidx.media3.effect.DefaultVideoCompositor;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.VideoCompositor;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.TextureBitmapReader;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Pixel test for {@link DefaultVideoCompositor} compositing 2 input frames into 1 output frame. */
@RunWith(Parameterized.class)
public final class DefaultVideoCompositorPixelTest {
  @Parameterized.Parameters(name = "useSharedExecutor={0}")
  public static ImmutableList<Boolean> useSharedExecutor() {
    return ImmutableList.of(true, false);
  }

  @Parameterized.Parameter public boolean useSharedExecutor;
  @Rule public final TestName testName = new TestName();

  private static final String ORIGINAL_PNG_ASSET_PATH = "media/bitmap/input_images/media3test.png";
  private static final String GRAYSCALE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/grayscale_media3test.png";
  private static final String ROTATE180_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/rotate180_media3test.png";
  private static final String GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH =
      "media/bitmap/sample_mp4_first_frame/electrical_colors/grayscaleAndRotate180Composite.png";

  private @MonotonicNonNull String testId;
  private @MonotonicNonNull VideoCompositorTestRunner compositorTestRunner;

  @Before
  @EnsuresNonNull("testId")
  public void setUpTestId() {
    testId = testName.getMethodName();
  }

  @After
  public void tearDown() {
    if (compositorTestRunner != null) {
      compositorTestRunner.release();
    }
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withOneFrameFromEach_matchesExpectedBitmap() throws Exception {
    compositorTestRunner = new VideoCompositorTestRunner(testId, useSharedExecutor);

    compositorTestRunner.queueBitmapsToBothInputs(/* durationSec= */ 1);

    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReader1.getBitmap(),
        /* actualBitmapLabel= */ "actualCompositorInputBitmap1",
        GRAYSCALE_PNG_ASSET_PATH);
    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReader2.getBitmap(),
        /* actualBitmapLabel= */ "actualCompositorInputBitmap2",
        ROTATE180_PNG_ASSET_PATH);
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withFiveFramesFromEach_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner = new VideoCompositorTestRunner(testId, useSharedExecutor);

    compositorTestRunner.queueBitmapsToBothInputs(/* durationSec= */ 5);

    ImmutableList<Long> expectedTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND,
            4 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReader1.getOutputTimestamps())
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReader2.getOutputTimestamps())
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.compositedTimestamps)
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  // TODO: b/262694346 -  Add tests for:
  //  * variable frame-rate.
  //  * checking correct input frames are composited.
  @Test
  @RequiresNonNull("testId")
  public void composite_onePrimaryAndFiveSecondaryFrames_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner = new VideoCompositorTestRunner(testId, useSharedExecutor);

    compositorTestRunner.queueBitmapsToBothInputs(
        /* durationSec= */ 1, /* secondarySourceFrameRate= */ 5f);

    ImmutableList<Long> primaryTimestamps = ImmutableList.of(0 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND / 5,
            2 * C.MICROS_PER_SECOND / 5,
            3 * C.MICROS_PER_SECOND / 5,
            4 * C.MICROS_PER_SECOND / 5);
    assertThat(compositorTestRunner.inputBitmapReader1.getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReader2.getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.compositedTimestamps)
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_fivePrimaryAndOneSecondaryFrames_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner = new VideoCompositorTestRunner(testId, useSharedExecutor);

    compositorTestRunner.queueBitmapsToBothInputs(
        /* durationSec= */ 5, /* secondarySourceFrameRate= */ .2f);

    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND,
            4 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps = ImmutableList.of(0 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReader1.getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReader2.getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.compositedTimestamps)
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_primaryDoubleSecondaryFrameRate_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner = new VideoCompositorTestRunner(testId, useSharedExecutor);

    compositorTestRunner.queueBitmapsToBothInputs(
        /* durationSec= */ 4, /* secondarySourceFrameRate= */ .5f);

    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(0 * C.MICROS_PER_SECOND, 2 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReader1.getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReader2.getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.compositedTimestamps)
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_primaryHalfSecondaryFrameRate_matchesExpectedTimestamps() throws Exception {
    compositorTestRunner = new VideoCompositorTestRunner(testId, useSharedExecutor);

    compositorTestRunner.queueBitmapsToBothInputs(
        /* durationSec= */ 2, /* secondarySourceFrameRate= */ 2f);

    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(0 * C.MICROS_PER_SECOND, 1 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND / 2,
            2 * C.MICROS_PER_SECOND / 2,
            3 * C.MICROS_PER_SECOND / 2);
    assertThat(compositorTestRunner.inputBitmapReader1.getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReader2.getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.compositedTimestamps)
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withTenFramesFromEach_matchesExpectedFrameCount()
      throws Exception {
    compositorTestRunner = new VideoCompositorTestRunner(testId, useSharedExecutor);
    int numberOfFramesToQueue = 10;

    compositorTestRunner.queueBitmapsToBothInputs(numberOfFramesToQueue);

    assertThat(compositorTestRunner.inputBitmapReader1.getOutputTimestamps())
        .hasSize(numberOfFramesToQueue);
    assertThat(compositorTestRunner.inputBitmapReader2.getOutputTimestamps())
        .hasSize(numberOfFramesToQueue);
    assertThat(compositorTestRunner.compositedTimestamps).hasSize(numberOfFramesToQueue);
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  /**
   * A test runner for {@link DefaultVideoCompositor} tests.
   *
   * <p>Composites input bitmaps from two input sources.
   */
  private static final class VideoCompositorTestRunner {
    // Compositor tests rely on 2 VideoFrameProcessor instances, plus the compositor.
    private static final int COMPOSITOR_TIMEOUT_MS = 2 * VIDEO_FRAME_PROCESSING_WAIT_MS;
    private static final Effect ROTATE_180_EFFECT =
        new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build();
    private static final Effect GRAYSCALE_EFFECT = RgbFilter.createGrayscaleFilter();

    public final TextureBitmapReader inputBitmapReader1;
    public final TextureBitmapReader inputBitmapReader2;
    public final List<Long> compositedTimestamps;
    private final VideoFrameProcessorTestRunner inputVideoFrameProcessorTestRunner1;
    private final VideoFrameProcessorTestRunner inputVideoFrameProcessorTestRunner2;
    private final VideoCompositor videoCompositor;
    private final @Nullable ExecutorService sharedExecutorService;
    private final AtomicReference<VideoFrameProcessingException> compositionException;
    private final AtomicReference<Bitmap> compositedFirstOutputBitmap;
    private final CountDownLatch compositorEnded;
    private final String testId;

    public VideoCompositorTestRunner(String testId, boolean useSharedExecutor)
        throws GlUtil.GlException, VideoFrameProcessingException {
      this.testId = testId;
      sharedExecutorService =
          useSharedExecutor ? Util.newSingleThreadExecutor("Effect:Shared:GlThread") : null;
      EGLContext sharedEglContext = AndroidTestUtil.createOpenGlObjects();
      GlObjectsProvider glObjectsProvider =
          new DefaultGlObjectsProvider(
              /* sharedEglContext= */ useSharedExecutor ? null : sharedEglContext);

      compositionException = new AtomicReference<>();
      compositedFirstOutputBitmap = new AtomicReference<>();
      compositedTimestamps = new CopyOnWriteArrayList<>();
      compositorEnded = new CountDownLatch(1);
      videoCompositor =
          new DefaultVideoCompositor(
              getApplicationContext(),
              glObjectsProvider,
              sharedExecutorService,
              new VideoCompositor.Listener() {
                @Override
                public void onError(VideoFrameProcessingException exception) {
                  compositionException.set(exception);
                  compositorEnded.countDown();
                }

                @Override
                public void onEnded() {
                  compositorEnded.countDown();
                }
              },
              /* textureOutputListener= */ (GlTextureInfo outputTexture,
                  long presentationTimeUs,
                  DefaultVideoFrameProcessor.ReleaseOutputTextureCallback
                      releaseOutputTextureCallback,
                  long syncObject) -> {
                if (!useSharedExecutor) {
                  GlUtil.awaitSyncObject(syncObject);
                }
                if (compositedFirstOutputBitmap.get() == null) {
                  compositedFirstOutputBitmap.set(
                      BitmapPixelTestUtil.createArgb8888BitmapFromFocusedGlFramebuffer(
                          outputTexture.width, outputTexture.height));
                }
                compositedTimestamps.add(presentationTimeUs);
                releaseOutputTextureCallback.release(presentationTimeUs);
              },
              /* textureOutputCapacity= */ 1);
      inputBitmapReader1 = new TextureBitmapReader();
      inputVideoFrameProcessorTestRunner1 =
          createVideoFrameProcessorTestRunnerBuilder(
                  testId,
                  inputBitmapReader1,
                  videoCompositor,
                  sharedExecutorService,
                  glObjectsProvider)
              .setEffects(GRAYSCALE_EFFECT)
              .build();
      inputBitmapReader2 = new TextureBitmapReader();
      inputVideoFrameProcessorTestRunner2 =
          createVideoFrameProcessorTestRunnerBuilder(
                  testId,
                  inputBitmapReader2,
                  videoCompositor,
                  sharedExecutorService,
                  glObjectsProvider)
              .setEffects(ROTATE_180_EFFECT)
              .build();
    }

    /**
     * Queues {@code durationSec} bitmaps, with one bitmap per second, starting from and including
     * {@code 0} seconds. Both sources have a {@code frameRate} of {@code 1}.
     */
    public void queueBitmapsToBothInputs(int durationSec) throws IOException {
      queueBitmapsToBothInputs(durationSec, /* secondarySourceFrameRate= */ 1);
    }

    /**
     * Queues {@code durationSec} bitmaps, with one bitmap per second, starting from and including
     * {@code 0} seconds. The primary source has a {@code frameRate} of {@code 1}, while secondary
     * sources have a {@code frameRate} of {@code secondarySourceFrameRate}.
     */
    public void queueBitmapsToBothInputs(int durationSec, float secondarySourceFrameRate)
        throws IOException {
      inputVideoFrameProcessorTestRunner1.queueInputBitmap(
          readBitmap(ORIGINAL_PNG_ASSET_PATH),
          /* durationUs= */ durationSec * C.MICROS_PER_SECOND,
          /* offsetToAddUs= */ 0,
          /* frameRate= */ 1);
      inputVideoFrameProcessorTestRunner2.queueInputBitmap(
          readBitmap(ORIGINAL_PNG_ASSET_PATH),
          /* durationUs= */ durationSec * C.MICROS_PER_SECOND,
          /* offsetToAddUs= */ 0,
          /* frameRate= */ secondarySourceFrameRate);

      inputVideoFrameProcessorTestRunner1.signalEndOfInput();
      inputVideoFrameProcessorTestRunner2.signalEndOfInput();

      inputVideoFrameProcessorTestRunner1.awaitFrameProcessingEnd(COMPOSITOR_TIMEOUT_MS);
      inputVideoFrameProcessorTestRunner2.awaitFrameProcessingEnd(COMPOSITOR_TIMEOUT_MS);
      @Nullable Exception endCompositingException = null;
      try {
        if (!compositorEnded.await(COMPOSITOR_TIMEOUT_MS, MILLISECONDS)) {
          endCompositingException = new IllegalStateException("Compositing timed out.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        endCompositingException = e;
      }

      assertThat(compositionException.get()).isNull();
      assertThat(endCompositingException).isNull();
    }

    public void saveAndAssertFirstCompositedBitmapMatchesExpected(String expectedBitmapPath)
        throws IOException {
      saveAndAssertBitmapMatchesExpected(
          testId,
          compositedFirstOutputBitmap.get(),
          /* actualBitmapLabel= */ "compositedFirstOutputBitmap",
          expectedBitmapPath);
    }

    public void release() {
      inputVideoFrameProcessorTestRunner1.release();
      inputVideoFrameProcessorTestRunner2.release();
      videoCompositor.release();

      if (sharedExecutorService != null) {
        try {
          sharedExecutorService.shutdown();
          if (!sharedExecutorService.awaitTermination(COMPOSITOR_TIMEOUT_MS, MILLISECONDS)) {
            throw new IllegalStateException("Missed shutdown timeout.");
          }
        } catch (InterruptedException unexpected) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(unexpected);
        }
      }
    }

    private static VideoFrameProcessorTestRunner.Builder createVideoFrameProcessorTestRunnerBuilder(
        String testId,
        TextureBitmapReader textureBitmapReader,
        VideoCompositor videoCompositor,
        @Nullable ExecutorService executorService,
        GlObjectsProvider glObjectsProvider) {
      int inputId = videoCompositor.registerInputSource();
      DefaultVideoFrameProcessor.Factory.Builder defaultVideoFrameProcessorFactoryBuilder =
          new DefaultVideoFrameProcessor.Factory.Builder()
              .setGlObjectsProvider(glObjectsProvider)
              .setTextureOutput(
                  /* textureOutputListener= */ (GlTextureInfo outputTexture,
                      long presentationTimeUs,
                      DefaultVideoFrameProcessor.ReleaseOutputTextureCallback
                          releaseOutputTextureCallback,
                      long syncObject) -> {
                    GlUtil.awaitSyncObject(syncObject);
                    textureBitmapReader.readBitmap(outputTexture, presentationTimeUs);
                    videoCompositor.queueInputTexture(
                        inputId, outputTexture, presentationTimeUs, releaseOutputTextureCallback);
                  },
                  /* textureOutputCapacity= */ 2);
      if (executorService != null) {
        defaultVideoFrameProcessorFactoryBuilder.setExecutorService(executorService);
      }
      return new VideoFrameProcessorTestRunner.Builder()
          .setTestId(testId)
          .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactoryBuilder.build())
          .setInputColorInfo(ColorInfo.SRGB_BT709_FULL)
          .setBitmapReader(textureBitmapReader)
          .setOnEndedListener(() -> videoCompositor.signalEndOfInputSource(inputId));
    }
  }

  private static void saveAndAssertBitmapMatchesExpected(
      String testId, Bitmap actualBitmap, String actualBitmapLabel, String expectedBitmapAssetPath)
      throws IOException {
    maybeSaveTestBitmap(testId, actualBitmapLabel, actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            readBitmap(expectedBitmapAssetPath), actualBitmap, testId);
    assertThat(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_DIFFERENT_DEVICE);
  }
}
