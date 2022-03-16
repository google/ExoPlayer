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

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Matrix;
import android.util.Size;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Applies a simple rotation and/or scale in the vertex shader. All input frames' pixels will be
 * preserved, potentially changing the width and height of the video by scaling dimensions to fit.
 * The background color will default to black.
 */
/* package */ final class ScaleToFitFrameProcessor implements GlFrameProcessor {

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final Context context;
  private final Matrix transformationMatrix;
  private final int requestedHeight;

  private @MonotonicNonNull AdvancedFrameProcessor advancedFrameProcessor;
  private int inputWidth;
  private int inputHeight;
  private int outputWidth;
  private int outputHeight;
  private int outputRotationDegrees;
  private @MonotonicNonNull Matrix adjustedTransformationMatrix;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param transformationMatrix The transformation matrix to apply to each frame.
   * @param requestedHeight The height of the output frame, in pixels.
   */
  public ScaleToFitFrameProcessor(
      Context context, Matrix transformationMatrix, int requestedHeight) {
    // TODO(b/201293185): Replace transformationMatrix parameter with scale and rotation.

    this.context = context;
    this.transformationMatrix = new Matrix(transformationMatrix);
    this.requestedHeight = requestedHeight;

    inputWidth = C.LENGTH_UNSET;
    inputHeight = C.LENGTH_UNSET;
    outputWidth = C.LENGTH_UNSET;
    outputHeight = C.LENGTH_UNSET;
    outputRotationDegrees = C.LENGTH_UNSET;
  }

  /**
   * Returns {@link Format#rotationDegrees} for the output frame.
   *
   * <p>Return values may be {@code 0} or {@code 90} degrees.
   *
   * <p>This method can only be called after {@link #configureOutputSize(int, int)}.
   */
  public int getOutputRotationDegrees() {
    checkState(outputRotationDegrees != C.LENGTH_UNSET);
    return outputRotationDegrees;
  }

  /**
   * Returns whether this ScaleToFitFrameProcessor will apply any changes on a frame.
   *
   * <p>The ScaleToFitFrameProcessor should only be used if this returns true.
   *
   * <p>This method can only be called after {@link #configureOutputSize(int, int)}.
   */
  @RequiresNonNull("adjustedTransformationMatrix")
  public boolean shouldProcess() {
    return inputWidth != outputWidth
        || inputHeight != outputHeight
        || !adjustedTransformationMatrix.isIdentity();
  }

  @Override
  @EnsuresNonNull("adjustedTransformationMatrix")
  public Size configureOutputSize(int inputWidth, int inputHeight) {
    this.inputWidth = inputWidth;
    this.inputHeight = inputHeight;
    adjustedTransformationMatrix = new Matrix(transformationMatrix);

    int displayWidth = inputWidth;
    int displayHeight = inputHeight;
    if (!transformationMatrix.isIdentity()) {
      float inputAspectRatio = (float) inputWidth / inputHeight;
      // Scale frames by inputAspectRatio, to account for FrameEditor's normalized device
      // coordinates (NDC) (a square from -1 to 1 for both x and y) and preserve rectangular
      // display of input pixels during transformations (ex. rotations). With scaling,
      // transformationMatrix operations operate on a rectangle for x from -inputAspectRatio to
      // inputAspectRatio, and y from -1 to 1.
      adjustedTransformationMatrix.preScale(/* sx= */ inputAspectRatio, /* sy= */ 1f);
      adjustedTransformationMatrix.postScale(/* sx= */ 1f / inputAspectRatio, /* sy= */ 1f);

      // Modify transformationMatrix to keep input pixels.
      float[][] transformOnNdcPoints = {{-1, -1, 0, 1}, {-1, 1, 0, 1}, {1, -1, 0, 1}, {1, 1, 0, 1}};
      float xMin = Float.MAX_VALUE;
      float xMax = Float.MIN_VALUE;
      float yMin = Float.MAX_VALUE;
      float yMax = Float.MIN_VALUE;
      for (float[] transformOnNdcPoint : transformOnNdcPoints) {
        adjustedTransformationMatrix.mapPoints(transformOnNdcPoint);
        xMin = min(xMin, transformOnNdcPoint[0]);
        xMax = max(xMax, transformOnNdcPoint[0]);
        yMin = min(yMin, transformOnNdcPoint[1]);
        yMax = max(yMax, transformOnNdcPoint[1]);
      }

      float xCenter = (xMax + xMin) / 2f;
      float yCenter = (yMax + yMin) / 2f;
      adjustedTransformationMatrix.postTranslate(-xCenter, -yCenter);

      float ndcWidthAndHeight = 2f; // Length from -1 to 1.
      float xScale = (xMax - xMin) / ndcWidthAndHeight;
      float yScale = (yMax - yMin) / ndcWidthAndHeight;
      adjustedTransformationMatrix.postScale(1f / xScale, 1f / yScale);
      displayWidth = Math.round(inputWidth * xScale);
      displayHeight = Math.round(inputHeight * yScale);
    }

    // TODO(b/214975934): Move following requestedHeight and outputRotationDegrees logic into
    //  separate GlFrameProcessors (ex. Presentation).

    // Scale width and height to desired requestedHeight, preserving aspect ratio.
    if (requestedHeight != C.LENGTH_UNSET && requestedHeight != displayHeight) {
      displayWidth = Math.round((float) requestedHeight * displayWidth / displayHeight);
      displayHeight = requestedHeight;
    }

    // Encoders commonly support higher maximum widths than maximum heights. Rotate the decoded
    // video before encoding, so the encoded video's width >= height, and set
    // outputRotationDegrees to ensure the video is displayed in the correct orientation.
    if (displayHeight > displayWidth) {
      outputRotationDegrees = 90;
      outputWidth = displayHeight;
      outputHeight = displayWidth;
      // TODO(b/201293185): After fragment shader transformations are implemented, put
      //  postRotate in a later GlFrameProcessor.
      adjustedTransformationMatrix.postRotate(outputRotationDegrees);
    } else {
      outputRotationDegrees = 0;
      outputWidth = displayWidth;
      outputHeight = displayHeight;
    }

    return new Size(outputWidth, outputHeight);
  }

  @Override
  public void initialize(int inputTexId) throws IOException {
    checkStateNotNull(adjustedTransformationMatrix);
    advancedFrameProcessor = new AdvancedFrameProcessor(context, adjustedTransformationMatrix);
    advancedFrameProcessor.configureOutputSize(inputWidth, inputHeight);
    advancedFrameProcessor.initialize(inputTexId);
  }

  @Override
  public void updateProgramAndDraw(long presentationTimeNs) {
    checkStateNotNull(advancedFrameProcessor).updateProgramAndDraw(presentationTimeNs);
  }

  @Override
  public void release() {
    if (advancedFrameProcessor != null) {
      advancedFrameProcessor.release();
    }
  }
}
