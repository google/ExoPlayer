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
package com.google.android.exoplayer2.effect;

import android.content.Context;
import androidx.annotation.RequiresApi;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;

/**
 * A {@link GlEffect} for performing separable convolutions.
 *
 * <p>A single 1D convolution function is applied horizontally on a first pass and vertically on a
 * second pass.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@RequiresApi(26) // See SeparableConvolutionShaderProgram.
@Deprecated
public abstract class SeparableConvolution implements GlEffect {
  private final float scaleFactor;

  /** Creates an instance with a {@code scaleFactor} of {@code 1}. */
  public SeparableConvolution() {
    this(/* scaleFactor= */ 1.0f);
  }

  /**
   * Creates an instance.
   *
   * @param scaleFactor The scaling factor used to determine the size of the output relative to the
   *     input. The aspect ratio remains constant.
   */
  public SeparableConvolution(float scaleFactor) {
    this.scaleFactor = scaleFactor;
  }

  /** Returns a {@linkplain ConvolutionFunction1D 1D convolution function}. */
  public abstract ConvolutionFunction1D getConvolution();

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new SeparableConvolutionShaderProgram(context, useHdr, this, scaleFactor);
  }
}
