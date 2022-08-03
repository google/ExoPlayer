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

package androidx.media3.effect;

import android.content.Context;
import androidx.media3.common.FrameProcessingException;

/**
 * Specifies a 4x4 RGBA color transformation matrix to apply to each frame in the fragment shader.
 */
public interface RgbaMatrix extends GlEffect {

  /**
   * Returns the 4x4 RGBA transformation {@linkplain android.opengl.Matrix matrix} to apply to the
   * color values of each pixel in the frame with the given timestamp.
   */
  float[] getMatrix(long presentationTimeUs);

  @Override
  default RgbaMatrixProcessor toGlTextureProcessor(Context context, boolean useHdr)
      throws FrameProcessingException {
    return new RgbaMatrixProcessor(context, /* rgbaMatrix= */ this, useHdr);
  }
}
