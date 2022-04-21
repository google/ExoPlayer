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

import android.content.Context;
import android.util.Size;
import androidx.annotation.VisibleForTesting;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.GlUtil;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Copies frames from a texture and applies {@link Format#rotationDegrees} for encoder
 * compatibility, if needed.
 *
 * <p>Encoders commonly support higher maximum widths than maximum heights. This may rotate the
 * decoded frame before encoding, so the encoded frame's width >= height, and set {@link
 * Format#rotationDegrees} to ensure the frame is displayed in the correct orientation.
 */
/* package */ class EncoderCompatibilityFrameProcessor implements GlFrameProcessor {
  // TODO(b/218488308): Allow reconfiguration of the output size, as encoders may not support the
  //  requested output resolution.

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private int outputRotationDegrees;
  private @MonotonicNonNull ScaleToFitFrameProcessor rotateFrameProcessor;

  /** Creates a new instance. */
  /* package */ EncoderCompatibilityFrameProcessor() {

    outputRotationDegrees = C.LENGTH_UNSET;
  }

  @Override
  public void initialize(Context context, int inputTexId, int inputWidth, int inputHeight)
      throws IOException {
    configureOutputSizeAndRotation(inputWidth, inputHeight);
    rotateFrameProcessor =
        new ScaleToFitFrameProcessor.Builder().setRotationDegrees(outputRotationDegrees).build();
    rotateFrameProcessor.initialize(context, inputTexId, inputWidth, inputHeight);
  }

  @Override
  public Size getOutputSize() {
    return checkStateNotNull(rotateFrameProcessor).getOutputSize();
  }

  /**
   * Returns {@link Format#rotationDegrees} for the output frame.
   *
   * <p>Return values may be {@code 0} or {@code 90} degrees.
   *
   * <p>The frame processor must be {@linkplain GlFrameProcessor#initialize(Context, int, int, int)
   * initialized}.
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
    checkStateNotNull(rotateFrameProcessor).drawFrame(presentationTimeUs);
  }

  @Override
  public void release() {
    if (rotateFrameProcessor != null) {
      rotateFrameProcessor.release();
    }
  }

  @VisibleForTesting // Allows robolectric testing of output size calculation without OpenGL.
  /* package */ void configureOutputSizeAndRotation(int inputWidth, int inputHeight) {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    outputRotationDegrees = (inputHeight > inputWidth) ? 90 : 0;
  }
}
