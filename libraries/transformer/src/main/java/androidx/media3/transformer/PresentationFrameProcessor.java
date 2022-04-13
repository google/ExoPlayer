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
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.content.Context;
import android.graphics.Matrix;
import android.util.Size;
import androidx.annotation.IntDef;
import androidx.annotation.VisibleForTesting;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import java.io.IOException;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Controls how a frame is viewed, by cropping, changing aspect ratio, or changing resolution.
 *
 * <p>Cropping or aspect ratio is applied before setting resolution.
 */
@UnstableApi
public final class PresentationFrameProcessor implements GlFrameProcessor {
  /**
   * Strategies for how to apply the presented frame. One of {@link #SCALE_TO_FIT}, {@link
   * #SCALE_TO_FIT_WITH_CROP}, or {@link #STRETCH_TO_FIT}.
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({SCALE_TO_FIT, SCALE_TO_FIT_WITH_CROP, STRETCH_TO_FIT})
  public @interface PresentationStrategy {}
  /**
   * Empty pixels added above and below the input frame (for letterboxing), or to the left and right
   * of the input frame (for pillarboxing), until the desired aspect ratio is achieved. All input
   * frame pixels will be within the output frame.
   */
  public static final int SCALE_TO_FIT = 0;
  /**
   * Pixels cropped from the input frame, until the desired aspect ratio is achieved. Pixels will be
   * cropped either from the top and bottom, or from the left and right sides, of the input frame.
   */
  public static final int SCALE_TO_FIT_WITH_CROP = 1;
  /** Frame stretched larger on the x or y axes to fit the desired aspect ratio. */
  public static final int STRETCH_TO_FIT = 2;

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
    private float aspectRatio;
    private @PresentationStrategy int presentationStrategy;

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
      aspectRatio = C.LENGTH_UNSET;
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
     * <p>Only one of {@code setCrop} or {@link #setAspectRatio(float, int)} can be called for one
     * {@link PresentationFrameProcessor}.
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
      checkState(
          aspectRatio == C.LENGTH_UNSET,
          "setAspectRatio and setCrop cannot be called in the same instance");
      cropLeft = left;
      cropRight = right;
      cropBottom = bottom;
      cropTop = top;

      return this;
    }

    /**
     * Resize a frame's width or height to conform to an {@code aspectRatio}, given a {@link
     * PresentationStrategy}, and leaving input pixels unchanged.
     *
     * <p>Width and height values set here may be rescaled by {@link #setResolution(int)}.
     *
     * <p>Only one of {@link #setCrop(float, float, float, float)} or {@code setAspectRatio} can be
     * called for one {@link PresentationFrameProcessor}.
     *
     * @param aspectRatio The aspect ratio of the output frame, defined as width/height. Must be
     *     positive.
     * @return This builder.
     */
    public Builder setAspectRatio(
        float aspectRatio, @PresentationStrategy int presentationStrategy) {
      checkArgument(aspectRatio > 0, "aspect ratio " + aspectRatio + " must be positive");
      checkArgument(
          presentationStrategy == SCALE_TO_FIT
              || presentationStrategy == SCALE_TO_FIT_WITH_CROP
              || presentationStrategy == STRETCH_TO_FIT,
          "invalid presentationStrategy " + presentationStrategy);
      checkState(
          cropLeft == -1f && cropRight == 1f && cropBottom == -1f && cropTop == 1f,
          "setAspectRatio and setCrop cannot be called in the same instance");
      this.aspectRatio = aspectRatio;
      this.presentationStrategy = presentationStrategy;
      return this;
    }

