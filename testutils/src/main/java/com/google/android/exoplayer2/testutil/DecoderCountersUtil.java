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
package com.google.android.exoplayer2.testutil;

import com.google.android.exoplayer2.decoder.DecoderCounters;
import junit.framework.TestCase;

/**
 * Assertions for {@link DecoderCounters}.
 */
public final class DecoderCountersUtil {

  private DecoderCountersUtil() {}

  /**
   * Returns the sum of the skipped, dropped and rendered buffers.
   *
   * @param counters The counters for which the total should be calculated.
   * @return The sum of the skipped, dropped and rendered buffers.
   */
  public static int getTotalOutputBuffers(DecoderCounters counters) {
    return counters.skippedOutputBufferCount + counters.droppedOutputBufferCount
        + counters.renderedOutputBufferCount;
  }

  public static void assertSkippedOutputBufferCount(String name, DecoderCounters counters,
      int expected) {
    counters.ensureUpdated();
    int actual = counters.skippedOutputBufferCount;
    TestCase.assertEquals("Codec(" + name + ") skipped " + actual + " buffers. Expected "
        + expected + ".", expected, actual);
  }

  public static void assertTotalOutputBufferCount(String name, DecoderCounters counters,
      int minCount, int maxCount) {
    counters.ensureUpdated();
    int actual = getTotalOutputBuffers(counters);
    TestCase.assertTrue("Codec(" + name + ") output " + actual + " buffers. Expected in range ["
        + minCount + ", " + maxCount + "].", minCount <= actual && actual <= maxCount);
  }

  public static void assertDroppedOutputBufferLimit(String name, DecoderCounters counters,
      int limit) {
    counters.ensureUpdated();
    int actual = counters.droppedOutputBufferCount;
    TestCase.assertTrue("Codec(" + name + ") was late decoding: " + actual + " buffers. "
        + "Limit: " + limit + ".", actual <= limit);
  }

  public static void assertConsecutiveDroppedOutputBufferLimit(String name,
      DecoderCounters counters, int limit) {
    counters.ensureUpdated();
    int actual = counters.maxConsecutiveDroppedOutputBufferCount;
    TestCase.assertTrue("Codec(" + name + ") was late decoding: " + actual
        + " buffers consecutively. " + "Limit: " + limit + ".", actual <= limit);
  }

}
