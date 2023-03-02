/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.content.Context;
import androidx.annotation.IntRange;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.UnstableApi;

/**
 * Caches the input frames.
 *
 * <p>Example usage: cache the processed frames when presenting them on screen, to accommodate for
 * the possible fluctuation in video frame processing time between frames.
 */
@UnstableApi
public final class FrameCache implements GlEffect {
  /** The capacity of the frame cache. */
  public final int capacity;

  /**
   * Creates a new instance.
   *
   * <p>The {@code capacity} should be chosen carefully. OpenGL could crash unexpectedly if the
   * device is not capable of allocating the requested buffer.
   *
   * <p>Currently up to 8 frames can be cached in one {@code FrameCache} instance.
   *
   * @param capacity The capacity of the frame cache, must be greater than zero.
   */
  public FrameCache(@IntRange(from = 1, to = 8) int capacity) {
    // TODO(b/243033952) Consider adding a global limit across many FrameCache instances.
    checkArgument(capacity > 0 && capacity < 9);
    this.capacity = capacity;
  }

  @Override
  public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
      throws VideoFrameProcessingException {
    return new FrameCacheGlShaderProgram(context, capacity, useHdr);
  }
}
