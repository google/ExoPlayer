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
   * The source has not yet been prepared.
   */
  int STATE_UNPREPARED = 0;
  /**
   * The source is prepared and in the track selection state.
   */
  int STATE_SELECTING_TRACKS = 1;
  /**
   * The source is prepared and in the reading state.
   */
  int STATE_READING = 2;
  /**
   * The source has been released.
   */
  int STATE_RELEASED = 3;

  /**
   * Returns the state of the source.
   *
   * @return The state of the source. One of {@link #STATE_UNPREPARED},
   *     {@link #STATE_SELECTING_TRACKS}, {@link #STATE_READING} and {@link #STATE_RELEASED}.
   */
  int getState();

  /**
   * Prepares the source.
   * <p>
   * If preparation cannot complete immediately then the call will return {@code false} rather than
   * block and the state will remain unchanged. If true is returned the state will have changed
   * to {@link #STATE_SELECTING_TRACKS} for the initial track selection to take place.
   * <p>
   * This method should only be called when the state is {@link #STATE_UNPREPARED}.
   *
   * @param positionUs The player's current playback position.
   * @return True if the source was prepared, false otherwise.
   * @throws IOException If there's an error preparing the source.
   */
  boolean prepare(long positionUs) throws IOException;

  /**
   * Returns the duration of the source.
   * <p>
   * This method should only be called when the state is {@link #STATE_SELECTING_TRACKS} or
   * {@link #STATE_READING}.
   *
   * @return The duration of the source in microseconds, or {@link C#UNKNOWN_TIME_US} if the
   *     duration is not known.
   */
  long getDurationUs();

  /**
   * Returns the {@link TrackGroup}s exposed by the source.
   * <p>
   * This method should only be called when the state is {@link #STATE_SELECTING_TRACKS} or
   * {@link #STATE_READING}.
   *
   * @return The {@link TrackGroup}s.
   */
  TrackGroupArray getTrackGroups();

  /**
   * Enters the track selection state.
   * <p>
   * The selected tracks are initially unchanged, but may be modified by calls to
   * {@link #unselectTrack(TrackStream)} and {@link #selectTrack(TrackSelection, long)}, followed by
   * a call to {@link #endTrackSelection(long)}.
   * <p>
   * This method should only be called when the state is {@link #STATE_READING}.
   */
  void startTrackSelection();

  /**
   * Selects a track defined by a {@link TrackSelection}. A {@link TrackStream} is returned through
   * which the track's data can be read.
   * <p>
   * The {@link TrackSelection} must have a {@link TrackSelection#group} index distinct from those
   * of other enabled tracks, and a {@code TrackSelection#length} of 1 unless
   * {@link TrackGroup#adaptive} is true for the selected group.
   * <p>
   * This method should only be called when the state is {@link #STATE_SELECTING_TRACKS}.
   *
   * @param selection Defines the track.
   * @param positionUs The current playback position in microseconds.
   * @return A {@link TrackStream} from which the enabled track's data can be read.
   */
  TrackStream selectTrack(TrackSelection selection, long positionUs);

  /**
   * Unselects a track previously selected by calling {@link #selectTrack(TrackSelection, long)}.
   * <p>
   * This method should only be called when the state is {@link #STATE_SELECTING_TRACKS}.
   *
   * @param stream The {@link TrackStream} obtained from the corresponding call to
   *     {@link #selectTrack(TrackSelection, long)}.
   */
  void unselectTrack(TrackStream stream);

  /**
   * Exits the track selection state.
   * <p>
   * This method should only be called when the state is {@link #STATE_SELECTING_TRACKS}.
   *
   * @param positionUs The current playback position in microseconds.
   */
  void endTrackSelection(long positionUs);

  /**
   * Indicates to the source that it should continue buffering data for its enabled tracks.
   * <p>
   * This method should only be called when the state is {@link #STATE_READING} and at least one
   * track is selected.
   *
   * @param positionUs The current playback position.
   */
  void continueBuffering(long positionUs);

  /**
   * Returns an estimate of the position up to which data is buffered for the enabled tracks.
   * <p>
   * This method should only be called when the state is {@link #STATE_READING} and at least one
   * track is selected.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered,
   *     or {@link C#END_OF_SOURCE_US} if the track is fully buffered, or {@link C#UNKNOWN_TIME_US}
   *     if no estimate is available.
   */
  long getBufferedPositionUs();

  /**
   * Seeks to the specified time in microseconds.
   * <p>
   * This method should only be called when the state is {@link #STATE_READING} and at least one
   * track is selected.
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
