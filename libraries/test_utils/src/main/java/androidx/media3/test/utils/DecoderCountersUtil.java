/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertWithMessage;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DecoderCounters;

/** Assertions for {@link DecoderCounters}. */
@UnstableApi
public final class DecoderCountersUtil {

  private DecoderCountersUtil() {}

  /**
   * Returns the sum of the skipped, dropped and rendered buffers.
   *
   * @param counters The counters for which the total should be calculated.
   * @return The sum of the skipped, dropped and rendered buffers.
   */
  public static int getTotalBufferCount(DecoderCounters counters) {
    counters.ensureUpdated();
    return counters.skippedInputBufferCount
        + counters.skippedOutputBufferCount
        + counters.droppedBufferCount
        + counters.renderedOutputBufferCount;
  }

  public static void assertSkippedOutputBufferCount(
      String name, DecoderCounters counters, int expected) {
    counters.ensureUpdated();
    int actual = counters.skippedOutputBufferCount;
    assertWithMessage(
            "Codec(%s) skipped an unexpected number of buffers. Counters:\n%s", name, counters)
        .that(actual)
        .isEqualTo(expected);
  }

  /** Asserts that the input and output values in {@code counters} are self-consistent. */
  public static void assertTotalBufferCount(String name, DecoderCounters counters) {
    // We allow one fewer output buffer due to the way that MediaCodecRenderer and the
    // underlying decoders handle the end of stream. This should be tightened up in the future.
    int totalInputBufferCount =
        counters.skippedInputBufferCount
            + counters.droppedInputBufferCount
            + counters.queuedInputBufferCount;
    assertTotalBufferCount(
        name,
        counters,
        /* minCount= */ totalInputBufferCount - 1,
        /* maxCount= */ totalInputBufferCount);
  }

  public static void assertTotalBufferCount(
      String name, DecoderCounters counters, int minCount, int maxCount) {
    int actual = getTotalBufferCount(counters);
    assertWithMessage("Codec(%s) output too few buffers. Counters:\n%s", name, counters)
        .that(actual)
        .isAtLeast(minCount);
    assertWithMessage("Codec(%s) output too many buffers. Counters:\n%s", name, counters)
        .that(actual)
        .isAtMost(maxCount);
  }

  public static void assertDroppedBufferLimit(String name, DecoderCounters counters, int limit) {
    counters.ensureUpdated();
    int actual = counters.droppedBufferCount;
    assertWithMessage(
            "Codec(%s) was late decoding too many buffers. Counters:\n%s: ", name, counters)
        .that(actual)
        .isAtMost(limit);
  }

  public static void assertConsecutiveDroppedBufferLimit(
      String name, DecoderCounters counters, int limit) {
    counters.ensureUpdated();
    int actual = counters.maxConsecutiveDroppedBufferCount;
    assertWithMessage(
            "Codec(%s) was late decoding too many buffers consecutively. Counters:\n%s",
            name, counters)
        .that(actual)
        .isAtMost(limit);
  }

  public static void assertVideoFrameProcessingOffsetSampleCount(
      String name, DecoderCounters counters, int minCount, int maxCount) {
    int actual = counters.videoFrameProcessingOffsetCount;
    assertWithMessage(
            "Codec(%s) videoFrameProcessingOffsetSampleCount too low. Counters:\n%s",
            name, counters)
        .that(actual)
        .isAtLeast(minCount);
    assertWithMessage(
            "Codec(%s) videoFrameProcessingOffsetSampleCount too high. Counters:\n%s",
            name, counters)
        .that(actual)
        .isAtMost(maxCount);
  }
}
