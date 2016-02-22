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

import java.io.IOException;

/**
 * A stream of media data.
 */
public interface TrackStream {

  /**
   * The end of stream has been reached.
   */
  int END_OF_STREAM = -1;
  /**
   * Nothing was read.
   */
  int NOTHING_READ = -2;
  /**
   * A sample was read.
   */
  int SAMPLE_READ = -3;
  /**
   * A format was read.
   */
  int FORMAT_READ = -4;
  /**
   * Returned from {@link #readReset()} to indicate no reset is required.
   */
  long NO_RESET = Long.MIN_VALUE;

  /**
   * Returns whether data is available to be read.
   * <p>
   * Note: If the stream has ended then {@link #END_OF_STREAM} can always be read from
   * {@link #readData(FormatHolder, SampleHolder)}. Hence an ended stream is always ready.
   *
   * @return True if data is available to be read. False otherwise.
   */
  boolean isReady();

  /**
   * If there's an underlying error preventing data from being read, it's thrown by this method.
   * If not, this method does nothing.
   *
   * @throws IOException The underlying error.
   */
  void maybeThrowError() throws IOException;

  /**
   * Attempts to read a pending reset.
   *
   * @return If a reset was read then the position after the reset. Else {@link #NO_RESET}.
   */
  long readReset();

  /**
   * Attempts to read the next format or sample.
   * <p>
   * This method will always return {@link #NOTHING_READ} in the case that there's a pending
   * discontinuity to be read from {@link #readReset} for the specified track.
   *
   * @param formatHolder A {@link FormatHolder} to populate in the case of a new format.
   * @param sampleHolder A {@link SampleHolder} to populate in the case of a new sample. If the
   *     caller requires the sample data then it must ensure that {@link SampleHolder#data}
   *     references a valid output buffer.
   * @return The result, which can be {@link #END_OF_STREAM}, {@link #NOTHING_READ},
   *     {@link #FORMAT_READ} or {@link #SAMPLE_READ}.
   */
  int readData(FormatHolder formatHolder, SampleHolder sampleHolder);

  /**
   * Disables the track.
   */
  void disable();

}
