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
package com.google.android.exoplayer.extractor.ts;

import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Extracts individual samples from an elementary media stream, preserving original order.
 */
/* package */ abstract class ElementaryStreamReader {

  protected final TrackOutput output;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  protected ElementaryStreamReader(TrackOutput output) {
    this.output = output;
  }

  /**
   * Notifies the reader that a seek has occurred.
   * <p>
   * Following a call to this method, the data passed to the next invocation of
   * {@link #consume(ParsableByteArray, long, boolean)} will not be a continuation of the data that
   * was previously passed. Hence the reader should reset any internal state.
   */
  public abstract void seek();

  /**
   * Consumes (possibly partial) payload data.
   *
   * @param data The payload data to consume.
   * @param pesTimeUs The timestamp associated with the payload.
   * @param startOfPacket True if this is the first time this method is being called for the
   *     current packet. False otherwise.
   */
  public abstract void consume(ParsableByteArray data, long pesTimeUs, boolean startOfPacket);

  /**
   * Invoked once all of the payload data for a packet has been passed to
   * {@link #consume(ParsableByteArray, long, boolean)}. The next call to
   * {@link #consume(ParsableByteArray, long, boolean)} will have {@code startOfPacket == true}.
   */
  public abstract void packetFinished();

}
