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
package com.google.android.exoplayer.source;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.MediaFormatHolder;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.SampleSource;
import com.google.android.exoplayer.TrackInfo;
import com.google.android.exoplayer.TrackRenderer;

import java.io.IOException;

/**
 * Extractor for reading track metadata and samples stored in tracks.
 *
 * <p>Call {@link #prepare} until it returns {@code true}, then access track metadata via
 * {@link #getTrackInfos} and {@link #getTrackMediaFormat}.
 *
 * <p>Pass indices of tracks to read from to {@link #selectTrack}. A track can later be deselected
 * by calling {@link #deselectTrack}. It is safe to select/deselect tracks after reading sample
 * data or seeking. Initially, all tracks are deselected.
 *
 * <p>Call {@link #release()} when the extractor is no longer needed to free resources.
 */
public interface SampleExtractor {

  /**
   * Prepares the extractor for reading track metadata and samples.
   *
   * @return Whether the source is ready; if {@code false}, {@link #prepare()} must be called again.
   * @throws IOException Thrown if the source can't be read.
   */
  boolean prepare() throws IOException;

  /** Returns track information about all tracks that can be selected. */
  TrackInfo[] getTrackInfos();

  /** Selects the track at {@code index} for reading sample data. */
  void selectTrack(int index);

  /** Deselects the track at {@code index}, so no more samples will be read from that track. */
  void deselectTrack(int index);

  /**
   * Returns an estimate of the position up to which data is buffered.
   *
   * <p>This method should not be called until after the extractor has been successfully prepared.
   *
   * @return An estimate of the absolute position in microseconds up to which data is buffered,
   *     or {@link TrackRenderer#END_OF_TRACK_US} if data is buffered to the end of the stream, or
   *     {@link TrackRenderer#UNKNOWN_TIME_US} if no estimate is available.
   */
  long getBufferedPositionUs();

  /**
   * Seeks to the specified time in microseconds.
   *
   * <p>This method should not be called until after the extractor has been successfully prepared.
   *
   * @param positionUs The seek position in microseconds.
   */
  void seekTo(long positionUs);

  /** Stores the {@link MediaFormat} of {@code track}. */
  void getTrackMediaFormat(int track, MediaFormatHolder mediaFormatHolder);

  /**
   * Reads the next sample in the track at index {@code track} into {@code sampleHolder}, returning
   * {@link SampleSource#SAMPLE_READ} if it is available.
   *
   * <p>Advances to the next sample if a sample was read.
   *
   * @param track The index of the track from which to read a sample.
   * @param sampleHolder The holder for read sample data, if {@link SampleSource#SAMPLE_READ} is
   *     returned.
   * @return {@link SampleSource#SAMPLE_READ} if a sample was read into {@code sampleHolder}, or
   *     {@link SampleSource#END_OF_STREAM} if the last samples in all tracks have been read, or
   *     {@link SampleSource#NOTHING_READ} if the sample cannot be read immediately as it is not
   *     loaded.
   * @throws IOException Thrown if the source can't be read.
   */
  int readSample(int track, SampleHolder sampleHolder) throws IOException;

  /** Releases resources associated with this extractor. */
  void release();

}
