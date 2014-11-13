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
package com.google.android.exoplayer.hls;

import com.google.android.exoplayer.ParserException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for HLS manifest parsing.
 */
/* package */ class HlsParserUtil {

  private HlsParserUtil() {}

  public static String parseStringAttr(String line, Pattern pattern, String tag)
      throws ParserException {
    Matcher matcher = pattern.matcher(line);
    if (matcher.find() && matcher.groupCount() == 1) {
      return matcher.group(1);
    }
    throw new ParserException(String.format("Couldn't match %s tag in %s", tag, line));
  }

  public static String parseOptionalStringAttr(String line, Pattern pattern) {
    Matcher matcher = pattern.matcher(line);
    if (matcher.find() && matcher.groupCount() == 1) {
      return matcher.group(1);
    }
    return null;
  }

  public static int parseIntAttr(String line, Pattern pattern, String tag)
      throws ParserException {
    return Integer.parseInt(parseStringAttr(line, pattern, tag));
  }

  public static double parseDoubleAttr(String line, Pattern pattern, String tag)
      throws ParserException {
    return Double.parseDouble(parseStringAttr(line, pattern, tag));
  }

}
