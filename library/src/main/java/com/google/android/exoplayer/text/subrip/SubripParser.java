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
package com.google.android.exoplayer.text.subrip;

import android.text.Html;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.util.MimeTypes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple SRT parser.
 * <p/>
 *
 * @see <a href="https://en.wikipedia.org/wiki/SubRip">Wikipedia on SRT</a>
 */
public final class SubripParser implements SubtitleParser {

  private static final String TAG = "SubRipParser";

  private static final String SUBRIP_POSITION_STRING = "^(\\d)$";
  private static final Pattern SUBRIP_POSITION = Pattern.compile(SUBRIP_POSITION_STRING);

  private static final String SUBRIP_CUE_IDENTIFIER_STRING = "^(.*)\\s-->\\s(.*)$";
  private static final Pattern SUBRIP_CUE_IDENTIFIER =
          Pattern.compile(SUBRIP_CUE_IDENTIFIER_STRING);

  private static final String SUBRIP_TIMESTAMP_STRING = "(\\d+:)?[0-5]\\d:[0-5]\\d:[0-5]\\d,\\d{3}";
  // private static final Pattern SUBRIP_TIMESTAMP = Pattern.compile(SUBRIP_TIMESTAMP_STRING);

  private final StringBuilder textBuilder;

  private final boolean strictParsing;

  public SubripParser() {
    this(true);
  }

  public SubripParser(boolean strictParsing) {
    this.strictParsing = strictParsing;

    textBuilder = new StringBuilder();
  }

  @Override
  public SubripSubtitle parse(InputStream inputStream, String inputEncoding, long startTimeUs)
          throws IOException {
    ArrayList<SubripCue> subtitles = new ArrayList<>();

    // file should not be empty
    if (inputStream.available() == 0) {
      throw new ParserException("File is empty?");
    }

    BufferedReader subripData = new BufferedReader(new InputStreamReader(inputStream, C.UTF8_NAME));
    String line;


    // process the cues and text
    while ((line = subripData.readLine()) != null) {
      long startTime = Cue.UNSET_VALUE;
      long endTime = Cue.UNSET_VALUE;
      CharSequence text = null;
      int position = Cue.UNSET_VALUE;

      Matcher matcher = SUBRIP_POSITION.matcher(line);
      if (matcher.matches()) {
        position = Integer.parseInt(matcher.group());
      }

      line = subripData.readLine();

      // parse cue time
      matcher = SUBRIP_CUE_IDENTIFIER.matcher(line);
      if (!matcher.find()) {
        throw new ParserException("Expected cue start time: " + line);
      } else {
        startTime = parseTimestampUs(matcher.group(1)) + startTimeUs;
        endTime = parseTimestampUs(matcher.group(2)) + startTimeUs;
      }

      // parse text
      textBuilder.setLength(0);
      while (((line = subripData.readLine()) != null) && (!line.isEmpty())) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(line.trim());
      }
      text = Html.fromHtml(textBuilder.toString());

      SubripCue cue = new SubripCue(startTime, endTime, position, text);
      subtitles.add(cue);
    }

    subripData.close();
    inputStream.close();
    SubripSubtitle subtitle = new SubripSubtitle(subtitles, startTimeUs);
    return subtitle;
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_SUBRIP.equals(mimeType);
  }

  private void handleNoncompliantLine(String line) throws ParserException {
    if (strictParsing) {
      throw new ParserException("Unexpected line: " + line);
    }
  }

  private static long parseTimestampUs(String s) throws NumberFormatException {
    if (!s.matches(SUBRIP_TIMESTAMP_STRING)) {
      throw new NumberFormatException("has invalid format");
    }

    String[] parts = s.split(",", 2);
    long value = 0;
    for (String group : parts[0].split(":")) {
      value = value * 60 + Long.parseLong(group);
    }
    return (value * 1000 + Long.parseLong(parts[1])) * 1000;
  }

}
