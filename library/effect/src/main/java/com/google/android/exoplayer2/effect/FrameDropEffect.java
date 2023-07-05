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
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;

/**
 * Drops frames to lower average frame rate to around {@code targetFrameRate}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public class FrameDropEffect implements GlEffect {

  private final float inputFrameRate;
  private final float targetFrameRate;

  /**
   * Creates a {@link FrameDropEffect} with the default frame dropping strategy.
   *
   * <p>The strategy used is to queue the current frame, x, with timestamp T_x if and only if one of
   * the following is true:
   *
   * <ul>
   *   <li>x is the first frame,
   *   <li>(T_x - T_lastQueued) is closer to the target frame interval than (T_(x+1) - T_lastQueued)
   * </ul>
   *
   * <p>Where T_lastQueued is the timestamp of the last queued frame and T_(x+1) is the timestamp of
   * the next frame. The target frame interval is determined from {@code targetFrameRate}.
   *
   * @param targetFrameRate The number of frames per second the output video should roughly have.
   */
  public static FrameDropEffect createDefaultFrameDropEffect(float targetFrameRate) {
    return new FrameDropEffect(/* inputFrameRate= */ C.RATE_UNSET, targetFrameRate);
  }

  /**
   * Creates a {@link FrameDropEffect} that keeps every nth frame, where n is the {@code
   * inputFrameRate} divided by the {@code targetFrameRate}.
   *
   * <p>For example, if the input stream came in at 60fps and the targeted frame rate was 20fps,
   * every 3rd frame would be kept. If n is not an integer, then we round to the nearest one.
   *
   * @param expectedFrameRate The number of frames per second in the input stream.
   * @param targetFrameRate The number of frames per second the output video should roughly have.
   */
  public static FrameDropEffect createSimpleFrameDropEffect(
      float expectedFrameRate, float targetFrameRate) {
    return new FrameDropEffect(expectedFrameRate, targetFrameRate);
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    if (inputFrameRate == C.RATE_UNSET) {
      return new DefaultFrameDroppingShaderProgram(context, useHdr, targetFrameRate);
    } else {
      return new SimpleFrameDroppingShaderProgram(context, useHdr, inputFrameRate, targetFrameRate);
    }
  }

  private FrameDropEffect(float inputFrameRate, float targetFrameRate) {
    this.inputFrameRate = inputFrameRate;
    this.targetFrameRate = targetFrameRate;
  }
}
