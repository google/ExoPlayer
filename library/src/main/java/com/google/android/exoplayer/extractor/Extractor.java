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
   * Returned by {@link #read(ExtractorInput)} if the {@link ExtractorInput} passed to the next
   * {@link #read(ExtractorInput)} is required to provide data continuing from the position in the
   * stream reached by the returning call.
   */
  public static final int RESULT_CONTINUE = 0;
  /**
   * Returned by {@link #read(ExtractorInput)} if the end of the {@link ExtractorInput} was reached.
   * Equal to {@link C#RESULT_END_OF_INPUT}.
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
   *
   * @param input The {@link ExtractorInput} from which data should be read.
   * @return One of the {@code RESULT_} values defined in this interface.
   * @throws IOException If an error occurred reading from the input.
   * @throws InterruptedException If the thread was interrupted.
   */
  int read(ExtractorInput input) throws IOException, InterruptedException;

}
