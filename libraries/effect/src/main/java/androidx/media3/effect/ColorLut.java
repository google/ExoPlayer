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
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.UnstableApi;

/**
 * Specifies color transformations using color lookup tables to apply to each frame in the fragment
 * shader.
 */
@UnstableApi
public interface ColorLut extends GlEffect {

  /**
   * Returns the OpenGL texture ID of the LUT to apply to the pixels of the frame with the given
   * timestamp.
   */
  int getLutTextureId(long presentationTimeUs);

  /** Returns the length N of the 3D N x N x N LUT cube with the given timestamp. */
  int getLength(long presentationTimeUs);

  /** Releases the OpenGL texture of the LUT. */
  void release() throws GlUtil.GlException;

  @Override
  default GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new ColorLutShaderProgram(context, /* colorLut= */ this, useHdr);
  }
}
