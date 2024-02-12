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

import androidx.media3.common.C;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.common.util.UnstableApi;

/**
 * Applies the speed changes specified in a {@link SpeedProvider} change by updating the frame
 * timestamps.
 *
 * <p>Does not support seeking in effects previewing.
 */
@UnstableApi
/* package */ final class SpeedChangeShaderProgram extends PassthroughShaderProgram {

  private final OffsetSpeedProvider speedProvider;

  private long lastSpeedChangeInputTimeUs;
  private long lastSpeedChangeOutputTimeUs;

  public SpeedChangeShaderProgram(SpeedProvider speedProvider) {
    super();
    this.speedProvider = new OffsetSpeedProvider(speedProvider);
    lastSpeedChangeInputTimeUs = C.TIME_UNSET;
    lastSpeedChangeOutputTimeUs = C.TIME_UNSET;
  }

  @Override
  public void queueInputFrame(
      GlObjectsProvider glObjectsProvider, GlTextureInfo inputTexture, long presentationTimeUs) {
    long outputPresentationTimeUs;
    if (lastSpeedChangeInputTimeUs == C.TIME_UNSET) {
      outputPresentationTimeUs = presentationTimeUs;
      lastSpeedChangeInputTimeUs = presentationTimeUs;
      lastSpeedChangeOutputTimeUs = outputPresentationTimeUs;
      speedProvider.setOffset(presentationTimeUs);
    } else {
      long nextSpeedChangeInputTimeUs =
          speedProvider.getNextSpeedChangeTimeUs(lastSpeedChangeInputTimeUs);
      while (nextSpeedChangeInputTimeUs != C.TIME_UNSET
          && nextSpeedChangeInputTimeUs <= presentationTimeUs) {
        lastSpeedChangeOutputTimeUs =
            getOutputTimeUs(
                nextSpeedChangeInputTimeUs, speedProvider.getSpeed(lastSpeedChangeInputTimeUs));
        lastSpeedChangeInputTimeUs = nextSpeedChangeInputTimeUs;
        nextSpeedChangeInputTimeUs =
            speedProvider.getNextSpeedChangeTimeUs(lastSpeedChangeInputTimeUs);
      }
      outputPresentationTimeUs =
          getOutputTimeUs(presentationTimeUs, speedProvider.getSpeed(presentationTimeUs));
    }
    super.queueInputFrame(glObjectsProvider, inputTexture, outputPresentationTimeUs);
  }

  @Override
  public void signalEndOfCurrentInputStream() {
    super.signalEndOfCurrentInputStream();
    lastSpeedChangeInputTimeUs = C.TIME_UNSET;
    lastSpeedChangeOutputTimeUs = C.TIME_UNSET;
  }

  private long getOutputTimeUs(long inputTimeUs, float speed) {
    return (long)
        (lastSpeedChangeOutputTimeUs + (inputTimeUs - lastSpeedChangeInputTimeUs) / speed);
  }

  private static class OffsetSpeedProvider implements SpeedProvider {

    private final SpeedProvider speedProvider;

    private long offset;

    public OffsetSpeedProvider(SpeedProvider speedProvider) {
      this.speedProvider = speedProvider;
    }

    public void setOffset(long offset) {
      this.offset = offset;
    }

    @Override
    public float getSpeed(long timeUs) {
      return speedProvider.getSpeed(timeUs - offset);
    }

    @Override
    public long getNextSpeedChangeTimeUs(long timeUs) {
      long nextSpeedChangeTimeUs = speedProvider.getNextSpeedChangeTimeUs(timeUs - offset);
      return nextSpeedChangeTimeUs == C.TIME_UNSET ? C.TIME_UNSET : offset + nextSpeedChangeTimeUs;
    }
  }
}
