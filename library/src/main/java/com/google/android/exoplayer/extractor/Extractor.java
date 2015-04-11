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
package com.google.android.exoplayer.extractor;

import com.google.android.exoplayer.C;

import java.io.IOException;

/**
 * Facilitates extraction of data from a container format.
 */
public interface Extractor {

  /**
   * Returned by {@link #read(ExtractorInput, PositionHolder)} if the {@link ExtractorInput} passed
   * to the next {@link #read(ExtractorInput, PositionHolder)} is required to provide data
   * continuing from the position in the stream reached by the returning call.
   */
  public static final int RESULT_CONTINUE = 0;
  /**
   * Returned by {@link #read(ExtractorInput, PositionHolder)} if the {@link ExtractorInput} passed
   * to the next {@link #read(ExtractorInput, PositionHolder)} is required to provide data starting
   * from a specified position in the stream.
   */
  public static final int RESULT_SEEK = 1;
  /**
   * Returned by {@link #read(ExtractorInput, PositionHolder)} if the end of the
   * {@link ExtractorInput} was reached. Equal to {@link C#RESULT_END_OF_INPUT}.
   */
  public static final int RESULT_END_OF_INPUT = C.RESULT_END_OF_INPUT;

  /**
   * Initializes the extractor with an {@link ExtractorOutput}.
   *
   * @param output An {@link ExtractorOutput} to receive extracted data.
   */
  void init(ExtractorOutput output);

  /**
   * Extracts data read from a provided {@link ExtractorInput}.
   * <p>
   * Each read will extract at most one sample from the stream before returning.
   * <p>
   * In the common case, {@link #RESULT_CONTINUE} is returned to indicate that
   * {@link ExtractorInput} passed to the next read is required to provide data continuing from the
   * position in the stream reached by the returning call. If the extractor requires data to be
   * provided from a different position, then that position is set in {@code seekPosition} and
   * {@link #RESULT_SEEK} is returned. If the extractor reached the end of the data provided by the
   * {@link ExtractorInput}, then {@link #RESULT_END_OF_INPUT} is returned.
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @param seekPosition If {@link #RESULT_SEEK} is returned, this holder is updated to hold the
   *     position of the required data.
   * @return One of the {@code RESULT_} values defined in this interface.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  int read(ExtractorInput input, PositionHolder seekPosition)
      throws IOException, InterruptedException;

  /**
   * Notifies the extractor that a seek has occurred.
   * <p>
   * Following a call to this method, the {@link ExtractorInput} passed to the next invocation of
   * {@link #read(ExtractorInput, PositionHolder)} is required to provide data starting from any
   * random access position in the stream. Random access positions can be obtained from a
   * {@link SeekMap} that has been extracted and passed to the {@link ExtractorOutput}.
   */
  void seek();

}
