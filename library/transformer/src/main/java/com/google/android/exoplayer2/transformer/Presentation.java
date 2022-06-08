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
 * Controls how a frame is presented with options to set the output resolution and choose how to map
 * the input pixels onto the output frame geometry (for example, by stretching the input frame to
 * match the specified output frame, or fitting the input frame using letterboxing).
 *
 * <p>Aspect ratio is applied before setting resolution.
 *
 * <p>The background color of the output frame will be black, with alpha = 0 if applicable.
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
    private int outputHeight;
    private float aspectRatio;
    private @Layout int layout;

    /** Creates a builder with default values. */
    public Builder() {
      outputHeight = C.LENGTH_UNSET;
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
      this.outputHeight = height;
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
      this.aspectRatio = aspectRatio;
      this.layout = layout;
      return this;
    }

    public Presentation build() {
      return new Presentation(outputHeight, aspectRatio, layout);
    }
  }

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final int requestedHeightPixels;
  private final float requestedAspectRatio;
  private final @Layout int layout;

  private float outputWidth;
  private float outputHeight;
  private @MonotonicNonNull Matrix transformationMatrix;

  /** Creates a new instance. */
  private Presentation(int requestedHeightPixels, float requestedAspectRatio, @Layout int layout) {
    this.requestedHeightPixels = requestedHeightPixels;
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

    if (requestedAspectRatio != C.LENGTH_UNSET) {
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
