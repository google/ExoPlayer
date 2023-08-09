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

/**
 * Applies a speed change by updating the frame timestamps.
 *
 * <p>This effect doesn't drop any frames.
 */
@UnstableApi
public final class SpeedChangeEffect implements GlEffect {

  private final float speed;

  /** Creates an instance. */
  public SpeedChangeEffect(@FloatRange(from = 0, fromInclusive = false) float speed) {
    checkArgument(speed > 0f);
    this.speed = speed;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new SpeedChangeShaderProgram(context, speed, useHdr);
  }

  @Override
  public boolean isNoOp(int inputWidth, int inputHeight) {
    return speed == 1f;
  }
}
