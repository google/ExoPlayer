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

import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Matrix;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Applies a simple rotation and/or scale in the vertex shader. All input frames' pixels will be
 * preserved, potentially changing the width and height of the frame by scaling dimensions to fit.
 * The background color will default to black.
 */
public final class ScaleToFitFrameProcessor implements GlFrameProcessor {

  /** A builder for {@link ScaleToFitFrameProcessor} instances. */
  public static final class Builder {
    // Mandatory field.
    private final Context context;

    // Optional fields.
    private float scaleX;
    private float scaleY;
    private float rotationDegrees;

    /**
     * Creates a builder with default values.
     *
     * @param context The {@link Context}.
     */
    public Builder(Context context) {
      this.context = context;

      scaleX = 1;
      scaleY = 1;
      rotationDegrees = 0;
    }

    /**
     * Sets the x and y axis scaling factors to apply to each frame's width and height.
     *
     * <p>The values default to 1, which corresponds to not scaling along both axes.
     *
     * @param scaleX The multiplier by which the frame will scale horizontally, along the x-axis.
     * @param scaleY The multiplier by which the frame will scale vertically, along the y-axis.
     * @return This builder.
     */
    public Builder setScale(float scaleX, float scaleY) {
      this.scaleX = scaleX;
      this.scaleY = scaleY;
      return this;
    }

    /**
     * Sets the counterclockwise rotation degrees.
     *
     * <p>The default value, 0, corresponds to not applying any rotation.
     *
     * @param rotationDegrees The counterclockwise rotation, in degrees.
     * @return This builder.
     */
    public Builder setRotationDegrees(float rotationDegrees) {
      this.rotationDegrees = rotationDegrees;
      return this;
    }

    public ScaleToFitFrameProcessor build() {
      return new ScaleToFitFrameProcessor(context, scaleX, scaleY, rotationDegrees);
    }
  }

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final Context context;
  private final Matrix transformationMatrix;

  private @MonotonicNonNull AdvancedFrameProcessor advancedFrameProcessor;
  private @MonotonicNonNull Size outputSize;
  private @MonotonicNonNull Matrix adjustedTransformationMatrix;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param scaleX The multiplier by which the frame will scale horizontally, along the x-axis.
   * @param scaleY The multiplier by which the frame will scale vertically, along the y-axis.
   * @param rotationDegrees How much to rotate the frame counterclockwise, in degrees.
   */
  private ScaleToFitFrameProcessor(
      Context context, float scaleX, float scaleY, float rotationDegrees) {

    this.context = context;
    this.transformationMatrix = new Matrix();
    this.transformationMatrix.postScale(scaleX, scaleY);
    this.transformationMatrix.postRotate(rotationDegrees);
  }

  @Override
  public void initialize(int inputTexId, int inputWidth, int inputHeight) throws IOException {
    configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    advancedFrameProcessor = new AdvancedFrameProcessor(context, adjustedTransformationMatrix);
    advancedFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
  }

  @Override
  public Size getOutputSize() {
    return checkStateNotNull(outputSize);
  }

  @Override
  public void updateProgramAndDraw(long presentationTimeUs) {
    checkStateNotNull(advancedFrameProcessor).updateProgramAndDraw(presentationTimeUs);
  }

  @Override
  public void release() {
    if (advancedFrameProcessor != null) {
      advancedFrameProcessor.release();
    }
  }

  @EnsuresNonNull("adjustedTransformationMatrix")
  @VisibleForTesting // Allows roboletric testing of output size calculation without OpenGL.
  /* package */ void configureOutputSizeAndTransformationMatrix(int inputWidth, int inputHeight) {
    adjustedTransformationMatrix = new Matrix(transformationMatrix);

    if (transformationMatrix.isIdentity()) {
      outputSize = new Size(inputWidth, inputHeight);
      return;
    }

    float inputAspectRatio = (float) inputWidth / inputHeight;
    // Scale frames by inputAspectRatio, to account for OpenGL's normalized device
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

    float ndcWidthAndHeight = 2f; // Length from -1 to 1.
    float xScale = (xMax - xMin) / ndcWidthAndHeight;
    float yScale = (yMax - yMin) / ndcWidthAndHeight;
    adjustedTransformationMatrix.postScale(1f / xScale, 1f / yScale);
    outputSize = new Size(Math.round(inputWidth * xScale), Math.round(inputHeight * yScale));
  }
}
