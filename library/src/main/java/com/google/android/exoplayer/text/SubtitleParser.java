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
package com.google.android.exoplayer.text;

import java.io.IOException;
import java.io.InputStream;

/**
 * Parses {@link Subtitle}s from {@link InputStream}s.
 */
public interface SubtitleParser {

  /**
   * Checks whether the parser supports a given subtitle mime type.
   *
   * @param mimeType A subtitle mime type.
   * @return Whether the mime type is supported.
   */
  public boolean canParse(String mimeType);

  /**
   * Parses a {@link Subtitle} from the provided {@link InputStream}.
   *
   * @param inputStream The stream from which to parse the subtitle.
   * @param inputEncoding The encoding of the input stream.
   * @param startTimeUs The start time of the subtitle.
   * @return A parsed representation of the subtitle.
   * @throws IOException If a problem occurred reading from the stream.
   */
  public Subtitle parse(InputStream inputStream, String inputEncoding, long startTimeUs)
      throws IOException;

}
