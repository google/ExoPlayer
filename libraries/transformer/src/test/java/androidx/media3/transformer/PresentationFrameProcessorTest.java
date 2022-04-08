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
package androidx.media3.transformer;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.util.Size;
import androidx.media3.common.util.GlUtil;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Unit tests for {@link PresentationFrameProcessor}.
 *
 * <p>See {@code AdvancedFrameProcessorPixelTest} for pixel tests testing {@link
 * AdvancedFrameProcessor} given a transformation matrix.
 */
@RunWith(AndroidJUnit4.class)
public final class PresentationFrameProcessorTest {
  @Test
  public void getOutputSize_noEditsLandscape_leavesFramesUnchanged() {
    int inputWidth = 200;
    int inputHeight = 150;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext()).build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(presentationFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(outputSize.getWidth()).isEqualTo(inputWidth);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void getOutputSize_noEditsSquare_leavesFramesUnchanged() {
    int inputWidth = 150;
    int inputHeight = 150;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext()).build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(presentationFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(outputSize.getWidth()).isEqualTo(inputWidth);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void getOutputSize_noEditsPortrait_flipsOrientation() {
    int inputWidth = 150;
    int inputHeight = 200;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext()).build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(presentationFrameProcessor.getOutputRotationDegrees()).isEqualTo(90);
    assertThat(outputSize.getWidth()).isEqualTo(inputHeight);
    assertThat(outputSize.getHeight()).isEqualTo(inputWidth);
  }

  @Test
  public void getOutputSize_setResolution_changesDimensions() {
    int inputWidth = 200;
    int inputHeight = 150;
    int requestedHeight = 300;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setResolution(requestedHeight)
            .build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(presentationFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    assertThat(outputSize.getWidth()).isEqualTo(requestedHeight * inputWidth / inputHeight);
    assertThat(outputSize.getHeight()).isEqualTo(requestedHeight);
  }

  @Test
  public void getOutputSize_setCrop_changesDimensions() {
    int inputWidth = 300;
    int inputHeight = 200;
    float left = -.5f;
    float right = .5f;
    float bottom = .5f;
    float top = 1f;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setCrop(left, right, bottom, top)
            .build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(presentationFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    int expectedPostCropWidth = Math.round(inputWidth * (right - left) / GlUtil.LENGTH_NDC);
    int expectedPostCropHeight = Math.round(inputHeight * (top - bottom) / GlUtil.LENGTH_NDC);
    assertThat(outputSize.getWidth()).isEqualTo(expectedPostCropWidth);
    assertThat(outputSize.getHeight()).isEqualTo(expectedPostCropHeight);
  }

  @Test
  public void getOutputSize_setCropAndSetResolution_changesDimensions() {
    int inputWidth = 300;
    int inputHeight = 200;
    float left = -.5f;
    float right = .5f;
    float bottom = .5f;
    float top = 1f;
    int requestedHeight = 100;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setCrop(left, right, bottom, top)
            .setResolution(100)
            .build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(presentationFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    int expectedPostCropWidth = Math.round(inputWidth * (right - left) / GlUtil.LENGTH_NDC);
    int expectedPostCropHeight = Math.round(inputHeight * (top - bottom) / GlUtil.LENGTH_NDC);
    assertThat(outputSize.getWidth())
        .isEqualTo(
            Math.round((float) requestedHeight * expectedPostCropWidth / expectedPostCropHeight));
    assertThat(outputSize.getHeight()).isEqualTo(requestedHeight);
  }

  @Test
  public void getOutputSize_setResolutionAndCrop_changesDimensions() {
    int inputWidth = 300;
    int inputHeight = 200;
    float left = -.5f;
    float right = .5f;
    float bottom = .5f;
    float top = 1f;
    int requestedHeight = 100;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext())
            .setResolution(100)
            .setCrop(left, right, bottom, top)
            .build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(presentationFrameProcessor.getOutputRotationDegrees()).isEqualTo(0);
    int expectedPostCropWidth = Math.round(inputWidth * (right - left) / GlUtil.LENGTH_NDC);
    int expectedPostCropHeight = Math.round(inputHeight * (top - bottom) / GlUtil.LENGTH_NDC);
    assertThat(outputSize.getWidth())
        .isEqualTo(
            Math.round((float) requestedHeight * expectedPostCropWidth / expectedPostCropHeight));
    assertThat(outputSize.getHeight()).isEqualTo(requestedHeight);
  }

  @Test
  public void getOutputRotationDegreesBeforeConfigure_throwsIllegalStateException() {
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder(getApplicationContext()).build();

    // configureOutputSize not called before getOutputRotationDegrees.
    assertThrows(IllegalStateException.class, presentationFrameProcessor::getOutputRotationDegrees);
  }
}
