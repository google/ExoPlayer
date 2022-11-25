/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.effect;

import static com.google.common.truth.Truth.assertThat;

import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link ScaleToFitTransformation}.
 *
 * <p>See {@code MatrixTextureProcessorPixelTest} for pixel tests testing {@link
 * MatrixTextureProcessor} given a transformation matrix.
 */
@RunWith(AndroidJUnit4.class)
public final class ScaleToFitTransformationTest {

  @Test
  public void configure_noEdits_leavesFramesUnchanged() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitTransformation scaleToFitTransformation =
        new ScaleToFitTransformation.Builder().build();

    Pair<Integer, Integer> outputSize = scaleToFitTransformation.configure(inputWidth, inputHeight);

    assertThat(outputSize.first).isEqualTo(inputWidth);
    assertThat(outputSize.second).isEqualTo(inputHeight);
  }

  @Test
  public void configure_scaleNarrow_decreasesWidth() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitTransformation scaleToFitTransformation =
        new ScaleToFitTransformation.Builder()
            .setScale(/* scaleX= */ .5f, /* scaleY= */ 1f)
            .build();

    Pair<Integer, Integer> outputSize = scaleToFitTransformation.configure(inputWidth, inputHeight);

    assertThat(outputSize.first).isEqualTo(Math.round(inputWidth * .5f));
    assertThat(outputSize.second).isEqualTo(inputHeight);
  }

  @Test
  public void configure_scaleWide_increasesWidth() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitTransformation scaleToFitTransformation =
        new ScaleToFitTransformation.Builder().setScale(/* scaleX= */ 2f, /* scaleY= */ 1f).build();

    Pair<Integer, Integer> outputSize = scaleToFitTransformation.configure(inputWidth, inputHeight);

    assertThat(outputSize.first).isEqualTo(inputWidth * 2);
    assertThat(outputSize.second).isEqualTo(inputHeight);
  }

  @Test
  public void configure_scaleTall_increasesHeight() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitTransformation scaleToFitTransformation =
        new ScaleToFitTransformation.Builder().setScale(/* scaleX= */ 1f, /* scaleY= */ 2f).build();

    Pair<Integer, Integer> outputSize = scaleToFitTransformation.configure(inputWidth, inputHeight);

    assertThat(outputSize.first).isEqualTo(inputWidth);
    assertThat(outputSize.second).isEqualTo(inputHeight * 2);
  }

  @Test
  public void configure_rotate90_swapsDimensions() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitTransformation scaleToFitTransformation =
        new ScaleToFitTransformation.Builder().setRotationDegrees(90).build();

    Pair<Integer, Integer> outputSize = scaleToFitTransformation.configure(inputWidth, inputHeight);

    assertThat(outputSize.first).isEqualTo(inputHeight);
    assertThat(outputSize.second).isEqualTo(inputWidth);
  }

  @Test
  public void configure_rotate45_changesDimensions() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitTransformation scaleToFitTransformation =
        new ScaleToFitTransformation.Builder().setRotationDegrees(45).build();
    long expectedOutputWidthHeight = 247;

    Pair<Integer, Integer> outputSize = scaleToFitTransformation.configure(inputWidth, inputHeight);

    assertThat(outputSize.first).isEqualTo(expectedOutputWidthHeight);
    assertThat(outputSize.second).isEqualTo(expectedOutputWidthHeight);
  }
}
