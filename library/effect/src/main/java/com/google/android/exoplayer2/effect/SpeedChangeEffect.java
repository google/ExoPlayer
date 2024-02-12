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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.audio.SpeedProvider;

/**
 * Applies a speed change by updating the frame timestamps.
 *
 * <p>This effect doesn't drop any frames.
 *
 * <p>This effect is not supported for effects previewing.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class SpeedChangeEffect implements GlEffect {

  private final SpeedProvider speedProvider;

  /** Creates an instance that applies the same {@code speed} change to all the timestamps. */
  public SpeedChangeEffect(@FloatRange(from = 0, fromInclusive = false) float speed) {
    checkArgument(speed > 0f);
    speedProvider =
        new SpeedProvider() {
          @Override
          public float getSpeed(long timeUs) {
            return speed;
          }

          @Override
          public long getNextSpeedChangeTimeUs(long timeUs) {
            return C.TIME_UNSET;
          }
        };
  }

  /**
   * Creates an instance.
   *
   * @param speedProvider The {@link SpeedProvider} specifying the speed changes. Applied on each
   *     stream assuming the first frame timestamp of the input media is 0.
   */
  public SpeedChangeEffect(SpeedProvider speedProvider) {
    this.speedProvider = speedProvider;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr) {
    return new SpeedChangeShaderProgram(speedProvider);
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return speedProvider.getSpeed(/* timeUs= */ 0) == 1
        && speedProvider.getNextSpeedChangeTimeUs(/* timeUs= */ 0) == C.TIME_UNSET;
  }
}
