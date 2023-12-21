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

import static androidx.media3.common.util.Util.SDK_INT;
import static androidx.media3.common.util.Util.isRunningOnEmulator;
import static androidx.media3.test.utils.BitmapPixelTestUtil.MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;
import static androidx.media3.test.utils.BitmapPixelTestUtil.maybeSaveTestBitmap;
import static androidx.media3.test.utils.BitmapPixelTestUtil.readBitmapUnpremultipliedAlpha;
import static androidx.media3.test.utils.VideoFrameProcessorTestRunner.VIDEO_FRAME_PROCESSING_WAIT_MS;
import static androidx.media3.test.utils.VideoFrameProcessorTestRunner.createTimestampIterator;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.lang.Math.max;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.opengl.EGLContext;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Pair;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Effect;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.Util;
import androidx.media3.effect.AlphaScale;
import androidx.media3.effect.DefaultGlObjectsProvider;
import androidx.media3.effect.DefaultVideoCompositor;
import androidx.media3.effect.DefaultVideoFrameProcessor;
import androidx.media3.effect.OverlayEffect;
import androidx.media3.effect.OverlaySettings;
import androidx.media3.effect.Presentation;
import androidx.media3.effect.RgbFilter;
import androidx.media3.effect.ScaleAndRotateTransformation;
import androidx.media3.effect.TextOverlay;
import androidx.media3.effect.VideoCompositor;
import androidx.media3.effect.VideoCompositorSettings;
import androidx.media3.test.utils.BitmapPixelTestUtil;
import androidx.media3.test.utils.TextureBitmapReader;
import androidx.media3.test.utils.VideoFrameProcessorTestRunner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
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

  // Golden images were generated on an API 33 emulator. API 26 emulators have a different text
  // rendering implementation that leads to a larger pixel difference.
  public static final float MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_WITH_TEXT_OVERLAY =
      isRunningOnEmulator() && SDK_INT <= 26 ? 2.5f : MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE;

  @Parameterized.Parameter public boolean useSharedExecutor;
  @Rule public final TestName testName = new TestName();

  private static final String ORIGINAL_PNG_ASSET_PATH =
      "media/bitmap/input_images/media3test_srgb.png";
  private static final String TEST_DIRECTORY = "media/bitmap/CompositorTestTimestamps/";
  private static final ImmutableList<ImmutableList<Effect>> TWO_INPUT_COMPOSITOR_EFFECT_LISTS =
      ImmutableList.of(
          ImmutableList.of(RgbFilter.createGrayscaleFilter(), new AlphaScale(0.7f)),
          ImmutableList.of(
              new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build()));

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

  // Tests for alpha and frame alpha/occlusion.

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withOneFrameFromEach_differentTimestamp_matchesExpectedBitmap()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* timestamps= */ ImmutableList.of(0L));
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* timestamps= */ ImmutableList.of(1_000_000L));
    compositorTestRunner.endCompositing();

    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(0).getBitmap(),
        /* actualBitmapLabel= */ "actual_input_grayscale",
        TEST_DIRECTORY + "input_grayscale_0s.png");
    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(1).getBitmap(),
        /* actualBitmapLabel= */ "actual_input_rotate180",
        TEST_DIRECTORY + "input_rotate180_1s.png");
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(ImmutableList.of("0s_1s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withPrimaryTransparent_differentTimestamp_matchesExpectedBitmap()
      throws Exception {
    ImmutableList<ImmutableList<Effect>> inputEffectLists =
        ImmutableList.of(
            ImmutableList.of(new AlphaScale(0f)),
            ImmutableList.of(
                new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build()));
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, inputEffectLists);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* timestamps= */ ImmutableList.of(0L));
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* timestamps= */ ImmutableList.of(1_000_000L));
    compositorTestRunner.endCompositing();

    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(0).getBitmap(),
        /* actualBitmapLabel= */ "actual_input_transparent",
        TEST_DIRECTORY + "input_transparent.png");
    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(1).getBitmap(),
        /* actualBitmapLabel= */ "actual_input_rotate180",
        TEST_DIRECTORY + "input_rotate180_1s.png");
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("0s_transparent_1s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withPrimaryOpaque_differentTimestamp_matchesExpectedBitmap()
      throws Exception {
    ImmutableList<ImmutableList<Effect>> inputEffectLists =
        ImmutableList.of(
            ImmutableList.of(RgbFilter.createGrayscaleFilter(), new AlphaScale(100f)),
            ImmutableList.of(
                new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build()));
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, inputEffectLists);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* timestamps= */ ImmutableList.of(0L));
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* timestamps= */ ImmutableList.of(1_000_000L));
    compositorTestRunner.endCompositing();

    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(0).getBitmap(),
        /* actualBitmapLabel= */ "actual_input_grayscale_opaque",
        TEST_DIRECTORY + "output_grayscale_opaque_0s.png");
    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(1).getBitmap(),
        /* actualBitmapLabel= */ "actual_input_rotate180",
        TEST_DIRECTORY + "input_rotate180_1s.png");
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("grayscale_opaque_0s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withSecondaryTransparent_differentTimestamp_matchesExpectedBitmap()
      throws Exception {
    ImmutableList<ImmutableList<Effect>> inputEffectLists =
        ImmutableList.of(
            ImmutableList.of(RgbFilter.createGrayscaleFilter(), new AlphaScale(0.7f)),
            ImmutableList.of(new AlphaScale(0f)));
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, inputEffectLists);

    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 0, /* timestamps= */ ImmutableList.of(0L));
    compositorTestRunner.queueBitmapToInput(
        /* inputId= */ 1, /* timestamps= */ ImmutableList.of(1_000_000L));
    compositorTestRunner.endCompositing();

    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(0).getBitmap(),
        /* actualBitmapLabel= */ "actual_input_grayscale",
        TEST_DIRECTORY + "input_grayscale_0s.png");
    saveAndAssertBitmapMatchesExpected(
        testId,
        compositorTestRunner.inputBitmapReaders.get(1).getBitmap(),
        /* actualBitmapLabel= */ "actual_input_transparent",
        TEST_DIRECTORY + "input_transparent.png");
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("0s_1s_transparent"));
  }

  // Tests for mixing different frame rates and timestamps.

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withFiveFramesFromEach_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);
    ImmutableList<Long> expectedTimestamps =
        ImmutableList.of(0L, 1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L);

    compositorTestRunner.queueBitmapToAllInputs(/* durationSec= */ 5);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.getCompositedTimestamps())
        .containsExactlyElementsIn(expectedTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("0s_0s", "1s_1s", "2s_2s", "3s_3s", "4s_4s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_onePrimaryAndFiveSecondaryFrames_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);
    ImmutableList<Long> primaryTimestamps = ImmutableList.of(0L);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(0L, 1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L);

    compositorTestRunner.queueBitmapToInput(/* inputId= */ 0, primaryTimestamps);
    compositorTestRunner.queueBitmapToInput(/* inputId= */ 1, secondaryTimestamps);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.getCompositedTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(ImmutableList.of("0s_0s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_fivePrimaryAndOneSecondaryFrames_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);
    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(0L, 1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L);
    ImmutableList<Long> secondaryTimestamps = ImmutableList.of(0L);

    compositorTestRunner.queueBitmapToInput(/* inputId= */ 0, primaryTimestamps);
    compositorTestRunner.queueBitmapToInput(/* inputId= */ 1, secondaryTimestamps);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.getCompositedTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("0s_0s", "1s_0s", "2s_0s", "3s_0s", "4s_0s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_primaryDoubleSecondaryFrameRate_matchesExpectedTimestamps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);
    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(0L, 1_000_000L, 2_000_000L, 3_000_000L);
    ImmutableList<Long> secondaryTimestamps = ImmutableList.of(0L, 2_000_000L);

    compositorTestRunner.queueBitmapToInput(/* inputId= */ 0, primaryTimestamps);
    compositorTestRunner.queueBitmapToInput(/* inputId= */ 1, secondaryTimestamps);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.getCompositedTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("0s_0s", "1s_0s", "2s_2s", "3s_2s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_primaryHalfSecondaryFrameRate_matchesExpectedTimestamps() throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);
    ImmutableList<Long> primaryTimestamps = ImmutableList.of(0L, 2_000_000L);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(0L, 1_000_000L, 2_000_000L, 3_000_000L);

    compositorTestRunner.queueBitmapToInput(/* inputId= */ 0, primaryTimestamps);
    compositorTestRunner.queueBitmapToInput(/* inputId= */ 1, secondaryTimestamps);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.getCompositedTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("0s_0s", "2s_2s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_primaryVariableFrameRateWithOffset_matchesExpectedTimestampsAndBitmaps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);
    ImmutableList<Long> primaryTimestamps = ImmutableList.of(1_000_000L, 3_000_000L, 4_000_000L);
    ImmutableList<Long> secondaryTimestamps =
        ImmutableList.of(0L, 1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L);

    compositorTestRunner.queueBitmapToInput(/* inputId= */ 0, primaryTimestamps);
    compositorTestRunner.queueBitmapToInput(/* inputId= */ 1, secondaryTimestamps);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.getCompositedTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("1s_1s", "3s_3s", "4s_4s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void composite_secondaryVariableFrameRateWithOffset_matchesExpectedTimestampsAndBitmaps()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);
    ImmutableList<Long> primaryTimestamps =
        ImmutableList.of(0L, 1_000_000L, 2_000_000L, 3_000_000L, 4_000_000L);
    ImmutableList<Long> secondaryTimestamps = ImmutableList.of(1_000_000L, 3_000_000L, 4_000_000L);

    compositorTestRunner.queueBitmapToInput(/* inputId= */ 0, primaryTimestamps);
    compositorTestRunner.queueBitmapToInput(/* inputId= */ 1, secondaryTimestamps);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(secondaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.getCompositedTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("0s_1s", "1s_1s", "2s_1s", "3s_3s", "4s_4s"));
  }

  // Tests for "many" inputs/frames.

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_withTenFramesFromEach_matchesExpectedFrameCount()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(testId, useSharedExecutor, TWO_INPUT_COMPOSITOR_EFFECT_LISTS);
    int numberOfFramesToQueue = 10;

    compositorTestRunner.queueBitmapToAllInputs(/* durationSec= */ numberOfFramesToQueue);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .hasSize(numberOfFramesToQueue);
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .hasSize(numberOfFramesToQueue);
    assertThat(compositorTestRunner.getCompositedTimestamps()).hasSize(numberOfFramesToQueue);
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeFiveInputs_withFiveFramesFromEach_matchesExpectedFrameCount()
      throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(
            testId,
            useSharedExecutor,
            /* inputEffectLists= */ ImmutableList.of(
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of(),
                ImmutableList.of()));
    int numberOfFramesToQueue = 5;

    compositorTestRunner.queueBitmapToAllInputs(/* durationSec= */ numberOfFramesToQueue);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.getCompositedTimestamps()).hasSize(numberOfFramesToQueue);
  }

  // Tests for different amounts of inputs.

  @Test
  @RequiresNonNull("testId")
  public void compositeOneInput_matchesExpectedBitmap() throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(
            testId,
            useSharedExecutor,
            ImmutableList.of(
                ImmutableList.of(RgbFilter.createGrayscaleFilter(), new AlphaScale(100f))));

    compositorTestRunner.queueBitmapToAllInputs(/* durationSec= */ 3);
    compositorTestRunner.endCompositing();

    ImmutableList<Long> primaryTimestamps = ImmutableList.of(0L, 1_000_000L, 2_000_000L);
    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("grayscale_opaque_0s", "grayscale_opaque_1s", "grayscale_opaque_2s"));
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeThreeInputs_matchesExpectedBitmap() throws Exception {
    compositorTestRunner =
        new VideoCompositorTestRunner(
            testId,
            useSharedExecutor,
            ImmutableList.of(
                ImmutableList.of(RgbFilter.createInvertedFilter(), new AlphaScale(0.4f)),
                ImmutableList.of(RgbFilter.createGrayscaleFilter(), new AlphaScale(0.7f)),
                ImmutableList.of(
                    new ScaleAndRotateTransformation.Builder().setRotationDegrees(180).build())));
    ImmutableList<Long> primaryTimestamps = ImmutableList.of(0L, 1_000_000L, 2_000_000L);
    ImmutableList<Long> secondary1Timestamps = ImmutableList.of(1_000_000L);
    ImmutableList<Long> secondary2Timestamps = ImmutableList.of(0L, 2_000_000L);

    compositorTestRunner.queueBitmapToInput(/* inputId= */ 0, primaryTimestamps);
    compositorTestRunner.queueBitmapToInput(/* inputId= */ 1, secondary1Timestamps);
    compositorTestRunner.queueBitmapToInput(/* inputId= */ 2, secondary2Timestamps);
    compositorTestRunner.endCompositing();

    assertThat(compositorTestRunner.inputBitmapReaders.get(0).getOutputTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(1).getOutputTimestamps())
        .containsExactlyElementsIn(secondary1Timestamps)
        .inOrder();
    assertThat(compositorTestRunner.inputBitmapReaders.get(2).getOutputTimestamps())
        .containsExactlyElementsIn(secondary2Timestamps)
        .inOrder();
    assertThat(compositorTestRunner.getCompositedTimestamps())
        .containsExactlyElementsIn(primaryTimestamps)
        .inOrder();
    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("0s_1s_0s", "1s_1s_0s", "2s_1s_2s"));
  }

  // Tests for different layouts.

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_pictureInPicture_matchesExpectedBitmap() throws Exception {
    ImmutableList<ImmutableList<Effect>> inputEffectLists =
        ImmutableList.of(ImmutableList.of(), ImmutableList.of(RgbFilter.createGrayscaleFilter()));
    VideoCompositorSettings pictureInPictureVideoCompositorSettings =
        new VideoCompositorSettings() {
          @Override
          public Size getOutputSize(List<Size> inputSizes) {
            return inputSizes.get(0);
          }

          @Override
          public OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs) {
            if (inputId == 0) {
              // This tests all OverlaySettings builder variables.
              return new OverlaySettings.Builder()
                  .setScale(.25f, .5f)
                  .setOverlayFrameAnchor(1, -1)
                  .setBackgroundFrameAnchor(.9f, -.7f)
                  .setRotationDegrees(20)
                  .setAlphaScale(.5f)
                  .build();
            } else {
              return new OverlaySettings.Builder().build();
            }
          }
        };
    compositorTestRunner =
        new VideoCompositorTestRunner(
            testId, useSharedExecutor, inputEffectLists, pictureInPictureVideoCompositorSettings);

    compositorTestRunner.queueBitmapToAllInputs(1);
    compositorTestRunner.endCompositing();

    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("picture_in_picture"));
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_differentDimensions_matchesExpectedBitmap() throws Exception {
    ImmutableList<ImmutableList<Effect>> inputEffectLists =
        ImmutableList.of(
            ImmutableList.of(
                Presentation.createForWidthAndHeight(100, 100, Presentation.LAYOUT_STRETCH_TO_FIT)),
            ImmutableList.of(RgbFilter.createGrayscaleFilter()));
    VideoCompositorSettings secondStreamAsOutputSizeVideoCompositorSettings =
        new VideoCompositorSettings() {
          @Override
          public Size getOutputSize(List<Size> inputSizes) {
            return Iterables.getLast(inputSizes);
          }

          @Override
          public OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs) {
            return new OverlaySettings.Builder().build();
          }
        };
    compositorTestRunner =
        new VideoCompositorTestRunner(
            testId,
            useSharedExecutor,
            inputEffectLists,
            secondStreamAsOutputSizeVideoCompositorSettings);

    compositorTestRunner.queueBitmapToAllInputs(1);
    compositorTestRunner.endCompositing();

    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(
        ImmutableList.of("different_dimensions"));
  }

  @Test
  @RequiresNonNull("testId")
  public void compositeTwoInputs_stacked_matchesExpectedBitmap() throws Exception {
    ImmutableList<ImmutableList<Effect>> inputEffectLists =
        ImmutableList.of(
            ImmutableList.of(RgbFilter.createGrayscaleFilter()),
            ImmutableList.of(),
            ImmutableList.of(RgbFilter.createInvertedFilter()));
    VideoCompositorSettings stackedFrameVideoCompositorSettings =
        new VideoCompositorSettings() {
          private static final int NUMBER_OF_INPUT_STREAMS = 3;

          @Override
          public Size getOutputSize(List<Size> inputSizes) {
            // Return the maximum width and sum of all heights.
            int width = 0;
            int height = 0;
            for (int i = 0; i < inputSizes.size(); i++) {
              width = max(width, inputSizes.get(i).getWidth());
              height += inputSizes.get(i).getHeight();
            }
            return new Size(width, height);
          }

          @Override
          public OverlaySettings getOverlaySettings(int inputId, long presentationTimeUs) {
            return new OverlaySettings.Builder()
                .setOverlayFrameAnchor(-1, -1)
                .setBackgroundFrameAnchor(-1, -1 + 2f * inputId / NUMBER_OF_INPUT_STREAMS)
                .build();
          }
        };
    compositorTestRunner =
        new VideoCompositorTestRunner(
            testId, useSharedExecutor, inputEffectLists, stackedFrameVideoCompositorSettings);

    compositorTestRunner.queueBitmapToAllInputs(1);
    compositorTestRunner.endCompositing();

    compositorTestRunner.saveAndAssertCompositedBitmapsMatchExpected(ImmutableList.of("stacked"));
  }

  /**
   * A test runner for {@link DefaultVideoCompositor} tests.
   *
   * <p>Composites input bitmaps from two input sources.
   */
  private static final class VideoCompositorTestRunner {

    public final List<TextureBitmapReader> inputBitmapReaders;
    private final int timeoutMs;
    private final LinkedHashMap<Long, Bitmap> outputTimestampsToBitmaps;
    private final List<VideoFrameProcessorTestRunner> inputVideoFrameProcessorTestRunners;
    private final VideoCompositor videoCompositor;
    private final @Nullable ExecutorService sharedExecutorService;
    private final AtomicReference<VideoFrameProcessingException> compositionException;
    private final CountDownLatch compositorEnded;
    private final String testId;

    /**
     * Creates an instance using {@link VideoCompositorSettings}.
     *
     * @param testId The {@link String} identifier for the test, used to name output files.
     * @param useSharedExecutor Whether to use a shared executor for {@link
     *     VideoFrameProcessorTestRunner} and {@link VideoCompositor} instances.
     * @param inputEffectLists {@link Effect}s to apply for {@link VideoCompositor} input sources.
     *     The size of this outer {@link List} is the amount of inputs. One inner list of {@link
     *     Effect}s is used for each input. For each input, the frame timestamp and {@code inputId}
     *     are overlaid via {@link TextOverlay} prior to its effects being applied.
     */
    public VideoCompositorTestRunner(
        String testId,
        boolean useSharedExecutor,
        ImmutableList<ImmutableList<Effect>> inputEffectLists)
        throws GlUtil.GlException, VideoFrameProcessingException {
      this(testId, useSharedExecutor, inputEffectLists, VideoCompositorSettings.DEFAULT);
    }

    /**
     * Creates an instance.
     *
     * @param testId The {@link String} identifier for the test, used to name output files.
     * @param useSharedExecutor Whether to use a shared executor for {@link
     *     VideoFrameProcessorTestRunner} and {@link VideoCompositor} instances.
     * @param inputEffectLists {@link Effect}s to apply for {@link VideoCompositor} input sources.
     *     The size of this outer {@link List} is the amount of inputs. One inner list of {@link
     *     Effect}s is used for each input. For each input, the frame timestamp and {@code inputId}
     *     are overlaid via {@link TextOverlay} prior to its effects being applied.
     * @param videoCompositorSettings The {@link VideoCompositorSettings}.
     */
    public VideoCompositorTestRunner(
        String testId,
        boolean useSharedExecutor,
        ImmutableList<ImmutableList<Effect>> inputEffectLists,
        VideoCompositorSettings videoCompositorSettings)
        throws GlUtil.GlException, VideoFrameProcessingException {
      this.testId = testId;
      timeoutMs = inputEffectLists.size() * VIDEO_FRAME_PROCESSING_WAIT_MS;
      sharedExecutorService =
          useSharedExecutor ? Util.newSingleThreadExecutor("Effect:Shared:GlThread") : null;
      EGLContext sharedEglContext = AndroidTestUtil.createOpenGlObjects();
      GlObjectsProvider glObjectsProvider =
          new DefaultGlObjectsProvider(
              /* sharedEglContext= */ useSharedExecutor ? null : sharedEglContext);

      compositionException = new AtomicReference<>();
      outputTimestampsToBitmaps = new LinkedHashMap<>();
      compositorEnded = new CountDownLatch(1);
      videoCompositor =
          new DefaultVideoCompositor(
              getApplicationContext(),
              glObjectsProvider,
              videoCompositorSettings,
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
              /* textureOutputListener= */ (outputTextureProducer,
                  outputTexture,
                  presentationTimeUs,
                  syncObject) -> {
                if (!useSharedExecutor) {
                  GlUtil.awaitSyncObject(syncObject);
                }
                outputTimestampsToBitmaps.put(
                    presentationTimeUs,
                    BitmapPixelTestUtil.createUnpremultipliedArgb8888BitmapFromFocusedGlFramebuffer(
                        outputTexture.width, outputTexture.height));
                outputTextureProducer.releaseOutputTexture(presentationTimeUs);
              },
              /* textureOutputCapacity= */ 1);
      inputBitmapReaders = new ArrayList<>();
      inputVideoFrameProcessorTestRunners = new ArrayList<>();
      for (int i = 0; i < inputEffectLists.size(); i++) {
        TextureBitmapReader textureBitmapReader = new TextureBitmapReader();
        inputBitmapReaders.add(textureBitmapReader);
        ImmutableList.Builder<Effect> effectsToApply = new ImmutableList.Builder<>();
        effectsToApply.add(createTimestampOverlayEffect(i)).addAll(inputEffectLists.get(i));
        VideoFrameProcessorTestRunner vfpTestRunner =
            createVideoFrameProcessorTestRunnerBuilder(
                    testId,
                    textureBitmapReader,
                    videoCompositor,
                    sharedExecutorService,
                    glObjectsProvider)
                .setEffects(effectsToApply.build())
                .build();
        inputVideoFrameProcessorTestRunners.add(vfpTestRunner);
      }
    }

    /**
     * Queues {@code durationSec} bitmaps, with one bitmap per second, starting from and including
     * {@code 0} seconds. Sources have a {@code frameRate} of {@code 1}.
     */
    public void queueBitmapToAllInputs(int durationSec) throws IOException, InterruptedException {
      for (int i = 0; i < inputVideoFrameProcessorTestRunners.size(); i++) {
        inputVideoFrameProcessorTestRunners
            .get(i)
            .queueInputBitmap(
                readBitmapUnpremultipliedAlpha(ORIGINAL_PNG_ASSET_PATH),
                /* durationUs= */ durationSec * C.MICROS_PER_SECOND,
                /* offsetToAddUs= */ 0L,
                /* frameRate= */ 1f);
      }
    }

    public void queueBitmapToInput(int inputId, List<Long> timestamps)
        throws IOException, InterruptedException {
      Bitmap bitmap = readBitmapUnpremultipliedAlpha(ORIGINAL_PNG_ASSET_PATH);
      inputVideoFrameProcessorTestRunners
          .get(inputId)
          .queueInputBitmaps(
              bitmap.getWidth(),
              bitmap.getHeight(),
              Pair.create(bitmap, createTimestampIterator(timestamps)));
    }

    public void endCompositing() {
      for (int i = 0; i < inputVideoFrameProcessorTestRunners.size(); i++) {
        inputVideoFrameProcessorTestRunners.get(i).signalEndOfInput();
      }
      for (int i = 0; i < inputVideoFrameProcessorTestRunners.size(); i++) {
        inputVideoFrameProcessorTestRunners.get(i).awaitFrameProcessingEnd(timeoutMs);
      }
      @Nullable Exception endCompositingException = null;
      try {
        if (!compositorEnded.await(timeoutMs, MILLISECONDS)) {
          endCompositingException = new IllegalStateException("Compositing timed out.");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        endCompositingException = e;
      }

      assertThat(compositionException.get()).isNull();
      assertThat(endCompositingException).isNull();
    }

    public Set<Long> getCompositedTimestamps() {
      return outputTimestampsToBitmaps.keySet();
    }

    /**
     * Saves bitmaps files with the {@code expectedBitmapLabels} and ensures that they match
     * corresponding expected files.
     *
     * @param expectedBitmapLabels A list of strings, where each string corresponds to the expected
     *     timestamps, in seconds, used as input for a composited frame. Typically, this will be
     *     first the timestamp from the first input, delimited by an underscore, and followed by a
     *     timestamp from the next input.
     */
    public void saveAndAssertCompositedBitmapsMatchExpected(List<String> expectedBitmapLabels)
        throws IOException {
      assertThat(outputTimestampsToBitmaps).hasSize(expectedBitmapLabels.size());

      int i = 0;
      for (Long outputTimestamp : outputTimestampsToBitmaps.keySet()) {
        String expectedBitmapLabel = expectedBitmapLabels.get(i);

        String expectedBitmapAssetPath = TEST_DIRECTORY + "output_" + expectedBitmapLabel + ".png";
        saveAndAssertBitmapMatchesExpected(
            testId,
            outputTimestampsToBitmaps.get(outputTimestamp),
            expectedBitmapLabel,
            expectedBitmapAssetPath);
        i++;
      }
    }

    public void release() {
      for (int i = 0; i < inputVideoFrameProcessorTestRunners.size(); i++) {
        inputVideoFrameProcessorTestRunners.get(i).release();
      }
      videoCompositor.release();

      if (sharedExecutorService != null) {
        try {
          sharedExecutorService.shutdown();
          if (!sharedExecutorService.awaitTermination(timeoutMs, MILLISECONDS)) {
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
                  /* textureOutputListener= */ (outputTextureProducer,
                      outputTexture,
                      presentationTimeUs,
                      syncObject) -> {
                    GlUtil.awaitSyncObject(syncObject);
                    textureBitmapReader.readBitmapUnpremultipliedAlpha(
                        outputTexture, presentationTimeUs);
                    videoCompositor.queueInputTexture(
                        inputId,
                        outputTextureProducer,
                        outputTexture,
                        ColorInfo.SRGB_BT709_FULL,
                        presentationTimeUs);
                  },
                  /* textureOutputCapacity= */ 2);
      if (executorService != null) {
        defaultVideoFrameProcessorFactoryBuilder.setExecutorService(executorService);
      }
      return new VideoFrameProcessorTestRunner.Builder()
          .setTestId(testId)
          .setVideoFrameProcessorFactory(defaultVideoFrameProcessorFactoryBuilder.build())
          .setBitmapReader(textureBitmapReader)
          .setOnEndedListener(() -> videoCompositor.signalEndOfInputSource(inputId));
    }
  }

  /**
   * Creates a timestamp overlay effect.
   *
   * <p>All input timestamps for this effect must have second values.
   */
  private static OverlayEffect createTimestampOverlayEffect(int inputId) {
    return new OverlayEffect(
        ImmutableList.of(
            new TextOverlay() {
              @Override
              public SpannableString getText(long presentationTimeUs) {
                assertThat(presentationTimeUs % C.MICROS_PER_SECOND).isEqualTo(0);
                String secondsString = String.valueOf(presentationTimeUs / C.MICROS_PER_SECOND);
                String timeString = secondsString + "s";
                SpannableString text = new SpannableString("In " + inputId + ", " + timeString);

                // Following font styles are applied for consistent text rendering between devices.
                text.setSpan(
                    new ForegroundColorSpan(Color.BLACK),
                    /* start= */ 0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(
                    new AbsoluteSizeSpan(/* size= */ 20),
                    /* start= */ 0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(
                    new TypefaceSpan(/* family= */ "sans-serif"),
                    /* start= */ 0,
                    text.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                // Following font styles increase pixel difference for the text it's applied on when
                // this text changes, but also may be implemented differently on different devices
                // or emulators, providing extraneous pixel differences. Only apply these styles to
                // the values we expect to change in the event of a failing test. Namely, only apply
                // these styles to the timestamp.
                int timestampStart = text.length() - timeString.length();
                int timestampEnd = timestampStart + secondsString.length();
                text.setSpan(
                    new BackgroundColorSpan(Color.WHITE),
                    timestampStart,
                    timestampEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    timestampStart,
                    timestampEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                text.setSpan(
                    new AbsoluteSizeSpan(/* size= */ 42),
                    timestampStart,
                    timestampEnd,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                return text;
              }

              @Override
              public OverlaySettings getOverlaySettings(long presentationTimeUs) {
                return new OverlaySettings.Builder()
                    .setBackgroundFrameAnchor(/* x= */ 0f, /* y= */ 0.5f)
                    .build();
              }
            }));
  }

  private static void saveAndAssertBitmapMatchesExpected(
      String testId, Bitmap actualBitmap, String actualBitmapLabel, String expectedBitmapAssetPath)
      throws IOException {
    maybeSaveTestBitmap(testId, actualBitmapLabel, actualBitmap, /* path= */ null);
    float averagePixelAbsoluteDifference =
        BitmapPixelTestUtil.getBitmapAveragePixelAbsoluteDifferenceArgb8888(
            readBitmapUnpremultipliedAlpha(expectedBitmapAssetPath), actualBitmap, testId);
    assertWithMessage("Pixel difference for bitmapLabel = " + actualBitmapLabel)
        .that(averagePixelAbsoluteDifference)
        .isAtMost(MAXIMUM_AVERAGE_PIXEL_ABSOLUTE_DIFFERENCE_WITH_TEXT_OVERLAY);
  }
}
