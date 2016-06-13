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
 * <p>
 * Counters should be written from the playback thread only. Counters may be read from any thread.
 * To ensure that the counter values are correctly reflected between threads, users of this class
 * should invoke {@link #ensureUpdated()} prior to reading and after writing.
 */
public final class CodecCounters {

  /**
   * The number of times the codec has been initialized.
   */
  public int codecInitCount;
  /**
   * The number of times the codec has been released.
   */
  public int codecReleaseCount;
  /**
   * The number of queued input buffers.
   */
  public int inputBufferCount;
  /**
   * The number of rendered output buffers.
   */
  public int renderedOutputBufferCount;
  /**
   * The number of skipped output buffers.
   * <p>
   * A skipped output buffer is an output buffer that was deliberately not rendered.
   */
  public int skippedOutputBufferCount;
  /**
   * The number of dropped output buffers.
   * <p>
   * A dropped output buffer is an output buffer that was supposed to be rendered, but was instead
   * dropped because it could not be rendered in time.
   */
  public int droppedOutputBufferCount;
  /**
   * The maximum number of dropped output buffers without an interleaving rendered output buffer.
   * <p>
   * Skipped output buffers are ignored for the purposes of calculating this value.
   */
  public int maxConsecutiveDroppedOutputBufferCount;

  /**
   * Should be invoked from the playback thread after the counters have been updated. Should also
   * be invoked from any other thread that wishes to read the counters, before reading. These calls
   * ensure that counter updates are made visible to the reading threads.
   */
  public synchronized void ensureUpdated() {
    // Do nothing. The use of synchronized ensures a memory barrier should another thread also
    // call this method.
  }

  /**
   * Merges the counts from {@code other} into this instance.
   *
   * @param other The {@link CodecCounters} to merge into this instance.
   */
  public void merge(CodecCounters other) {
    codecInitCount += other.codecInitCount;
    codecReleaseCount += other.codecReleaseCount;
    inputBufferCount += other.inputBufferCount;
    renderedOutputBufferCount += other.renderedOutputBufferCount;
    skippedOutputBufferCount += other.skippedOutputBufferCount;
    droppedOutputBufferCount += other.droppedOutputBufferCount;
    maxConsecutiveDroppedOutputBufferCount = Math.max(maxConsecutiveDroppedOutputBufferCount,
        other.maxConsecutiveDroppedOutputBufferCount);
  }

}
