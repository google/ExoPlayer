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

import static com.google.android.exoplayer2.util.Assertions.checkArgument;
import static java.lang.Math.round;

import android.content.Context;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;

/**
 * Drops frames by only keeping every nth frame, where n is the {@code inputFrameRate} divided by
 * the {@code targetFrameRate}.
 *
 * <p>For example, if the input stream came in at 60fps and the targeted frame rate was 20fps, every
 * 3rd frame would be kept. If n is not an integer, then we round to the nearest one.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class SimpleFrameDroppingShaderProgram extends FrameCacheGlShaderProgram {

  private final int n;

  private int framesReceived;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param inputFrameRate The number of frames per second the input stream should have.
   * @param targetFrameRate The number of frames per second the output video should roughly have.
   */
  public SimpleFrameDroppingShaderProgram(
      Context context, boolean useHdr, float inputFrameRate, float targetFrameRate)
      throws VideoFrameProcessingException {
    super(context, /* capacity= */ 1, useHdr);
    n = round(inputFrameRate / targetFrameRate);
    checkArgument(n >= 1, "The input frame rate should be greater than the target frame rate.");
  }

  @Override
  public void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
    framesReceived++;
    if (framesReceived % n == 0) {
      super.queueInputFrame(inputTexture, presentationTimeUs);
    } else {
      inputListener.onInputFrameProcessed(inputTexture);
      inputListener.onReadyToAcceptInputFrame();
    }
  }
}
