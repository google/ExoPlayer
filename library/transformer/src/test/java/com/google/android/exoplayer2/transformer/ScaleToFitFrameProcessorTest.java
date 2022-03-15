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
import static org.junit.Assert.assertThrows;

import android.graphics.Matrix;
import android.util.Pair;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.C;
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
  public void configureOutputDimensions_noEdits_producesExpectedOutput() {
    Matrix identityMatrix = new Matrix();
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor(getApplicationContext(), identityMatrix, C.LENGTH_UNSET);

    Pair<Integer, Integer> outputDimensions =
        scaleToFitFrameProcessor.configureOutputDimensions(inputWidth, inputHeight);

    assertThat(scaleToFitFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(scaleToFitFrameProcessor.shouldProcess()).isFalse();
    assertThat(outputDimensions.first).isEqualTo(inputWidth);
    assertThat(outputDimensions.second).isEqualTo(inputHeight);
  }

  @Test
  public void initializeBeforeConfigure_throwsIllegalStateException() {
    Matrix identityMatrix = new Matrix();
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor(getApplicationContext(), identityMatrix, C.LENGTH_UNSET);

    // configureOutputDimensions not called before initialize.
    assertThrows(
        IllegalStateException.class,
        () -> scaleToFitFrameProcessor.initialize(/* inputTexId= */ 0));
  }

  @Test
  public void getOutputRotationDegreesBeforeConfigure_throwsIllegalStateException() {
    Matrix identityMatrix = new Matrix();
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor(getApplicationContext(), identityMatrix, C.LENGTH_UNSET);

    // configureOutputDimensions not called before initialize.
    assertThrows(IllegalStateException.class, scaleToFitFrameProcessor::getOutputRotationDegrees);
  }

  @Test
  public void configureOutputDimensions_scaleNarrow_producesExpectedOutput() {
    Matrix scaleNarrowMatrix = new Matrix();
    scaleNarrowMatrix.postScale(/* sx= */ .5f, /* sy= */ 1.0f);
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor(getApplicationContext(), scaleNarrowMatrix, C.LENGTH_UNSET);

    Pair<Integer, Integer> outputDimensions =
        scaleToFitFrameProcessor.configureOutputDimensions(inputWidth, inputHeight);

    assertThat(scaleToFitFrameProcessor.getOutputRotationDegrees()).isEqualTo(90);
    assertThat(scaleToFitFrameProcessor.shouldProcess()).isTrue();
    assertThat(outputDimensions.first).isEqualTo(inputHeight);
    assertThat(outputDimensions.second).isEqualTo(Math.round(inputWidth * .5f));
  }

  @Test
  public void configureOutputDimensions_scaleWide_producesExpectedOutput() {
    Matrix scaleNarrowMatrix = new Matrix();
    scaleNarrowMatrix.postScale(/* sx= */ 2f, /* sy= */ 1.0f);
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor(getApplicationContext(), scaleNarrowMatrix, C.LENGTH_UNSET);

    Pair<Integer, Integer> outputDimensions =
        scaleToFitFrameProcessor.configureOutputDimensions(inputWidth, inputHeight);

    assertThat(scaleToFitFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(scaleToFitFrameProcessor.shouldProcess()).isTrue();
    assertThat(outputDimensions.first).isEqualTo(inputWidth * 2);
    assertThat(outputDimensions.second).isEqualTo(inputHeight);
  }

  @Test
  public void configureOutputDimensions_rotate90_producesExpectedOutput() {
    Matrix rotate90Matrix = new Matrix();
    rotate90Matrix.postRotate(/* degrees= */ 90);
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor(getApplicationContext(), rotate90Matrix, C.LENGTH_UNSET);

    Pair<Integer, Integer> outputDimensions =
        scaleToFitFrameProcessor.configureOutputDimensions(inputWidth, inputHeight);

    assertThat(scaleToFitFrameProcessor.getOutputRotationDegrees()).isEqualTo(90);
    assertThat(scaleToFitFrameProcessor.shouldProcess()).isTrue();
    assertThat(outputDimensions.first).isEqualTo(inputWidth);
    assertThat(outputDimensions.second).isEqualTo(inputHeight);
  }

  @Test
  public void configureOutputDimensions_rotate45_producesExpectedOutput() {
    Matrix rotate45Matrix = new Matrix();
    rotate45Matrix.postRotate(/* degrees= */ 45);
    int inputWidth = 200;
    int inputHeight = 150;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor(getApplicationContext(), rotate45Matrix, C.LENGTH_UNSET);
    long expectedOutputWidthHeight = 247;

    Pair<Integer, Integer> outputDimensions =
        scaleToFitFrameProcessor.configureOutputDimensions(inputWidth, inputHeight);

    assertThat(scaleToFitFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(scaleToFitFrameProcessor.shouldProcess()).isTrue();
    assertThat(outputDimensions.first).isEqualTo(expectedOutputWidthHeight);
    assertThat(outputDimensions.second).isEqualTo(expectedOutputWidthHeight);
  }

  @Test
  public void configureOutputDimensions_setResolution_producesExpectedOutput() {
    Matrix identityMatrix = new Matrix();
    int inputWidth = 200;
    int inputHeight = 150;
    int requestedHeight = 300;
    ScaleToFitFrameProcessor scaleToFitFrameProcessor =
        new ScaleToFitFrameProcessor(getApplicationContext(), identityMatrix, requestedHeight);

    Pair<Integer, Integer> outputDimensions =
        scaleToFitFrameProcessor.configureOutputDimensions(inputWidth, inputHeight);

    assertThat(scaleToFitFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(scaleToFitFrameProcessor.shouldProcess()).isTrue();
    assertThat(outputDimensions.first).isEqualTo(requestedHeight * inputWidth / inputHeight);
    assertThat(outputDimensions.second).isEqualTo(requestedHeight);
  }
}
