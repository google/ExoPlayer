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

import android.graphics.Matrix;
import android.util.Size;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.util.GlUtil;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * Specifies a {@link Format#rotationDegrees} to apply to each frame for encoder compatibility, if
 * needed.
 *
 * <p>Encoders commonly support higher maximum widths than maximum heights. This may rotate the
 * decoded frame before encoding, so the encoded frame's width >= height, and set {@link
 * Format#rotationDegrees} to ensure the frame is displayed in the correct orientation.
 */
/* package */ class EncoderCompatibilityTransformation implements MatrixTransformation {
  // TODO(b/218488308): Allow reconfiguration of the output size, as encoders may not support the
  //  requested output resolution.

  static {
    GlUtil.glAssertionsEnabled = true;
  }

  private int outputRotationDegrees;
  private @MonotonicNonNull Matrix transformationMatrix;

  /** Creates a new instance. */
  public EncoderCompatibilityTransformation() {
    outputRotationDegrees = C.LENGTH_UNSET;
  }

  @Override
  public Size configure(int inputWidth, int inputHeight) {
    checkArgument(inputWidth > 0, "inputWidth must be positive");
    checkArgument(inputHeight > 0, "inputHeight must be positive");

    transformationMatrix = new Matrix();
    if (inputHeight > inputWidth) {
      outputRotationDegrees = 90;
      transformationMatrix.postRotate(outputRotationDegrees);
      return new Size(inputHeight, inputWidth);
    } else {
      outputRotationDegrees = 0;
      return new Size(inputWidth, inputHeight);
    }
  }

  @Override
  public Matrix getMatrix(long presentationTimeUs) {
    return checkStateNotNull(transformationMatrix, "configure must be called first");
  }

  /**
   * Returns {@link Format#rotationDegrees} for the output frame.
   *
   * <p>Return values may be {@code 0} or {@code 90} degrees.
   *
   * <p>Should only be called after {@linkplain #configure(int, int) configuration}.
   */
  public int getOutputRotationDegrees() {
    checkState(
        outputRotationDegrees != C.LENGTH_UNSET,
        "configure must be called before getOutputRotationDegrees");
    return outputRotationDegrees;
  }
}
