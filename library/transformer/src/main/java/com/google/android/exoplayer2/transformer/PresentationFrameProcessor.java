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

import android.content.Context;
import android.graphics.Matrix;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Controls how a frame is viewed, by changing resolution. */
// TODO(b/213190310): Implement crop, aspect ratio changes, etc.
public final class PresentationFrameProcessor implements GlFrameProcessor {

  /** A builder for {@link PresentationFrameProcessor} instances. */
  public static final class Builder {

    // Mandatory field.
    private final Context context;

    // Optional field.
    private int outputHeight;

    /**
     * Creates a builder with default values.
     *
     * @param context The {@link Context}.
     */
    public Builder(Context context) {
      this.context = context;
      outputHeight = C.LENGTH_UNSET;
    }

    /**
     * Sets the output resolution using the output height.
     *
     * <p>The default value {@link C#LENGTH_UNSET} corresponds to using the same height as the
     * input. Output width of the displayed frame will scale to preserve the frame's aspect ratio
     * after other transformations.
     *
     * <p>For example, a 1920x1440 frame can be scaled to 640x480 by calling setResolution(480).
     *
     * @param outputHeight The output height of the displayed frame, in pixels.
     * @return This builder.
     */
    public Builder setResolution(int outputHeight) {
      this.outputHeight = outputHeight;
      return this;
    }

    public PresentationFrameProcessor build() {
      return new PresentationFrameProcessor(context, outputHeight);
    }
  }

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private final Context context;
  private final int requestedHeight;

  private @MonotonicNonNull Size outputSize;
  private int outputRotationDegrees;
  private @MonotonicNonNull Matrix transformationMatrix;
  private @MonotonicNonNull AdvancedFrameProcessor advancedFrameProcessor;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param requestedHeight The height of the output frame, in pixels.
   */
  private PresentationFrameProcessor(Context context, int requestedHeight) {
    this.context = context;
    this.requestedHeight = requestedHeight;

    outputRotationDegrees = C.LENGTH_UNSET;
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
  @VisibleForTesting // Allows roboletric testing of output size calculation without OpenGL.
  /* package */ void configureOutputSizeAndTransformationMatrix(int inputWidth, int inputHeight) {
    transformationMatrix = new Matrix();
    int displayWidth = inputWidth;
    int displayHeight = inputHeight;
    // Scale width and height to desired requestedHeight, preserving aspect ratio.
    if (requestedHeight != C.LENGTH_UNSET && requestedHeight != displayHeight) {
      displayWidth = Math.round((float) requestedHeight * displayWidth / displayHeight);
      displayHeight = requestedHeight;
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
}
