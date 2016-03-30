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
import java.util.List;

/**
 * A source of media.
 */
public interface SampleSource {

  /**
   * Prepares the source, or does nothing if the source is already prepared.
   * <p>
   * {@link #selectTracks(List, List, long)} <b>must</b> be called after the source is prepared to
   * make an initial track selection. This is true even if the caller does not wish to select any
   * tracks.
   *
   * @param positionUs The player's current playback position.
   * @return True if the source is prepared, false otherwise.
   * @throws IOException If there's an error preparing the source.
   */
  boolean prepare(long positionUs) throws IOException;

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
  TrackGroupArray getTrackGroups();

  /**
   * Modifies the selected tracks.
   * <p>
   * {@link TrackStream}s corresponding to tracks being unselected are passed in {@code oldStreams}.
   * Tracks being selected are specified in {@code newSelections}. Each new {@link TrackSelection}
   * must have a {@link TrackSelection#group} index distinct from those of currently enabled tracks,
   * except for those being unselected.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @param oldStreams {@link TrackStream}s corresponding to tracks being unselected. May be empty
   *     but must not be null.
   * @param newSelections {@link TrackSelection}s that define tracks being selected. May be empty
   *     but must not be null.
   * @param positionUs The current playback position in microseconds.
   * @return The {@link TrackStream}s corresponding to each of the newly selected tracks.
   */
  TrackStream[] selectTracks(List<TrackStream> oldStreams, List<TrackSelection> newSelections,
      long positionUs);

  /**
   * Indicates to the source that it should continue buffering data for its enabled tracks.
   * <p>
   * This method should only be called when at least one track is selected.
   *
   * @param positionUs The current playback position.
   */
  void continueBuffering(long positionUs);

  /**
   * Returns an estimate of the position up to which data is buffered for the enabled tracks.
   * <p>
   * This method should only be called when at least one track is selected.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered,
   *     or {@link C#END_OF_SOURCE_US} if the track is fully buffered, or {@link C#UNKNOWN_TIME_US}
   *     if no estimate is available.
   */
  long getBufferedPositionUs();

  /**
   * Seeks to the specified time in microseconds.
   * <p>
   * This method should only be called when at least one track is selected.
   *
   * @param positionUs The seek position in microseconds.
   */
  void seekToUs(long positionUs);

  /**
   * Releases the source.
   * <p>
   * This method should be called when the source is no longer required. It may be called in any
   * state.
   */
  void release();

}
