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

import com.google.android.exoplayer.upstream.Allocator;

import java.io.IOException;
import java.util.List;

/**
 * A source of media.
 */
public interface SampleSource extends SequenceableLoader {

  /**
   * A callback to be notified of {@link SampleSource} events.
   */
  interface Callback extends SequenceableLoader.Callback<SampleSource> {

    /**
     * Invoked by the source when preparation completes.
     * <p>
     * May be called from any thread. After invoking this method, the source can expect
     * {@link #selectTracks(List, List, long)} to be invoked when the initial track selection.
     *
     * @param source The prepared source.
     */
    void onSourcePrepared(SampleSource source);

  }

  /**
   * Starts preparation of the source.
   * <p>
   * {@link Callback#onSourcePrepared(SampleSource)} is invoked when preparation completes. If
   * preparation fails, {@link #maybeThrowPrepareError()} will throw an {@link IOException} if
   * invoked.
   *
   * @param callback A callback to receive updates from the source.
   * @param allocator An {@link Allocator} from which to obtain media buffer allocations.
   * @param positionUs The player's current playback position.
   */
  void prepare(Callback callback, Allocator allocator, long positionUs);

  /**
   * Throws an error that's preventing the source from becoming prepared. Does nothing if no such
   * error exists.
   * <p>
   * This method should only be called before the source has completed preparation.
   *
   * @throws IOException The underlying error.
   */
  void maybeThrowPrepareError() throws IOException;

  /**
   * Returns the duration of the source in microseconds, or {@link C#UNSET_TIME_US} if not known.
   * <p>
   * If {@link #getBufferedPositionUs()} returns {@link C#END_OF_SOURCE_US}, the duration is
   * guaranteed to be known.
   * <p>
   * This method should only be called after the source has been prepared.
   *
   * @return The duration of the source in microseconds, or {@link C#UNSET_TIME_US} if the duration
   *     is not known.
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
   * Attempts to read a discontinuity.
   * <p>
   * After this method has returned a value other than {@link C#UNSET_TIME_US}, all
   * {@link TrackStream}s provided by the source are guaranteed to start from a key frame.
   *
   * @return If a discontinuity was read then the playback position in microseconds after the
   *     discontinuity. Else {@link C#UNSET_TIME_US}.
   */
  long readDiscontinuity();

  /**
   * Returns an estimate of the position up to which data is buffered for the enabled tracks.
   * <p>
   * This method should only be called when at least one track is selected.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered, or
   *     {@link C#END_OF_SOURCE_US} if the track is fully buffered.
   */
  long getBufferedPositionUs();

  /**
   * Attempts to seek to the specified position in microseconds.
   * <p>
   * After this method has been called, all {@link TrackStream}s provided by the source are
   * guaranteed to start from a key frame.
   * <p>
   * This method should only be called when at least one track is selected.
   *
   * @param positionUs The seek position in microseconds.
   * @return The actual position to which the source was seeked, in microseconds.
   */
  long seekToUs(long positionUs);

  /**
   * Releases the source.
   * <p>
   * This method should be called when the source is no longer required. It may be called in any
   * state.
   */
  void release();

}
