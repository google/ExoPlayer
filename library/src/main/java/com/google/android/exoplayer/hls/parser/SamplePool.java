/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.google.android.exoplayer.hls.parser;

/**
 * A pool from which the extractor can obtain sample objects for internal use.
 *
 * TODO: Over time the average size of a sample in the video pool will become larger, as the
 * proportion of samples in the pool that have at some point held a keyframe grows. Currently
 * this leads to inefficient memory usage, since samples large enough to hold keyframes end up
 * being used to hold non-keyframes. We need to fix this.
 */
public class SamplePool {

  private static final int[] DEFAULT_SAMPLE_SIZES;
  static {
    DEFAULT_SAMPLE_SIZES = new int[Sample.TYPE_COUNT];
    DEFAULT_SAMPLE_SIZES[Sample.TYPE_VIDEO] = 10 * 1024;
    DEFAULT_SAMPLE_SIZES[Sample.TYPE_AUDIO] = 512;
    DEFAULT_SAMPLE_SIZES[Sample.TYPE_MISC] = 512;
  }

  private final Sample[] pools;

  public SamplePool() {
    pools = new Sample[Sample.TYPE_COUNT];
  }

  /* package */ synchronized Sample get(int type) {
    if (pools[type] == null) {
      return new Sample(type, DEFAULT_SAMPLE_SIZES[type]);
    }
    Sample sample = pools[type];
    pools[type] = sample.nextInPool;
    sample.nextInPool = null;
    return sample;
  }

  /* package */ synchronized void recycle(Sample sample) {
    sample.reset();
    sample.nextInPool = pools[sample.type];
    pools[sample.type] = sample;
  }

}
