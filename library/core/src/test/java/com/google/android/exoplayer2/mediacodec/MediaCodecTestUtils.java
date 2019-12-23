/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.google.android.exoplayer2.mediacodec;

import static org.robolectric.Shadows.shadowOf;

import android.media.MediaCodec;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Testing utilities for MediaCodec related test classes */
public class MediaCodecTestUtils {
  /**
   * Compares if two {@link android.media.MediaCodec.BufferInfo} are equal by inspecting {@link
   * android.media.MediaCodec.BufferInfo#flags}, {@link android.media.MediaCodec.BufferInfo#size},
   * {@link android.media.MediaCodec.BufferInfo#presentationTimeUs} and {@link
   * android.media.MediaCodec.BufferInfo#offset}.
   */
  public static boolean areEqual(MediaCodec.BufferInfo lhs, MediaCodec.BufferInfo rhs) {
    return lhs.flags == rhs.flags
        && lhs.offset == rhs.offset
        && lhs.presentationTimeUs == rhs.presentationTimeUs
        && lhs.size == rhs.size;
  }

  /**
   * Blocks until all events of a shadow looper are executed or the specified time elapses.
   *
   * @param looper the shadow looper
   * @param time the time to wait
   * @param unit the time units
   * @return true if all events are executed, false if the time elapsed.
   * @throws InterruptedException if the Thread was interrupted while waiting.
   */
  public static boolean waitUntilAllEventsAreExecuted(Looper looper, long time, TimeUnit unit)
      throws InterruptedException {
    Handler handler = new Handler(looper);
    CountDownLatch latch = new CountDownLatch(1);
    handler.post(() -> latch.countDown());
    shadowOf(looper).idle();
    return latch.await(time, unit);
  }
}
