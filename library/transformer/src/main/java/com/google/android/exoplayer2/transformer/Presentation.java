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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.android.exoplayer2.util.Assertions.checkStateNotNull;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.graphics.Matrix;
import android.util.Size;
import androidx.annotation.IntDef;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlUtil;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;

/**
 * Controls how a frame is presented with options to set the output resolution, crop the input, and
 * choose how to map the input pixels onto the output frame geometry (for example, by stretching the
 * input frame to match the specified output frame, or fitting the input frame using letterboxing).
 *
 * <p>Cropping or aspect ratio is applied before setting resolution.
 *
 * <p>The background color of the output frame will be black.
 */
public final class Presentation implements MatrixTransformation {

  /**
   * Strategies controlling the layout of input pixels in the output frame.
   *
   * <p>One of {@link #LAYOUT_SCALE_TO_FIT}, {@link #LAYOUT_SCALE_TO_FIT_WITH_CROP}, or {@link
   * #LAYOUT_STRETCH_TO_FIT}.
   *
   * <p>May scale either width or height, leaving the other output dimension equal to its input,
   * unless {@link Builder#setResolution(int)} rescales width and height.
   */
  @Documented
  @Retention(SOURCE)
  @Target(TYPE_USE)
  @IntDef({LAYOUT_SCALE_TO_FIT, LAYOUT_SCALE_TO_FIT_WITH_CROP, LAYOUT_STRETCH_TO_FIT})
  public @interface Layout {}
  /**
   * Empty pixels added above and below the input frame (for letterboxing), or to the left and right
   * of the input frame (for pillarboxing), until the desired aspect ratio is achieved. All input
   * frame pixels will be within the output frame.
   *
   * <p>When applying:
   *
   * <ul>
   *   <li>letterboxing, the output width will default to the input width, and the output height
   *       will be scaled appropriately.
   *   <li>pillarboxing, the output height will default to the input height, and the output width
   *       will be scaled appropriately.
   * </ul>
   */
  public static final int LAYOUT_SCALE_TO_FIT = 0;
  /**
   * Pixels cropped from the input frame, until the desired aspect ratio is achieved. Pixels may be
   * cropped either from the bottom and top, or from the left and right sides, of the input frame.
   *
   * <p>When cropping from the:
   *
   * <ul>
   *   <li>bottom and top, the output width will default to the input width, and the output height
   *       will be scaled appropriately.
   *   <li>left and right, the output height will default to the input height, and the output width
   *       will be scaled appropriately.
   * </ul>
   */
  public static final int LAYOUT_SCALE_TO_FIT_WITH_CROP = 1;
  /**
   * Frame stretched larger on the x or y axes to fit the desired aspect ratio.
   *
   * <p>When stretching to a:
   *
   * <ul>
   *   <li>taller aspect ratio, the output width will default to the input width, and the output
   *       height will be scaled appropriately.
   *   <li>narrower aspect ratio, the output height will default to the input height, and the output
   *       width will be scaled appropriately.
   * </ul>
   */
  public static final int LAYOUT_STRETCH_TO_FIT = 2;

  /** A builder for {@link Presentation} instances. */
  public static final class Builder {

    // Optional fields.
    private int heightPixels;
    private float cropLeft;
    private float cropRight;
    private float cropBottom;
    private float cropTop;
    private float aspectRatio;
    private @Layout int layout;

