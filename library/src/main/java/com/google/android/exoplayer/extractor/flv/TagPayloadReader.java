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
package com.google.android.exoplayer.extractor.flv;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.extractor.TrackOutput;
import com.google.android.exoplayer.util.ParsableByteArray;

/**
 * Extracts individual samples from FLV tags, preserving original order.
 */
/* package */ abstract class TagPayloadReader {

  protected final TrackOutput output;

  // Duration of the track
  protected long durationUs;

  /**
   * @param output A {@link TrackOutput} to which samples should be written.
   */
  protected TagPayloadReader(TrackOutput output) {
    this.output = output;
    this.durationUs = C.UNKNOWN_TIME_US;
  }

  /**
   * Notifies the reader that a seek has occurred.
   * <p>
   * Following a call to this method, the data passed to the next invocation of
   * {@link #consume(ParsableByteArray, long)} will not be a continuation of the data that
   * was previously passed. Hence the reader should reset any internal state.
   */
  public abstract void seek();

  /**
   * Parses tag header
   * @param data Buffer where the tag header is stored
   * @return True if header was parsed successfully and then payload should be read;
   * Otherwise, false
   * @throws UnsupportedTrack
   */
  protected abstract boolean parseHeader(ParsableByteArray data) throws UnsupportedTrack;

  /**
   * Parses tag payload
   * @param data Buffer where tag payload is stored
   * @param timeUs Time position of the frame
   */
  protected abstract void parsePayload(ParsableByteArray data, long timeUs);

  /**
   * Consumes payload data.
   *
   * @param data The payload data to consume.
   * @param timeUs The timestamp associated with the payload.
   */
  public void consume(ParsableByteArray data, long timeUs) throws UnsupportedTrack {
    if (parseHeader(data)) {
      parsePayload(data, timeUs);
    }
  }

  /**
   * Sets duration in microseconds
   * @param durationUs duration in microseconds
   */
  public void setDurationUs(long durationUs) {
    this.durationUs = durationUs;
  }

  public long getDurationUs() {
    return durationUs;
  }
  /**
   * Thrown when format described in the AudioTrack is not supported
   */
  public static final class UnsupportedTrack extends Exception {

    public UnsupportedTrack(String msg) {
      super(msg);
    }

  }
}
