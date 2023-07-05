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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.graphics.Matrix;
import com.google.android.exoplayer2.util.GlUtil;
import com.google.android.exoplayer2.util.Size;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Specifies a simple rotation and/or scale to apply in the vertex shader.
 *
 * <p>All input frames' pixels will be preserved and copied into an output frame, potentially
 * changing the width and height of the frame by scaling dimensions to fit.
 *
 * <p>The background color of the output frame will be black, with alpha = 0 if applicable.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class ScaleAndRotateTransformation implements MatrixTransformation {

  /** A builder for {@link ScaleAndRotateTransformation} instances. */
  public static final class Builder {

    // Optional fields.
    private float scaleX;
    private float scaleY;
    private float rotationDegrees;

    /** Creates a builder with default values. */
    public Builder() {
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
    @CanIgnoreReturnValue
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
     * <p>The output frame's width and height are adjusted to preserve all input pixels. The rotated
     * input frame is fitted inside an enclosing black rectangle if its edges aren't parallel to the
     * x and y axes, to form the output frame.
     *
     * @param rotationDegrees The counterclockwise rotation, in degrees.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setRotationDegrees(float rotationDegrees) {
      this.rotationDegrees = rotationDegrees;
      return this;
    }

    public ScaleAndRotateTransformation build() {
      return new ScaleAndRotateTransformation(scaleX, scaleY, rotationDegrees);
    }
  }

  /** The multiplier by which the frame will scale horizontally, along the x-axis. */
  public final float scaleX;
  /** The multiplier by which the frame will scale vertically, along the y-axis. */
  public final float scaleY;
  /** The counterclockwise rotation, in degrees. */
  public final float rotationDegrees;

  private final Matrix transformationMatrix;
  private @MonotonicNonNull Matrix adjustedTransformationMatrix;

  private ScaleAndRotateTransformation(float scaleX, float scaleY, float rotationDegrees) {
    this.scaleX = scaleX;
    this.scaleY = scaleY;
    this.rotationDegrees = rotationDegrees;
    transformationMatrix = new Matrix();
    transformationMatrix.postScale(scaleX, scaleY);
    transformationMatrix.postRotate(rotationDegrees);
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    adjustedTransformationMatrix = new Matrix(transformationMatrix);

    if (transformationMatrix.isIdentity()) {
      return new Size(inputWidth, inputHeight);
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
    float minX = Float.MAX_VALUE;
    float maxX = Float.MIN_VALUE;
    float minY = Float.MAX_VALUE;
    float maxY = Float.MIN_VALUE;
    for (float[] transformOnNdcPoint : transformOnNdcPoints) {
      adjustedTransformationMatrix.mapPoints(transformOnNdcPoint);
      minX = min(minX, transformOnNdcPoint[0]);
      maxX = max(maxX, transformOnNdcPoint[0]);
      minY = min(minY, transformOnNdcPoint[1]);
      maxY = max(maxY, transformOnNdcPoint[1]);
    }

    float scaleX = (maxX - minX) / GlUtil.LENGTH_NDC;
    float scaleY = (maxY - minY) / GlUtil.LENGTH_NDC;
    adjustedTransformationMatrix.postScale(1f / scaleX, 1f / scaleY);
    return new Size(Math.round(inputWidth * scaleX), Math.round(inputHeight * scaleY));
  }

  @Override
  public Matrix getMatrix(long presentationTimeUs) {
    return checkStateNotNull(adjustedTransformationMatrix, "configure must be called first");
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    Size outputSize = configure(inputWidth, inputHeight);
    return checkStateNotNull(adjustedTransformationMatrix).isIdentity()
        && inputWidth == outputSize.getWidth()
        && inputHeight == outputSize.getHeight();
  }
}
