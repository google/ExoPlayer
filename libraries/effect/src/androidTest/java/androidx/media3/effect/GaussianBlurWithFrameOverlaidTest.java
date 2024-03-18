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

import static androidx.media3.effect.EffectsTestUtil.generateAndProcessFrames;
import static androidx.media3.effect.EffectsTestUtil.getAndAssertOutputBitmaps;
import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Consumer;
import androidx.media3.test.utils.TextureBitmapReader;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** Tests for {@link GaussianBlur}. */
@RunWith(AndroidJUnit4.class)
public class GaussianBlurWithFrameOverlaidTest {
  @Rule public final TestName testName = new TestName();

  private static final String ASSET_PATH =
      "test-generated-goldens/GaussianBlurWithFrameOverlaidTest";
  private static final int BLANK_FRAME_WIDTH = 200;
  private static final int BLANK_FRAME_HEIGHT = 100;
  private static final Consumer<SpannableString> TEXT_SPAN_CONSUMER =
      (text) -> {
        text.setSpan(
            new BackgroundColorSpan(Color.BLUE),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
            new ForegroundColorSpan(Color.WHITE),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
            new AbsoluteSizeSpan(/* size= */ 100),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(
            new TypefaceSpan(/* family= */ "sans-serif"),
            /* start= */ 0,
            text.length(),
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
      };

  private String testId;
  private @MonotonicNonNull TextureBitmapReader textureBitmapReader;

  @Before
  public void setUp() {
    textureBitmapReader = new TextureBitmapReader();
    testId = testName.getMethodName();
  }

  // Golden images for these tests were generated on an API 33 emulator. API 26 emulators have a
  // different text rendering implementation that leads to a larger pixel difference.

  @Test
  public void gaussianBlurWithFrameOverlaid_blursFrameAndOverlaysSharpImage() throws Exception {
    ImmutableList<Long> frameTimesUs = ImmutableList.of(32_000L);
    ImmutableList<Long> actualPresentationTimesUs =
        generateAndProcessFrames(
            BLANK_FRAME_WIDTH,
            BLANK_FRAME_HEIGHT,
            frameTimesUs,
            new GaussianBlurWithFrameOverlaid(
                /* sigma= */ 5f, /* scaleSharpX= */ 0.5f, /* scaleSharpY= */ 1f),
            textureBitmapReader,
            TEXT_SPAN_CONSUMER);

    assertThat(actualPresentationTimesUs).containsExactly(32_000L);
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId, ASSET_PATH);
  }

  @Test
  public void gaussianBlurWithFrameOverlaid_sigmaChangesWithTime_differentFramesHaveDifferentBlurs()
      throws Exception {
    ImmutableList<Long> frameTimesUs = ImmutableList.of(32_000L, 71_000L);
    ImmutableList<Long> actualPresentationTimesUs =
        generateAndProcessFrames(
            BLANK_FRAME_WIDTH,
            BLANK_FRAME_HEIGHT,
            frameTimesUs,
            new SeparableConvolution() {
              @Override
              public ConvolutionFunction1D getConvolution(long presentationTimeUs) {
                return new GaussianFunction(
                    presentationTimeUs < 40_000L ? 5f : 20f, /* numStandardDeviations= */ 2.0f);
              }

              @Override
              public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
                  throws VideoFrameProcessingException {
                return new SharpSeparableConvolutionShaderProgram(
                    context,
                    useHdr,
                    /* convolution= */ this,
                    /* scaleFactor= */
                    /* scaleSharpX= */ 0.5f,
                    /* scaleSharpY= */ 1f);
              }
            },
            textureBitmapReader,
            TEXT_SPAN_CONSUMER);

    assertThat(actualPresentationTimesUs).containsExactly(32_000L, 71_000L);
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId, ASSET_PATH);
  }
}
