/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.opengl.Matrix;
import androidx.annotation.FloatRange;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;
import java.util.Arrays;

/** Modifies brightness of an input frame. */
@UnstableApi
public final class Brightness implements RgbMatrix {

  private final float[] rgbMatrix;

  /**
   * Modifies brightness by adding a constant value to red, green, and blue values.
   *
   * @param brightness The constant value to add to red, green, and blue values. Should be greater
   *     than or equal to {@code -1f}, and less than or equal to {@code 1f}. {@code 0} means to
   *     leave brightness unchanged.
   */
  public Brightness(@FloatRange(from = -1, to = 1) float brightness) {
    checkArgument(
        brightness >= -1f && brightness <= 1f,
        "brightness value outside of range from -1f to 1f, inclusive");
    rgbMatrix = GlUtil.create4x4IdentityMatrix();
    Matrix.translateM(
        rgbMatrix,
        /* smOffset= */ 0,
        /* x= */ brightness,
        /* y= */ brightness,
        /* z= */ brightness);
  }

  @Override
  public float[] getMatrix(long presentationTimeUs, boolean useHdr) {
    checkArgument(!useHdr, "HDR is not supported.");
    return rgbMatrix;
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return Arrays.equals(rgbMatrix, GlUtil.create4x4IdentityMatrix());
  }
}
