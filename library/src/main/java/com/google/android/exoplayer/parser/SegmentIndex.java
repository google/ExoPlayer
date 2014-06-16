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
package com.google.android.exoplayer.parser;

/**
 * Defines segments within a media stream.
 */
public final class SegmentIndex {

  /**
   * The size in bytes of the segment index as it exists in the stream.
   */
  public final int sizeBytes;

  /**
   * The number of segments.
   */
  public final int length;

  /**
   * The segment sizes, in bytes.
   */
  public final int[] sizes;

  /**
   * The segment byte offsets.
   */
  public final long[] offsets;

  /**
   * The segment durations, in microseconds.
   */
  public final long[] durationsUs;

  /**
   * The start time of each segment, in microseconds.
   */
  public final long[] timesUs;

  /**
   * @param sizeBytes The size in bytes of the segment index as it exists in the stream.
   * @param sizes The segment sizes, in bytes.
   * @param offsets The segment byte offsets.
   * @param durationsUs The segment durations, in microseconds.
   * @param timesUs The start time of each segment, in microseconds.
   */
  public SegmentIndex(int sizeBytes, int[] sizes, long[] offsets, long[] durationsUs,
      long[] timesUs) {
    this.sizeBytes = sizeBytes;
    this.length = sizes.length;
    this.sizes = sizes;
    this.offsets = offsets;
    this.durationsUs = durationsUs;
    this.timesUs = timesUs;
  }

}
