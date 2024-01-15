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
  private final float scaleWidth;
  private final float scaleHeight;

  /** Creates an instance with {@code scaleWidth} and {@code scaleHeight} set to {@code 1.0f}. */
  public SeparableConvolution() {
    this(/* scaleWidth= */ 1.0f, /* scaleHeight= */ 1.0f);
  }

  /**
   * Creates an instance.
   *
   * @param scaleWidth The scaling factor used to determine the width of the output relative to the
   *     input.
   * @param scaleHeight The scaling factor used to determine the height of the output relative to
   *     the input.
   */
  public SeparableConvolution(float scaleWidth, float scaleHeight) {
    this.scaleWidth = scaleWidth;
    this.scaleHeight = scaleHeight;
  }

  /**
   * Returns a {@linkplain ConvolutionFunction1D 1D convolution function}.
   *
   * @param presentationTimeUs The presentation timestamp of the input frame, in microseconds.
   */
  public abstract ConvolutionFunction1D getConvolution(long presentationTimeUs);

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new SeparableConvolutionShaderProgram(
        context, useHdr, /* convolution= */ this, scaleWidth, scaleHeight);
  }
}
