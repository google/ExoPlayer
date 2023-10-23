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

import android.content.Context;
import androidx.annotation.IntRange;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;

/**
 * Applies a {@link GlEffect} from {@code startTimeUs} to {@code endTimeUs}, and no change on all
 * other timestamps.
 */
@UnstableApi
public final class TimestampWrapper implements GlEffect {

  public final GlEffect glEffect;
  public final long startTimeUs;
  public final long endTimeUs;

  /**
   * Creates a new instance.
   *
   * @param glEffect The {@link GlEffect} to apply, from {@code startTimeUs} to {@code endTimeUs}.
   *     This instance must not change the output dimensions.
   * @param startTimeUs The time to begin applying {@code glEffect} on, in microseconds. Must be
   *     non-negative.
   * @param endTimeUs The time to stop applying {code glEffect} on, in microseconds. Must be
   *     non-negative.
   */
  public TimestampWrapper(
      GlEffect glEffect, @IntRange(from = 0) long startTimeUs, @IntRange(from = 0) long endTimeUs) {
    // TODO(b/272063508): Allow TimestampWrapper to take in a glEffect that changes the output
    //  dimensions, likely by moving the configure() method from SingleFrameGlShaderProgram to
    //  GlShaderProgram, so that we can detect the output dimensions of the
    //  glEffect.toGlShaderProgram.
    checkArgument(
        startTimeUs >= 0 && endTimeUs >= 0, "startTimeUs and endTimeUs must be non-negative.");
    checkArgument(endTimeUs > startTimeUs, "endTimeUs should be after startTimeUs.");
    this.glEffect = glEffect;
    this.startTimeUs = startTimeUs;
    this.endTimeUs = endTimeUs;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new TimestampWrapperShaderProgram(context, useHdr, /* timestampWrapper= */ this);
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return glEffect.isNoOp(inputWidth, inputHeight);
  }
}
