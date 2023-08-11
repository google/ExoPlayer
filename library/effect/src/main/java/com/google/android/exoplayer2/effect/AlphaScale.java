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
package com.google.android.exoplayer2.effect;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import android.content.Context;
import androidx.annotation.FloatRange;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;

/**
 * Scales the alpha value (i.e. the translucency) of a frame.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class AlphaScale implements GlEffect {
  private final float alphaScale;

  /**
   * Creates a new instance to scale the entire frame's alpha values by {@code alphaScale}, to
   * modify translucency.
   *
   * <p>An {@code alphaScale} value of {@code 1} means no change is applied. A value below {@code 1}
   * reduces translucency, and a value above {@code 1} increases translucency.
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
