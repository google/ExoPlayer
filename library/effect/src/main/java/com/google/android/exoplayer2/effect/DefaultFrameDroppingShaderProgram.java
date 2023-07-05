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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.lang.Math.abs;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.util.GlTextureInfo;
import com.google.android.exoplayer2.util.VideoFrameProcessingException;

/**
 * Drops frames by only queuing input frames that are chosen by the frame dropping strategy.
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
 * the next frame. The target frame interval is determined from {@code targetFps}.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
/* package */ final class DefaultFrameDroppingShaderProgram extends FrameCacheGlShaderProgram {
  private final long targetFrameDeltaUs;

  @Nullable private GlTextureInfo previousTexture;
  private long previousPresentationTimeUs;
  private long lastQueuedPresentationTimeUs;
  private boolean isPreviousFrameFirstFrame;

  /**
   * Creates a new instance.
   *
   * @param context The {@link Context}.
   * @param useHdr Whether input textures come from an HDR source. If {@code true}, colors will be
   *     in linear RGB BT.2020. If {@code false}, colors will be in linear RGB BT.709.
   * @param targetFps The number of frames per second the output video should roughly have.
   */
  public DefaultFrameDroppingShaderProgram(Context context, boolean useHdr, float targetFps)
      throws VideoFrameProcessingException {
    super(context, /* capacity= */ 1, useHdr);
    this.targetFrameDeltaUs = (long) (C.MICROS_PER_SECOND / targetFps);
    lastQueuedPresentationTimeUs = C.TIME_UNSET;
    previousPresentationTimeUs = C.TIME_UNSET;
  }

  @Override
  public void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
    if (previousTexture == null) {
      super.queueInputFrame(inputTexture, presentationTimeUs);
      lastQueuedPresentationTimeUs = presentationTimeUs;
      isPreviousFrameFirstFrame = true;
    } else if (shouldQueuePreviousFrame(presentationTimeUs)) {
      super.queueInputFrame(checkNotNull(previousTexture), previousPresentationTimeUs);
      lastQueuedPresentationTimeUs = previousPresentationTimeUs;
    } else {
      inputListener.onInputFrameProcessed(checkNotNull(previousTexture));
      inputListener.onReadyToAcceptInputFrame();
    }
    previousTexture = inputTexture;
    previousPresentationTimeUs = presentationTimeUs;
  }

  @Override
  public void flush() {
    super.flush();
    lastQueuedPresentationTimeUs = C.TIME_UNSET;
    previousPresentationTimeUs = C.TIME_UNSET;
    previousTexture = null;
  }

  private boolean shouldQueuePreviousFrame(long currentPresentationTimeUs) {
    if (isPreviousFrameFirstFrame) {
      isPreviousFrameFirstFrame = false;
      return false;
    }

    long previousFrameTimeDeltaUs = previousPresentationTimeUs - lastQueuedPresentationTimeUs;
    long currentFrameTimeDeltaUs = currentPresentationTimeUs - lastQueuedPresentationTimeUs;

    return abs(previousFrameTimeDeltaUs - targetFrameDeltaUs)
        < abs(currentFrameTimeDeltaUs - targetFrameDeltaUs);
  }
}
