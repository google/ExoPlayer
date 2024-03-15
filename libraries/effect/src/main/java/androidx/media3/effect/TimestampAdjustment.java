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

import android.content.Context;
import androidx.media3.common.util.UnstableApi;
import java.util.function.LongConsumer;

/**
 * Changes the frame timestamps using the {@link TimestampMap}.
 *
 * <p>This effect doesn't drop any frames.
 *
 * <p>This effect is not supported for effects previewing.
 */
@UnstableApi
public final class TimestampAdjustment implements GlEffect {

  /**
   * Maps input timestamps to output timestamps asynchronously.
   *
   * <p>Implementation can choose to calculate the timestamp and invoke the consumer on another
   * thread asynchronously.
   */
  public interface TimestampMap {

    /**
     * Calculates the output timestamp that corresponds to the input timestamp.
     *
     * <p>The implementation should invoke the {@code outputTimeConsumer} with the output timestamp,
     * on any thread.
     */
    void calculateOutputTimeUs(long inputTimeUs, LongConsumer outputTimeConsumer);
  }

  private final TimestampMap timestampMap;

  /** Creates an instance. */
  public TimestampAdjustment(TimestampMap timestampMap) {
    this.timestampMap = timestampMap;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr) {
    return new TimestampAdjustmentShaderProgram(timestampMap);
  }
}