    /** Creates a builder with default values. */
    public Builder() {
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
     * <p>The default value, {@link C#LENGTH_UNSET}, corresponds to using the same height as the
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
     * to 1, which corresponds to not applying any crop. To crop to a smaller subset of the input
     * frame, use values between -1 and 1. To crop to a larger frame, use values below -1 and above
     * 1.
     *
     * <p>Width and height values set may be rescaled by {@link #setResolution(int)}, which is
     * applied after cropping changes.
     *
     * <p>Only one of {@code setCrop} or {@link #setAspectRatio(float, int)} can be called for one
     * {@link Presentation}.
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
     * Sets the aspect ratio (width/height ratio) for the output frame.
     *
     * <p>Resizes a frame's width or height to conform to an {@code aspectRatio}, given a {@link
     * Layout}. {@code aspectRatio} defaults to {@link C#LENGTH_UNSET}, which corresponds to the
     * same aspect ratio as the input frame. {@code layout} defaults to {@link #LAYOUT_SCALE_TO_FIT}
     *
     * <p>Width and height values set may be rescaled by {@link #setResolution(int)}, which is
     * applied after aspect ratio changes.
     *
     * <p>Only one of {@link #setCrop(float, float, float, float)} or {@code setAspectRatio} can be
     * called for one {@link Presentation}.
     *
     * @param aspectRatio The aspect ratio (width/height ratio) of the output frame. Must be
     *     positive.
     * @return This builder.
     */
    public Builder setAspectRatio(float aspectRatio, @Layout int layout) {
      checkArgument(aspectRatio > 0, "aspect ratio " + aspectRatio + " must be positive");
      checkArgument(
          layout == LAYOUT_SCALE_TO_FIT
              || layout == LAYOUT_SCALE_TO_FIT_WITH_CROP
              || layout == LAYOUT_STRETCH_TO_FIT,
          "invalid layout " + layout);
      checkState(
          cropLeft == -1f && cropRight == 1f && cropBottom == -1f && cropTop == 1f,
          "setAspectRatio and setCrop cannot be called in the same instance");
      this.aspectRatio = aspectRatio;
      this.layout = layout;
      return this;
    }

    public Presentation build() {
      return new Presentation(
          heightPixels, cropLeft, cropRight, cropBottom, cropTop, aspectRatio, layout);
    }
  }

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final int requestedHeightPixels;
  private final float cropLeft;
  private final float cropRight;
  private final float cropBottom;
  private final float cropTop;
  private final float requestedAspectRatio;
  private final @Layout int layout;

  private float outputWidth;
  private float outputHeight;
  private @MonotonicNonNull Matrix transformationMatrix;

  /** Creates a new instance. */
  private Presentation(
      int requestedHeightPixels,
      float cropLeft,
      float cropRight,
      float cropBottom,
      float cropTop,
      float requestedAspectRatio,
      @Layout int layout) {
    this.requestedHeightPixels = requestedHeightPixels;
    this.cropLeft = cropLeft;
    this.cropRight = cropRight;
    this.cropBottom = cropBottom;
    this.cropTop = cropTop;
    this.requestedAspectRatio = requestedAspectRatio;
    this.layout = layout;

    outputWidth = C.LENGTH_UNSET;
    outputHeight = C.LENGTH_UNSET;
    transformationMatrix = new Matrix();
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
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
      outputWidth = requestedHeightPixels * outputWidth / outputHeight;
      outputHeight = requestedHeightPixels;
    }
    return new Size(Math.round(outputWidth), Math.round(outputHeight));
  }

  @Override
  public Matrix getMatrix(long presentationTimeUs) {
    return checkStateNotNull(transformationMatrix, "configure must be called first");
  }

  @RequiresNonNull("transformationMatrix")
  private void applyCrop() {
    float scaleX = (cropRight - cropLeft) / GlUtil.LENGTH_NDC;
    float scaleY = (cropTop - cropBottom) / GlUtil.LENGTH_NDC;
    float centerX = (cropLeft + cropRight) / 2;
    float centerY = (cropBottom + cropTop) / 2;

    transformationMatrix.postTranslate(-centerX, -centerY);
    transformationMatrix.postScale(1f / scaleX, 1f / scaleY);

    outputWidth = outputWidth * scaleX;
    outputHeight = outputHeight * scaleY;
  }

  @RequiresNonNull("transformationMatrix")
  private void applyAspectRatio() {
    float inputAspectRatio = outputWidth / outputHeight;
    if (layout == LAYOUT_SCALE_TO_FIT) {
      if (requestedAspectRatio > inputAspectRatio) {
        transformationMatrix.setScale(inputAspectRatio / requestedAspectRatio, 1f);
        outputWidth = outputHeight * requestedAspectRatio;
      } else {
        transformationMatrix.setScale(1f, requestedAspectRatio / inputAspectRatio);
        outputHeight = outputWidth / requestedAspectRatio;
      }
    } else if (layout == LAYOUT_SCALE_TO_FIT_WITH_CROP) {
      if (requestedAspectRatio > inputAspectRatio) {
        transformationMatrix.setScale(1f, requestedAspectRatio / inputAspectRatio);
        outputHeight = outputWidth / requestedAspectRatio;
      } else {
        transformationMatrix.setScale(inputAspectRatio / requestedAspectRatio, 1f);
        outputWidth = outputHeight * requestedAspectRatio;
      }
    } else if (layout == LAYOUT_STRETCH_TO_FIT) {
      if (requestedAspectRatio > inputAspectRatio) {
        outputWidth = outputHeight * requestedAspectRatio;
      } else {
        outputHeight = outputWidth / requestedAspectRatio;
      }
    }
  }
}
