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
 * A source of media.
 */
public interface SampleSource {

  /**
   * Prepares the source.
   * <p>
   * If preparation cannot complete immediately then the call will return {@code false} rather than
   * block. The method can be called repeatedly until the return value indicates success.
   *
   * @param positionUs The player's current playback position.
   * @return True if the source was prepared, false otherwise.
   * @throws IOException If there's an error preparing the source.
   */
  boolean prepare(long positionUs) throws IOException;

  /**
   * Returns whether the source is prepared.
   *
   * @return True if the source is prepared. False otherwise.
   */
  boolean isPrepared();

  /**
   * Returns the duration of the source.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The duration of the source in microseconds, or {@link C#UNKNOWN_TIME_US} if the
   *     duration is not known.
   */
  long getDurationUs();

  /**
   * Returns the number of track groups exposed by the source.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The number of track groups exposed by the source.
   */
  public int getTrackGroupCount();

  /**
   * Returns the {@link TrackGroup} at the specified index.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @int group The group index.
   * @return The corresponding {@link TrackGroup}.
   */
  public TrackGroup getTrackGroup(int group);

  /**
   * Indicates to the source that it should continue buffering data for its enabled tracks.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param positionUs The current playback position.
   */
  void continueBuffering(long positionUs);

  /**
   * Returns an estimate of the position up to which data is buffered for the enabled tracks.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered,
   *     or {@link C#END_OF_SOURCE_US} if the track is fully buffered, or {@link C#UNKNOWN_TIME_US}
   *     if no estimate is available. If no tracks are enabled then {@link C#END_OF_SOURCE_US} is
   *     returned.
   */
  long getBufferedPositionUs();

  /**
   * Seeks to the specified time in microseconds.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param positionUs The seek position in microseconds.
   */
  void seekToUs(long positionUs);

  /**
   * Enables the specified group to read the specified tracks. A {@link TrackStream} is returned
   * through which the enabled track's data can be read.
   * <p>
   * This method should only be called after the source has been prepared, and when the specified
   * group is disabled. Note that {@code tracks.length} is only permitted to be greater than one
   * if {@link TrackGroup#adaptive} is true for the group.
   *
   * @param group The group index.
   * @param tracks The track indices.
   * @param positionUs The current playback position in microseconds.
   * @return A {@link TrackStream} from which the enabled track's data can be read.
   */
  public TrackStream enable(int group, int[] tracks, long positionUs);

  /**
   * Releases the source.
   * <p>
   * This method should be called when the source is no longer required.
   */
  void release();

  /**
   * A stream of data corresponding to a single {@link SampleSource} track.
   */
  interface TrackStream {

    /**
     * The end of stream has been reached.
     */
    static final int END_OF_STREAM = -1;
    /**
     * Nothing was read.
     */
    static final int NOTHING_READ = -2;
    /**
     * A sample was read.
     */
    static final int SAMPLE_READ = -3;
    /**
     * A format was read.
     */
    static final int FORMAT_READ = -4;
    /**
     * Returned from {@link #readReset()} to indicate no reset is required.
     */
    static final long NO_RESET = Long.MIN_VALUE;

    /**
     * Returns whether data is available to be read.
     * <p>
     * Note: If the stream has ended then {@link #END_OF_STREAM} can always be read from
     * {@link #readData(MediaFormatHolder, SampleHolder)}. Hence an ended stream is always ready.
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
     * @param formatHolder A {@link MediaFormatHolder} to populate in the case of a new format.
     * @param sampleHolder A {@link SampleHolder} to populate in the case of a new sample. If the
     *     caller requires the sample data then it must ensure that {@link SampleHolder#data}
     *     references a valid output buffer.
     * @return The result, which can be {@link #END_OF_STREAM}, {@link #NOTHING_READ},
     *     {@link #FORMAT_READ} or {@link #SAMPLE_READ}.
     */
    int readData(MediaFormatHolder formatHolder, SampleHolder sampleHolder);

    /**
     * Disables the track.
     */
    void disable();

  }

}
