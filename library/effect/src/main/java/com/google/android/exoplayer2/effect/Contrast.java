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

import androidx.annotation.FloatRange;

/**
 * A {@link RgbMatrix} to control the contrast of video frames.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class Contrast implements RgbMatrix {

  /** Adjusts the contrast of video frames in the interval [-1, 1]. */
  private final float contrast;

  private final float[] contrastMatrix;

  /**
   * Creates a new instance for the given contrast value.
   *
   * <p>Contrast values range from -1 (all gray pixels) to 1 (maximum difference of colors). 0 means
   * to add no contrast and leaves the frames unchanged.
   */
  public Contrast(@FloatRange(from = -1, to = 1) float contrast) {
    checkArgument(-1 <= contrast && contrast <= 1, "Contrast needs to be in the interval [-1, 1].");
    this.contrast = contrast;
    float contrastFactor = (1 + contrast) / (1.0001f - contrast);
    contrastMatrix =
        new float[] {
          contrastFactor,
          0.0f,
          0.0f,
          0.0f,
          0.0f,
          contrastFactor,
          0.0f,
          0.0f,
          0.0f,
          0.0f,
          contrastFactor,
          0.0f,
          (1.0f - contrastFactor) * 0.5f,
          (1.0f - contrastFactor) * 0.5f,
          (1.0f - contrastFactor) * 0.5f,
          1.0f
        };
  }

  @Override
  public float[] getMatrix(long presentationTimeUs, boolean useHdr) {
    // Implementation is not currently time-varying, therefore matrix should not be changing between
    // frames.
    return contrastMatrix;
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return contrast == 0f;
  }
}
