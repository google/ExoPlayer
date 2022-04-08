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

import static androidx.media3.common.util.Assertions.checkArgument;
import static androidx.media3.common.util.Assertions.checkState;
import static androidx.media3.common.util.Assertions.checkStateNotNull;

import android.content.Context;
import android.graphics.Matrix;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Controls how a frame is viewed, by cropping or changing resolution.
 *
 * <p>Cropping is applied before setting resolution.
 */
// TODO(b/213190310): Implement aspect ratio changes, etc.
@UnstableApi
public final class PresentationFrameProcessor implements GlFrameProcessor {
  /** A builder for {@link PresentationFrameProcessor} instances. */
  public static final class Builder {

    // Mandatory field.
    private final Context context;

    // Optional fields.
    private int heightPixels;
    private float cropLeft;
    private float cropRight;
    private float cropBottom;
    private float cropTop;

    /**
     * Creates a builder with default values.
     *
     * @param context The {@link Context}.
     */
    public Builder(Context context) {
      this.context = context;
      heightPixels = C.LENGTH_UNSET;
      cropLeft = -1f;
      cropRight = 1f;
      cropBottom = -1f;
      cropTop = 1f;
    }

    /**
     * Sets the output resolution using the output height.
     *
     * <p>The default value {@link C#LENGTH_UNSET} corresponds to using the same height as the
     * input. Output width of the displayed frame will scale to preserve the frame's aspect ratio
     * after other transformations.
     *
     * <p>For example, a 1920x1440 frame can be scaled to 640x480 by calling {@code
     * setResolution(480)}.
     *
     * @param height The output height of the displayed frame, in pixels.
     * @return This builder.
     */
    public Builder setResolution(int height) {
      this.heightPixels = height;
      return this;
    }

    /**
     * Crops a smaller (or larger frame), per normalized device coordinates (NDC), where the input
     * frame corresponds to the square ranging from -1 to 1 on the x and y axes.
     *
     * <p>{@code left} and {@code bottom} default to -1, and {@code right} and {@code top} default
     * to 1. To crop to a smaller subset of the input frame, use values between -1 and 1. To crop to
     * a larger frame, use values below -1 and above 1.
     *
     * <p>Width and height values set may be rescaled by {@link #setResolution(int)}.
     *
     * @param left The left edge of the output frame, in NDC. Must be less than {@code right}.
     * @param right The right edge of the output frame, in NDC. Must be greater than {@code left}.
     * @param bottom The bottom edge of the output frame, in NDC. Must be less than {@code top}.
     * @param top The top edge of the output frame, in NDC. Must be greater than {@code bottom}.
     * @return This builder.
     */
    public Builder setCrop(float left, float right, float bottom, float top) {
      checkArgument(
          right > left, "right value " + right + " should be greater than left value " + left);
      checkArgument(
          top > bottom, "top value " + top + " should be greater than bottom value " + bottom);
      cropLeft = left;
      cropRight = right;
      cropBottom = bottom;
      cropTop = top;

      return this;
    }

    public PresentationFrameProcessor build() {
      return new PresentationFrameProcessor(
          context, heightPixels, cropLeft, cropRight, cropBottom, cropTop);
    }
  }

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final Context context;
  private final int requestedHeightPixels;
  private final float cropLeft;
  private final float cropRight;
  private final float cropBottom;
  private final float cropTop;

  private int outputRotationDegrees;
  private @MonotonicNonNull Size outputSize;
  private @MonotonicNonNull Matrix transformationMatrix;
  private @MonotonicNonNull AdvancedFrameProcessor advancedFrameProcessor;

  /** Creates a new instance. */
  private PresentationFrameProcessor(
      Context context,
      int requestedHeightPixels,
      float cropLeft,
      float cropRight,
      float cropBottom,
      float cropTop) {
    this.context = context;
    this.requestedHeightPixels = requestedHeightPixels;
    this.cropLeft = cropLeft;
    this.cropRight = cropRight;
    this.cropBottom = cropBottom;
    this.cropTop = cropTop;

    outputRotationDegrees = C.LENGTH_UNSET;
    transformationMatrix = new Matrix();
  }

  @Override
  public void initialize(int inputTexId, int inputWidth, int inputHeight) throws IOException {
    configureOutputSizeAndTransformationMatrix(inputWidth, inputHeight);
    advancedFrameProcessor = new AdvancedFrameProcessor(context, transformationMatrix);
    advancedFrameProcessor.initialize(inputTexId, inputWidth, inputHeight);
  }

  @Override
  public Size getOutputSize() {
    return checkStateNotNull(outputSize);
  }

  /**
   * Returns {@link Format#rotationDegrees} for the output frame.
   *
   * <p>Return values may be {@code 0} or {@code 90} degrees.
   *
   * <p>The frame processor must be {@linkplain #initialize(int,int,int) initialized}.
   */
  public int getOutputRotationDegrees() {
    checkState(outputRotationDegrees != C.LENGTH_UNSET);
    return outputRotationDegrees;
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

  @EnsuresNonNull("transformationMatrix")
  @VisibleForTesting // Allows robolectric testing of output size calculation without OpenGL.
  /* package */ void configureOutputSizeAndTransformationMatrix(int inputWidth, int inputHeight) {
    transformationMatrix = new Matrix();

    Size cropSize = applyCrop(inputWidth, inputHeight);
    int displayWidth = cropSize.getWidth();
    int displayHeight = cropSize.getHeight();

    // Scale width and height to desired requestedHeightPixels, preserving aspect ratio.
    if (requestedHeightPixels != C.LENGTH_UNSET && requestedHeightPixels != displayHeight) {
      displayWidth = Math.round((float) requestedHeightPixels * displayWidth / displayHeight);
      displayHeight = requestedHeightPixels;
    }

    // Encoders commonly support higher maximum widths than maximum heights. Rotate the decoded
    // frame before encoding, so the encoded frame's width >= height, and set
    // outputRotationDegrees to ensure the frame is displayed in the correct orientation.
    if (displayHeight > displayWidth) {
      outputRotationDegrees = 90;
      // TODO(b/201293185): After fragment shader transformations are implemented, put
      //  postRotate in a later GlFrameProcessor.
      transformationMatrix.postRotate(outputRotationDegrees);
      outputSize = new Size(displayHeight, displayWidth);
    } else {
      outputRotationDegrees = 0;
      outputSize = new Size(displayWidth, displayHeight);
    }
  }

  @RequiresNonNull("transformationMatrix")
  private Size applyCrop(int inputWidth, int inputHeight) {
    float scaleX = (cropRight - cropLeft) / GlUtil.LENGTH_NDC;
    float scaleY = (cropTop - cropBottom) / GlUtil.LENGTH_NDC;
    float centerX = (cropLeft + cropRight) / 2;
    float centerY = (cropBottom + cropTop) / 2;

    transformationMatrix.postTranslate(-centerX, -centerY);
    transformationMatrix.postScale(1f / scaleX, 1f / scaleY);

    int outputWidth = Math.round(inputWidth * scaleX);
    int outputHeight = Math.round(inputHeight * scaleY);
    return new Size(outputWidth, outputHeight);
  }
}
