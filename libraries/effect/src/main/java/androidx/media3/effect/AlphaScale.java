/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.content.Context;
import androidx.annotation.FloatRange;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;

/** Scales the alpha value (i.e. the translucency) of a frame. */
@UnstableApi
public final class AlphaScale implements GlEffect {
  private final float alphaScale;

  /**
   * Creates a new instance to scale the entire frame's alpha values by {@code alphaScale}, to
   * modify translucency.
   *
   * <p>An {@code alphaScale} value of {@code 1} means no change is applied. A value below {@code 1}
   * increases translucency, and a value above {@code 1} reduces translucency.
   */
  public AlphaScale(@FloatRange(from = 0) float alphaScale) {
    checkArgument(0 <= alphaScale);
    this.alphaScale = alphaScale;
  }

  @Override
  public AlphaScaleShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new AlphaScaleShaderProgram(context, useHdr, alphaScale);
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return alphaScale == 1f;
  }
}
