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
import com.google.android.exoplayer2.util.VideoFrameProcessingException;

/** Drops frames to lower average frame rate to around {@code targetFrameRate}. */
public class FrameDropEffect implements GlEffect {

  private final float targetFrameRate;

  /**
   * Creates an instance.
   *
   * @param targetFrameRate The number of frames per second the output video should roughly have.
   */
  public FrameDropEffect(float targetFrameRate) {
    this.targetFrameRate = targetFrameRate;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new FrameDroppingShaderProgram(context, useHdr, targetFrameRate);
  }
}
