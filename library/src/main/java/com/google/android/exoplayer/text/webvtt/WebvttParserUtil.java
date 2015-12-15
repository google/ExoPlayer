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
package com.google.android.exoplayer.text.webvtt;

import com.google.android.exoplayer.ParserException;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for parsing WebVTT data.
 */
public final class WebvttParserUtil {

  private static final Pattern HEADER = Pattern.compile("^\uFEFF?WEBVTT((\u0020|\u0009).*)?$");
  private static final Pattern COMMENT = Pattern.compile("^NOTE((\u0020|\u0009).*)?$");
  private static final Pattern CUE_HEADER = Pattern.compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$");

  private WebvttParserUtil() {}

  /**
   * Reads and validates the first line of a WebVTT file.
   *
   * @param input The input from which the line should be read.
   * @throws ParserException If the line isn't the start of a valid WebVTT file.
   * @throws IOException If an error occurs reading from the input.
   */
  public static void validateWebvttHeaderLine(BufferedReader input) throws IOException {
    String line = input.readLine();
    if (line == null || !HEADER.matcher(line).matches()) {
      throw new ParserException("Expected WEBVTT. Got " + line);
    }
  }

  /**
   * Reads lines up to and including the next WebVTT cue header.
   *
   * @param input The input from which lines should be read.
   * @throws IOException If an error occurs reading from the input.
   * @return A {@link Matcher} for the WebVTT cue header, or null if the end of the input was
   *     reached without a cue header being found. In the case that a cue header is found, groups 1,
   *     2 and 3 of the returned matcher contain the start time, end time and settings list.
   */
  public static Matcher findNextCueHeader(BufferedReader input) throws IOException {
    String line;
    while ((line = input.readLine()) != null) {
      if (COMMENT.matcher(line).matches()) {
        // Skip until the end of the comment block.
        while ((line = input.readLine()) != null && !line.isEmpty()) {}
      } else {
        Matcher cueHeaderMatcher = CUE_HEADER.matcher(line);
        if (cueHeaderMatcher.matches()) {
          return cueHeaderMatcher;
        }
      }
    }
    return null;
  }

  /**
   * Parses a WebVTT timestamp.
   *
   * @param timestamp The timestamp string.
   * @return The parsed timestamp in microseconds.
   * @throws NumberFormatException If the timestamp could not be parsed.
   */
  public static long parseTimestampUs(String timestamp) throws NumberFormatException {
    long value = 0;
    String[] parts = timestamp.split("\\.", 2);
    String[] subparts = parts[0].split(":");
    for (int i = 0; i < subparts.length; i++) {
      value = value * 60 + Long.parseLong(subparts[i]);
    }
    return (value * 1000 + Long.parseLong(parts[1])) * 1000;
  }

}
