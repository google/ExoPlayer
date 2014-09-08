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
package com.google.android.exoplayer.parser;

import com.google.android.exoplayer.MediaFormat;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.SampleHolder;
import com.google.android.exoplayer.upstream.NonBlockingInputStream;

import java.util.Map;
import java.util.UUID;

/**
 * Facilitates extraction of media samples from a container format.
 */
public interface Extractor {

  /**
   * An attempt to read from the input stream returned insufficient data.
   */
  public static final int RESULT_NEED_MORE_DATA = 1;
  /**
   * The end of the input stream was reached.
   */
  public static final int RESULT_END_OF_STREAM = 2;
  /**
   * A media sample was read.
   */
  public static final int RESULT_READ_SAMPLE = 4;
  /**
   * Initialization data was read. The parsed data can be read using {@link #getFormat()} and
   * {@link #getPsshInfo}.
   */
  public static final int RESULT_READ_INIT = 8;
  /**
   * A sidx atom was read. The parsed data can be read using {@link #getIndex()}.
   */
  public static final int RESULT_READ_INDEX = 16;
  /**
   * The next thing to be read is a sample, but a {@link SampleHolder} was not supplied.
   */
  public static final int RESULT_NEED_SAMPLE_HOLDER = 32;

  /**
   * Returns the segment index parsed from the stream.
   *
   * @return The segment index, or null if a SIDX atom has yet to be parsed.
   */
  public SegmentIndex getIndex();

  /**
   * Returns true if the offsets in the index returned by {@link #getIndex()} are relative to the
   * first byte following the initialization data, or false if they are absolute (i.e. relative to
   * the first byte of the stream).
   *
   * @return True if the offsets are relative to the first byte following the initialization data.
   *     False otherwise.
   */
  public boolean hasRelativeIndexOffsets();

  /**
   * Returns the format of the samples contained within the media stream.
   *
   * @return The sample media format, or null if the format has yet to be parsed.
   */
  public MediaFormat getFormat();

  /**
   * Returns the pssh information parsed from the stream.
   *
   * @return The pssh information. May be null if pssh data has yet to be parsed, or if the stream
   *     does not contain any pssh data.
   */
  public Map<UUID, byte[]> getPsshInfo();

  /**
   * Consumes data from a {@link NonBlockingInputStream}.
   * <p>
   * The read terminates if the end of the input stream is reached, if an attempt to read from the
   * input stream returned 0 bytes of data, or if a sample is read. The returned flags indicate
   * both the reason for termination and data that was parsed during the read.
   *
   * @param inputStream The input stream from which data should be read.
   * @param out A {@link SampleHolder} into which the next sample should be read. If null then
   *     {@link #RESULT_NEED_SAMPLE_HOLDER} will be returned once a sample has been reached.
   * @return One or more of the {@code RESULT_*} flags defined in this class.
   * @throws ParserException If an error occurs parsing the media data.
   */
  public int read(NonBlockingInputStream inputStream, SampleHolder out) throws ParserException;

  /**
   * Seeks to a position before or equal to the requested time.
   *
   * @param seekTimeUs The desired seek time in microseconds.
   * @param allowNoop Allow the seek operation to do nothing if the seek time is in the current
   *     fragment run, is equal to or greater than the time of the current sample, and if there
   *     does not exist a sync frame between these two times.
   * @return True if the operation resulted in a change of state. False if it was a no-op.
   */
  public boolean seekTo(long seekTimeUs, boolean allowNoop);

}