    public PresentationFrameProcessor build() {
      return new PresentationFrameProcessor(
          context,
          heightPixels,
          cropLeft,
          cropRight,
          cropBottom,
          cropTop,
          aspectRatio,
          presentationStrategy);
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
  private final float requestedAspectRatio;
  private final @PresentationStrategy int presentationStrategy;

  private int outputRotationDegrees;
  private int outputWidth;
  private int outputHeight;
  private @MonotonicNonNull Matrix transformationMatrix;
  private @MonotonicNonNull AdvancedFrameProcessor advancedFrameProcessor;

  /** Creates a new instance. */
  private PresentationFrameProcessor(
      Context context,
      int requestedHeightPixels,
      float cropLeft,
      float cropRight,
      float cropBottom,
      float cropTop,
      float requestedAspectRatio,
      @PresentationStrategy int presentationStrategy) {
    this.context = context;
    this.requestedHeightPixels = requestedHeightPixels;
    this.cropLeft = cropLeft;
    this.cropRight = cropRight;
    this.cropBottom = cropBottom;
    this.cropTop = cropTop;
    this.requestedAspectRatio = requestedAspectRatio;
    this.presentationStrategy = presentationStrategy;

    outputWidth = C.LENGTH_UNSET;
    outputHeight = C.LENGTH_UNSET;
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
    checkState(
        outputWidth != C.LENGTH_UNSET && outputHeight != C.LENGTH_UNSET,
        "configureOutputSizeAndTransformationMatrix must be called before getOutputSize");
    return new Size(outputWidth, outputHeight);
  }

  /**
   * Returns {@link Format#rotationDegrees} for the output frame.
   *
   * <p>Return values may be {@code 0} or {@code 90} degrees.
   *
   * <p>The frame processor must be {@linkplain #initialize(int,int,int) initialized}.
   */
  public int getOutputRotationDegrees() {
    checkState(
        outputRotationDegrees != C.LENGTH_UNSET,
        "configureOutputSizeAndTransformationMatrix must be called before"
            + " getOutputRotationDegrees");
    return outputRotationDegrees;
  }

  @Override
  public void drawFrame(long presentationTimeUs) {
    checkStateNotNull(advancedFrameProcessor).drawFrame(presentationTimeUs);
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
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");
    transformationMatrix = new Matrix();
    outputWidth = inputWidth;
    outputHeight = inputHeight;

    if (cropLeft != -1f || cropRight != 1f || cropBottom != -1f || cropTop != 1f) {
      checkState(
          requestedAspectRatio == C.LENGTH_UNSET,
          "aspect ratio and crop cannot both be set in the same instance");
      applyCrop();
    } else if (requestedAspectRatio != C.LENGTH_UNSET) {
      applyAspectRatio();
    }

    // Scale width and height to desired requestedHeightPixels, preserving aspect ratio.
    if (requestedHeightPixels != C.LENGTH_UNSET && requestedHeightPixels != outputHeight) {
      outputWidth = Math.round((float) requestedHeightPixels * outputWidth / outputHeight);
      outputHeight = requestedHeightPixels;
    }

    // Encoders commonly support higher maximum widths than maximum heights. Rotate the decoded
    // frame before encoding, so the encoded frame's width >= height, and set
    // outputRotationDegrees to ensure the frame is displayed in the correct orientation.
    if (outputHeight > outputWidth) {
      outputRotationDegrees = 90;
      // TODO(b/201293185): Put postRotate in a later GlFrameProcessor.
      transformationMatrix.postRotate(outputRotationDegrees);
      int swap = outputWidth;
      outputWidth = outputHeight;
      outputHeight = swap;
    } else {
      outputRotationDegrees = 0;
    }
  }

  @RequiresNonNull("transformationMatrix")
  private void applyCrop() {
    float scaleX = (cropRight - cropLeft) / GlUtil.LENGTH_NDC;
    float scaleY = (cropTop - cropBottom) / GlUtil.LENGTH_NDC;
    float centerX = (cropLeft + cropRight) / 2;
    float centerY = (cropBottom + cropTop) / 2;

    transformationMatrix.postTranslate(-centerX, -centerY);
    transformationMatrix.postScale(1f / scaleX, 1f / scaleY);

    outputWidth = Math.round(outputWidth * scaleX);
    outputHeight = Math.round(outputHeight * scaleY);
  }

  @RequiresNonNull("transformationMatrix")
  private void applyAspectRatio() {
    float inputAspectRatio = (float) outputWidth / outputHeight;
    if (presentationStrategy == SCALE_TO_FIT) {
      if (requestedAspectRatio > inputAspectRatio) {
        transformationMatrix.setScale(inputAspectRatio / requestedAspectRatio, 1f);
        outputWidth = Math.round(outputHeight * requestedAspectRatio);
      } else {
        transformationMatrix.setScale(1f, requestedAspectRatio / inputAspectRatio);
        outputHeight = Math.round(outputWidth / requestedAspectRatio);
      }
    } else if (presentationStrategy == SCALE_TO_FIT_WITH_CROP) {
      if (requestedAspectRatio > inputAspectRatio) {
        transformationMatrix.setScale(1f, requestedAspectRatio / inputAspectRatio);
        outputHeight = Math.round(outputWidth / requestedAspectRatio);
      } else {
        transformationMatrix.setScale(inputAspectRatio / requestedAspectRatio, 1f);
        outputWidth = Math.round(outputHeight * requestedAspectRatio);
      }
    } else if (presentationStrategy == STRETCH_TO_FIT) {
      if (requestedAspectRatio > inputAspectRatio) {
        outputWidth = Math.round(outputHeight * requestedAspectRatio);
      } else {
        outputHeight = Math.round(outputWidth / requestedAspectRatio);
      }
    }
  }
}
