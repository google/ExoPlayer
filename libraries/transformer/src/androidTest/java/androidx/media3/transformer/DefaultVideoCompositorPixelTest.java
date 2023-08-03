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
import java.util.ArrayList;
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
  private static final ImmutableList<Effect> TWO_INPUT_COMPOSITOR_EFFECTS =
      ImmutableList.of(
          RgbFilter.createGrayscaleFilter(),
          new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build());

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
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);

    compositorTestRunner.queueBitmapToAllInputs(/* durationSec= */ 1);
    compositorTestRunner.endCompositing();

    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(0).getBitmap(),
        /* actualBitmapLabel= */ "actualCompositorInputBitmap1",
        GRAYSCALE_PNG_ASSET_PATH);
    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(1).getBitmap(),
        /* actualBitmapLabel= */ "actualCompositorInputBitmap2",
        ROTATE180_PNG_ASSET_PATH);
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withFiveFramesFromEach_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);

    compositorTestRunner.queueBitmapToAllInputs(/* durationSec= */ 5);
    compositorTestRunner.endCompositing();

    ImmutableList<Long> expectedTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND,
            4 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.compositedTimestamps)
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertFirstCompositedBitmapMatchesExpected(
        GRAYSCALE_AND_ROTATE180_COMPOSITE_PNG_ASSET_PATH);
  }

  // TODO: b/262694346 -  Add tests for:
  //  * checking correct input frames are composited.
  @Test
  @RequiresNonNull("testId")
  public void composite_onePrimaryAndFiveSecondaryFrames_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* durationSec= */ 5, /* offsetToAddSec= */ 0L, /* frameRate= */ 0.2f);
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* durationSec= */ 5, /* offsetToAddSec= */ 0L, /* frameRate= */ 1f);
    compositorTestRunner.endCompositing();

    ImmutableList<Long> primaryTimestamps = ImmutableList.of(0 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND,
            4 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
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
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* durationSec= */ 5, /* offsetToAddSec= */ 0L, /* frameRate= */ 1f);
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* durationSec= */ 5, /* offsetToAddSec= */ 0L, /* frameRate= */ 0.2f);
    compositorTestRunner.endCompositing();

    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND,
            4 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps = ImmutableList.of(0 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
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
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* durationSec= */ 4, /* offsetToAddSec= */ 0L, /* frameRate= */ 1f);
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* durationSec= */ 4, /* offsetToAddSec= */ 0L, /* frameRate= */ 0.5f);
    compositorTestRunner.endCompositing();

    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(0 * C.MICROS_PER_SECOND, 2 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
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
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* durationSec= */ 4, /* offsetToAddSec= */ 0L, /* frameRate= */ 0.5f);
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* durationSec= */ 4, /* offsetToAddSec= */ 0L, /* frameRate= */ 1f);
    compositorTestRunner.endCompositing();

    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(0 * C.MICROS_PER_SECOND, 2 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
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
  public void composite_primaryVariableFrameRateWithOffset_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* durationSec= */ 2, /* offsetToAddSec= */ 1L, /* frameRate= */ 0.5f);
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* durationSec= */ 2, /* offsetToAddSec= */ 3L, /* frameRate= */ 1f);
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* durationSec= */ 5, /* offsetToAddSec= */ 0L, /* frameRate= */ 1f);
    compositorTestRunner.endCompositing();

    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(1 * C.MICROS_PER_SECOND, 3 * C.MICROS_PER_SECOND, 4 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND,
            4 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
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
  public void composite_secondaryVariableFrameRateWithOffset_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* durationSec= */ 5, /* offsetToAddSec= */ 0L, /* frameRate= */ 1f);
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* durationSec= */ 2, /* offsetToAddSec= */ 1L, /* frameRate= */ 0.5f);
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* durationSec= */ 2, /* offsetToAddSec= */ 3L, /* frameRate= */ 1f);
    compositorTestRunner.endCompositing();

    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(
            0 * C.MICROS_PER_SECOND,
            1 * C.MICROS_PER_SECOND,
            2 * C.MICROS_PER_SECOND,
            3 * C.MICROS_PER_SECOND,
            4 * C.MICROS_PER_SECOND);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(1 * C.MICROS_PER_SECOND, 3 * C.MICROS_PER_SECOND, 4 * C.MICROS_PER_SECOND);
    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
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
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECTS);
    int numberOfFramesToQueue = 10;

    compositorTestRunner.queueBitmapToAllInputs(/* durationSec= */ numberOfFramesToQueue);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .hasSize(numberOfFramesToQueue);
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
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
    private static final int COMPOSITOR_INPUT_SIZE = 2;

    public final List<Long> compositedTimestamps;
    public final List<TextureBitmapReader> inputBitmapReaders;
    private final List<VideoFrameProcessorTestRunner> inputVideoFrameProcessorTestRunners;
    private final VideoCompositor videoCompositor;
    private final @Nullable ExecutorService sharedExecutorService;
    private final AtomicReference<VideoFrameProcessingException> compositionException;
    private final AtomicReference<Bitmap> compositedFirstOutputBitmap;
    private final CountDownLatch compositorEnded;
    private final String testId;

    /**
     * Creates an instance.
     *
     * @param testId The {@link String} identifier for the test, used to name output files.
     * @param useSharedExecutor Whether to use a shared executor for {@link
     *     VideoFrameProcessorTestRunner} and {@link VideoCompositor} instances.
     * @param inputEffects {@link Effect}s to apply for {@link VideoCompositor} input sources. The
     *     size of this {@link List} is the amount of inputs. One {@link Effect} is used for each
     *     input.
     */
    public VideoCompositorTestRunner(
        String testId, boolean useSharedExecutor, List<Effect> inputEffects)
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
      inputBitmapReaders = new ArrayList<>();
      inputVideoFrameProcessorTestRunners = new ArrayList<>();
      assertThat(inputEffects).hasSize(COMPOSITOR_INPUT_SIZE);
      for (int i = 0; i < inputEffects.size(); i++) {
        TextureBitmapReader textureBitmapReader = new TextureBitmapReader();
        inputBitmapReaders.add(textureBitmapReader);
        VideoFrameProcessorTestRunner vfpTestRunner =
            createVideoFrameProcessorTestRunnerBuilder(
                    testId,
                    textureBitmapReader,
                    videoCompositor,
                    sharedExecutorService,
                    glObjectsProvider)
                .setEffects(inputEffects.get(i))
                .build();
        inputVideoFrameProcessorTestRunners.add(vfpTestRunner);
      }
    }

    /**
     * Queues {@code durationSec} bitmaps, with one bitmap per second, starting from and including
     * {@code 0} seconds. Sources have a {@code frameRate} of {@code 1}.
     */
    public void queueBitmapToAllInputs(int durationSec) throws IOException {
      for (int i = 0; i < inputVideoFrameProcessorTestRunners.size(); i++) {
        queueBitmapToInput(
            /* inputId= */ i, durationSec, /* offsetToAddSec= */ 0L, /* frameRate= */ 1f);
      }
    }

    /**
     * Queues {@code durationSec} bitmaps, with one bitmap per second, starting from and including
     * {@code 0} seconds. The primary source has a {@code frameRate} of {@code 1}, while secondary
     * sources have a {@code frameRate} of {@code secondarySourceFrameRate}.
     */
    public void queueBitmapToInput(
        int inputId, int durationSec, long offsetToAddSec, float frameRate) throws IOException {
      inputVideoFrameProcessorTestRunners
          .get(inputId)
          .queueInputBitmap(
              readBitmap(ORIGINAL_PNG_ASSET_PATH),
              /* durationUs= */ durationSec * C.MICROS_PER_SECOND,
              /* offsetToAddUs= */ offsetToAddSec * C.MICROS_PER_SECOND,
              /* frameRate= */ frameRate);
    }

    public void endCompositing() {
      for (int i = 0; i < inputVideoFrameProcessorTestRunners.size(); i++) {
        inputVideoFrameProcessorTestRunners.get(i).signalEndOfInput();
      }
      for (int i = 0; i < inputVideoFrameProcessorTestRunners.size(); i++) {
        inputVideoFrameProcessorTestRunners.get(i).awaitFrameProcessingEnd(COMPOSITOR_TIMEOUT_MS);
      }
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
      for (int i = 0; i < inputVideoFrameProcessorTestRunners.size(); i++) {
        inputVideoFrameProcessorTestRunners.get(i).release();
      }
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
