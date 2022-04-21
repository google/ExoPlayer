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
 * <p>See {@code PresentationFrameProcessorPixelTest} for pixel tests testing {@link
 * PresentationFrameProcessor}.
 */
@RunWith(AndroidJUnit4.class)
public final class PresentationFrameProcessorTest {
  @Test
  public void getOutputSize_noEdits_leavesFramesUnchanged() {
    int inputWidth = 200;
    int inputHeight = 150;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder().build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(inputWidth);
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void getOutputSize_setResolution_changesDimensions() {
    int inputWidth = 200;
    int inputHeight = 150;
    int requestedHeight = 300;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder().setResolution(requestedHeight).build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

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
        new PresentationFrameProcessor.Builder().setCrop(left, right, bottom, top).build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

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
        new PresentationFrameProcessor.Builder()
            .setCrop(left, right, bottom, top)
            .setResolution(requestedHeight)
            .build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

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
        new PresentationFrameProcessor.Builder()
            .setResolution(requestedHeight)
            .setCrop(left, right, bottom, top)
            .build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    int expectedPostCropWidth = Math.round(inputWidth * (right - left) / GlUtil.LENGTH_NDC);
    int expectedPostCropHeight = Math.round(inputHeight * (top - bottom) / GlUtil.LENGTH_NDC);
    assertThat(outputSize.getWidth())
        .isEqualTo(
            Math.round((float) requestedHeight * expectedPostCropWidth / expectedPostCropHeight));
    assertThat(outputSize.getHeight()).isEqualTo(requestedHeight);
  }

  @Test
  public void getOutputSize_setAspectRatio_changesDimensions() {
    int inputWidth = 300;
    int inputHeight = 200;
    float aspectRatio = 2f;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder()
            .setAspectRatio(aspectRatio, PresentationFrameProcessor.LAYOUT_SCALE_TO_FIT)
            .build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(Math.round(aspectRatio * inputHeight));
    assertThat(outputSize.getHeight()).isEqualTo(inputHeight);
  }

  @Test
  public void getOutputSize_setAspectRatioAndResolution_changesDimensions() {
    int inputWidth = 300;
    int inputHeight = 200;
    float aspectRatio = 2f;
    int requestedHeight = 100;
    PresentationFrameProcessor presentationFrameProcessor =
        new PresentationFrameProcessor.Builder()
            .setAspectRatio(aspectRatio, PresentationFrameProcessor.LAYOUT_SCALE_TO_FIT)
            .setResolution(requestedHeight)
            .build();

    presentationFrameProcessor.configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    Size outputSize = presentationFrameProcessor.getOutputSize();

    assertThat(outputSize.getWidth()).isEqualTo(Math.round(aspectRatio * requestedHeight));
    assertThat(outputSize.getHeight()).isEqualTo(requestedHeight);
  }

  @Test
  public void getOutputSize_setAspectRatioAndCrop_throwsIllegalStateException() {
    PresentationFrameProcessor.Builder presentationFrameProcessor =
        new PresentationFrameProcessor.Builder()
            .setAspectRatio(/* aspectRatio= */ 2f, PresentationFrameProcessor.LAYOUT_SCALE_TO_FIT);

    assertThrows(
        IllegalStateException.class,
        () ->
            presentationFrameProcessor.setCrop(
                /* left= */ -.5f, /* right= */ .5f, /* bottom= */ .5f, /* top= */ 1f));
  }

  @Test
  public void getOutputSize_setCropAndAspectRatio_throwsIllegalStateException() {
    PresentationFrameProcessor.Builder presentationFrameProcessor =
        new PresentationFrameProcessor.Builder()
            .setCrop(/* left= */ -.5f, /* right= */ .5f, /* bottom= */ .5f, /* top= */ 1f);

    assertThrows(
        IllegalStateException.class,
        () ->
            presentationFrameProcessor.setAspectRatio(
                /* aspectRatio= */ 2f, PresentationFrameProcessor.LAYOUT_SCALE_TO_FIT));
  }
}
