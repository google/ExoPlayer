/*
 * Copyright 2024 The Android Open Source Project
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
package androidx.media3.test.utils;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.media3.common.C;
import androidx.media3.common.audio.AudioProcessor.AudioFormat;
import androidx.media3.common.audio.SpeedProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;

/** {@link SpeedProvider} for tests */
@UnstableApi
public final class TestSpeedProvider implements SpeedProvider {

  private final long[] startTimesUs;
  private final float[] speeds;

  /**
   * Creates a {@code TestSpeedProvider} instance.
   *
   * @param startTimesUs The speed change start times, in microseconds. The values must be distinct
   *     and in increasing order.
   * @param speeds The speeds corresponding to each start time. Consecutive values must be distinct.
   * @return A {@code TestSpeedProvider}.
   */
  public static TestSpeedProvider createWithStartTimes(long[] startTimesUs, float[] speeds) {
    return new TestSpeedProvider(startTimesUs, speeds);
  }

  /**
   * Creates a {@code TestSpeedProvider} instance.
   *
   * @param audioFormat the {@link AudioFormat}.
   * @param frameCounts The frame counts for which the same speed should be applied.
   * @param speeds The speeds corresponding to each frame count. Consecutive values must be
   *     distinct.
   * @return A {@code TestSpeedProvider}.
   */
  public static TestSpeedProvider createWithFrameCounts(
      AudioFormat audioFormat, int[] frameCounts, float[] speeds) {
    long[] startTimesUs = new long[frameCounts.length];
    int totalFrameCount = 0;
    for (int i = 0; i < frameCounts.length; i++) {
      startTimesUs[i] = totalFrameCount * C.MICROS_PER_SECOND / audioFormat.sampleRate;
      totalFrameCount += frameCounts[i];
    }
    return new TestSpeedProvider(startTimesUs, speeds);
  }

  private TestSpeedProvider(long[] startTimesUs, float[] speeds) {
    checkArgument(startTimesUs.length == speeds.length);
    this.startTimesUs = startTimesUs;
    this.speeds = speeds;
  }

  @Override
  public float getSpeed(long timeUs) {
    int index =
        Util.binarySearchFloor(
            startTimesUs, timeUs, /* inclusive= */ true, /* stayInBounds= */ true);
    return speeds[index];
  }

  @Override
  public long getNextSpeedChangeTimeUs(long timeUs) {
    int index =
        Util.binarySearchCeil(
            startTimesUs, timeUs, /* inclusive= */ false, /* stayInBounds= */ false);
    return index < startTimesUs.length ? startTimesUs[index] : C.TIME_UNSET;
  }
}
