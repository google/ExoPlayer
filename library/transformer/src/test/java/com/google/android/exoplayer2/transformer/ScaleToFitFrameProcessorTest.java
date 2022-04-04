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
package com.google.android.exoplayer2.transformer;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;

import android.util.Size;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link ScaleToFitFrameProcessor}.
 *
 * <p>See {@code AdvancedFrameProcessorPixelTest} for pixel tests testing {@link
 * AdvancedFrameProcessor} given a transformation matrix.
 */
@RunWith(AndroidJUnit4.class)
public final class ScaleToFitFrameProcessorTest {

  @Test
  public void getOutputSize_noEdits_leavesFramesUnchanged() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext()).build();

    scaleToFitFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = scaleToFitFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(inputWidth);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void getOutputSize_scaleNarrow_decreasesWidth() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext())
            .setScale(/* scaleX= */ .5f, /* scaleY= */ 1f)
            .build();

    scaleToFitFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = scaleToFitFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(Math.round(inputWidth * .5f));
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void getOutputSize_scaleWide_increasesWidth() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext())
            .setScale(/* scaleX= */ 2f, /* scaleY= */ 1f)
            .build();

    scaleToFitFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = scaleToFitFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(inputWidth * 2);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void getOutputSize_scaleTall_increasesHeight() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext())
            .setScale(/* scaleX= */ 1f, /* scaleY= */ 2f)
            .build();

    scaleToFitFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = scaleToFitFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(inputWidth);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight * 2);
  }

  @Test
  public void getOutputSize_rotate90_swapsDimensions() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext())
            .setRotationDegrees(90)
            .build();

    scaleToFitFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = scaleToFitFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(inputHeight);
    assertThat(outputSize.getHeight()).isEqualTo(inputWidth);
  }

  @Test
  public void getOutputSize_rotate45_changesDimensions() {
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor.Builder(getApplicationContext())
            .setRotationDegrees(45)
            .build();
    long expectedOutputWidthHeight = 247;

    scaleToFitFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = scaleToFitFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(expectedOutputWidthHeight);
    assertThat(outputSize.getHeight()).isEqualTo(expectedOutputWidthHeight);
  }
}
