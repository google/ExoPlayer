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
package com.google.android.exoplayer;

/**
 * Maintains codec event counts, for debugging purposes only.
 */
public final class CodecCounters {

  public volatile long codecInitCount;
  public volatile long codecReleaseCount;
  public volatile long outputFormatChangedCount;
  public volatile long outputBuffersChangedCount;
  public volatile long queuedInputBufferCount;
  public volatile long inputBufferWaitingForSampleCount;
  public volatile long keyframeCount;
  public volatile long queuedEndOfStreamCount;
  public volatile long renderedOutputBufferCount;
  public volatile long skippedOutputBufferCount;
  public volatile long droppedOutputBufferCount;
  public volatile long discardedSamplesCount;

  /**
   * Resets all counts to zero.
   */
  public void zeroAllCounts() {
    codecInitCount = 0;
    codecReleaseCount = 0;
    outputFormatChangedCount = 0;
    outputBuffersChangedCount = 0;
    queuedInputBufferCount = 0;
    inputBufferWaitingForSampleCount = 0;
    keyframeCount = 0;
    queuedEndOfStreamCount = 0;
    renderedOutputBufferCount = 0;
    skippedOutputBufferCount = 0;
    droppedOutputBufferCount = 0;
    discardedSamplesCount = 0;
  }

  public String getDebugString() {
    StringBuilder builder = new StringBuilder();
    builder.append("cic(").append(codecInitCount).append(")");
    builder.append("crc(").append(codecReleaseCount).append(")");
    builder.append("ofc(").append(outputFormatChangedCount).append(")");
    builder.append("obc(").append(outputBuffersChangedCount).append(")");
    builder.append("qib(").append(queuedInputBufferCount).append(")");
    builder.append("wib(").append(inputBufferWaitingForSampleCount).append(")");
    builder.append("kfc(").append(keyframeCount).append(")");
    builder.append("qes(").append(queuedEndOfStreamCount).append(")");
    builder.append("ren(").append(renderedOutputBufferCount).append(")");
    builder.append("sob(").append(skippedOutputBufferCount).append(")");
    builder.append("dob(").append(droppedOutputBufferCount).append(")");
    builder.append("dsc(").append(discardedSamplesCount).append(")");
    return builder.toString();
  }

}
