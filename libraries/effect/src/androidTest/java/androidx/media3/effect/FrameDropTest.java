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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.effect.EffectsTestUtil.generateAndProcessFrames;
import static androidx.media3.effect.EffectsTestUtil.getAndAssertOutputBitmaps;
import static com.google.common.truth.Truth.assertThat;

import android.graphics.Color;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
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

/** Tests for {@link FrameDropEffect}. */
@RunWith(AndroidJUnit4.class)
public class FrameDropTest {
  @Rule public final TestName testName = new TestName();

  private static final String ASSET_PATH = "test-generated-goldens/FrameDropTest";

  private @MonotonicNonNull TextureBitmapReader textureBitmapReader;
  private String testId;

  @Before
  public void setUp() {
    textureBitmapReader = new TextureBitmapReader();
    testId = testName.getMethodName();
  }

  @Test
  public void frameDrop_withDefaultStrategy_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    ImmutableList<Long> frameTimesUs =
        ImmutableList.of(0L, 16_000L, 32_000L, 48_000L, 58_000L, 71_000L, 86_000L);
    FrameDropEffect frameDropEffect =
        FrameDropEffect.createDefaultFrameDropEffect(/* targetFrameRate= */ 30);

    ImmutableList<Long> actualPresentationTimesUs =
        generateAndProcessBlackTimeStampedFrames(frameTimesUs, frameDropEffect);

    assertThat(actualPresentationTimesUs).containsExactly(0L, 32_000L, 71_000L).inOrder();
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId, ASSET_PATH);
  }

  @Test
  public void frameDrop_withSimpleStrategy_outputsFramesAtTheCorrectPresentationTimesUs()
      throws Exception {
    ImmutableList<Long> frameTimesUs =
        ImmutableList.of(0L, 250_000L, 500_000L, 750_000L, 1_000_000L, 1_500_000L);
    FrameDropEffect frameDropEffect =
        FrameDropEffect.createSimpleFrameDropEffect(
            /* expectedFrameRate= */ 6, /* targetFrameRate= */ 2);

    ImmutableList<Long> actualPresentationTimesUs =
        generateAndProcessBlackTimeStampedFrames(frameTimesUs, frameDropEffect);

    assertThat(actualPresentationTimesUs).containsExactly(0L, 750_000L).inOrder();
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId, ASSET_PATH);
  }

  @Test
  public void frameDrop_withSimpleStrategy_outputsAllFrames() throws Exception {
    ImmutableList<Long> frameTimesUs = ImmutableList.of(0L, 333_333L, 666_667L);
    FrameDropEffect frameDropEffect =
        FrameDropEffect.createSimpleFrameDropEffect(
            /* expectedFrameRate= */ 3, /* targetFrameRate= */ 3);

    ImmutableList<Long> actualPresentationTimesUs =
        generateAndProcessBlackTimeStampedFrames(frameTimesUs, frameDropEffect);

    assertThat(actualPresentationTimesUs).containsExactly(0L, 333_333L, 666_667L).inOrder();
    getAndAssertOutputBitmaps(textureBitmapReader, actualPresentationTimesUs, testId, ASSET_PATH);
  }

  private ImmutableList<Long> generateAndProcessBlackTimeStampedFrames(
      ImmutableList<Long> frameTimesUs, FrameDropEffect frameDropEffect) throws Exception {
    int blankFrameWidth = 100;
    int blankFrameHeight = 50;
    Consumer<SpannableString> textSpanConsumer =
        (text) -> {
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
        };
    return generateAndProcessFrames(
        blankFrameWidth,
        blankFrameHeight,
        frameTimesUs,
        frameDropEffect,
        checkNotNull(textureBitmapReader),
        textSpanConsumer);
  }
}
