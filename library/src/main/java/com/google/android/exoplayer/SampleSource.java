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
   * Returns the {@link TrackGroup}s exposed by the source.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The {@link TrackGroup}s.
   */
  public TrackGroupArray getTrackGroups();

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
   * Enables the source to read a track defined by a {@link TrackSelection}. A {@link TrackStream}
   * is returned through which the track's data can be read.
   * <p>
   * This method should only be called after the source has been prepared, and when there are no
   * other enabled tracks with the same {@link TrackSelection#group} index. Note that
   * {@code TrackSelection#tracks} must be of length 1 unless {@link TrackGroup#adaptive} is true
   * for the group.
   *
   * @param selection Defines the track.
   * @param positionUs The current playback position in microseconds.
   * @return A {@link TrackStream} from which the enabled track's data can be read.
   */
  public TrackStream enable(TrackSelection selection, long positionUs);

  /**
   * Releases the source.
   * <p>
   * This method should be called when the source is no longer required.
   */
  void release();

}
