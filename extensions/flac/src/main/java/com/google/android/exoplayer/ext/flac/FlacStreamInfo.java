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
package com.google.android.exoplayer.ext.flac;

/**
 * Holder for flac stream info.
 */
/* package */ final class FlacStreamInfo {
  public final int minBlockSize;
  public final int maxBlockSize;
  public final int minFrameSize;
  public final int maxFrameSize;
  public final int sampleRate;
  public final int channels;
  public final int bitsPerSample;
  public final long totalSamples;

  public FlacStreamInfo(int minBlockSize, int maxBlockSize, int minFrameSize, int maxFrameSize,
      int sampleRate, int channels, int bitsPerSample, long totalSamples) {
    this.minBlockSize = minBlockSize;
    this.maxBlockSize = maxBlockSize;
    this.minFrameSize = minFrameSize;
    this.maxFrameSize = maxFrameSize;
    this.sampleRate = sampleRate;
    this.channels = channels;
    this.bitsPerSample = bitsPerSample;
    this.totalSamples = totalSamples;
  }

  public int maxDecodedFrameSize() {
    return maxBlockSize * channels * 2;
  }

  public int bitRate() {
    return bitsPerSample * sampleRate;
  }

  public long durationUs() {
    return (totalSamples * 1000000L) / sampleRate;
  }
}
