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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ParserException;
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
 * A simple WebVTT parser.
 * <p>
 * @see <a href="http://dev.w3.org/html5/webvtt">WebVTT specification</a>
 * <p>
 */
public class WebvttParser implements SubtitleParser {

  /**
   * This parser allows a custom header to be prepended to the WebVTT data, in the form of a text
   * line starting with this string.
   *
   * @hide
   */
  public static final String EXO_HEADER = "EXO-HEADER";
  /**
   * A {@code OFFSET + value} element can be added to the custom header to specify an offset time
   * (in microseconds) that should be subtracted from the embedded MPEGTS value.
   *
   * @hide
   */
  public static final String OFFSET = "OFFSET:";

  private static final long SAMPLING_RATE = 90;

  private static final String WEBVTT_METADATA_HEADER_STRING = "\\S*[:=]\\S*";
  private static final Pattern WEBVTT_METADATA_HEADER =
      Pattern.compile(WEBVTT_METADATA_HEADER_STRING);

  private static final String WEBVTT_TIMESTAMP_STRING = "(\\d+:)?[0-5]\\d:[0-5]\\d\\.\\d{3}";
  private static final Pattern WEBVTT_TIMESTAMP = Pattern.compile(WEBVTT_TIMESTAMP_STRING);

  private static final Pattern MEDIA_TIMESTAMP_OFFSET = Pattern.compile(OFFSET + "\\d+");
  private static final Pattern MEDIA_TIMESTAMP = Pattern.compile("MPEGTS:\\d+");

  private final boolean strictParsing;

  public WebvttParser() {
    this(true);
  }

  public WebvttParser(boolean strictParsing) {
    this.strictParsing = strictParsing;
  }

  @Override
  public WebvttSubtitle parse(InputStream inputStream, String inputEncoding, long startTimeUs)
      throws IOException {
    ArrayList<WebvttCue> subtitles = new ArrayList<WebvttCue>();
    long mediaTimestampUs = startTimeUs;
    long mediaTimestampOffsetUs = 0;

    BufferedReader webvttData = new BufferedReader(new InputStreamReader(inputStream, C.UTF8_NAME));
    String line;

    // file should start with "WEBVTT" on the first line or "EXO-HEADER"
    line = webvttData.readLine();
    if (line == null) {
      throw new ParserException("Expected WEBVTT or EXO-HEADER. Got null");
    }
    if (line.startsWith(EXO_HEADER)) {
      // parse the timestamp offset, if present
      Matcher matcher = MEDIA_TIMESTAMP_OFFSET.matcher(line);
      if (matcher.find()) {
        mediaTimestampOffsetUs = Long.parseLong(matcher.group().substring(7));
      }

      // read the next line, which should now be WEBVTT
      line = webvttData.readLine();
      if (line == null) {
        throw new ParserException("Expected WEBVTT. Got null");
      }
    }
    if (!line.equals("WEBVTT")) {
      throw new ParserException("Expected WEBVTT. Got " + line);
    }

    // parse the remainder of the header
    while (true) {
      line = webvttData.readLine();
      if (line == null) {
        // we reached EOF before finishing the header
        throw new ParserException("Expected an empty line after webvtt header");
      } else if (line.isEmpty()) {
        // we've read the newline that separates the header from the body
        break;
      }

      Matcher matcher = WEBVTT_METADATA_HEADER.matcher(line);
      if (!matcher.find()) {
        handleNoncompliantLine(line);
      }

      if (line.startsWith("X-TIMESTAMP-MAP")) {
        // parse the media timestamp
        Matcher timestampMatcher = MEDIA_TIMESTAMP.matcher(line);
        if (!timestampMatcher.find()) {
          throw new ParserException("X-TIMESTAMP-MAP doesn't contain media timestamp: " + line);
        } else {
          mediaTimestampUs = (Long.parseLong(timestampMatcher.group().substring(7)) * 1000)
              / SAMPLING_RATE - mediaTimestampOffsetUs;
        }
        mediaTimestampUs = getAdjustedStartTime(mediaTimestampUs);
      }
    }

    // process the cues and text
    while ((line = webvttData.readLine()) != null) {
      // parse the cue timestamps
      Matcher matcher = WEBVTT_TIMESTAMP.matcher(line);
      long startTime;
      long endTime;
      String text = "";

      // parse start timestamp
      if (!matcher.find()) {
        throw new ParserException("Expected cue start time: " + line);
      } else {
        startTime = parseTimestampUs(matcher.group()) + mediaTimestampUs;
      }

      // parse end timestamp
      if (!matcher.find()) {
        throw new ParserException("Expected cue end time: " + line);
      } else {
        endTime = parseTimestampUs(matcher.group()) + mediaTimestampUs;
      }

      // parse text
      while (((line = webvttData.readLine()) != null) && (!line.isEmpty())) {
        text += line.trim() + "\n";
      }

      WebvttCue cue = new WebvttCue(startTime, endTime, text);
      subtitles.add(cue);
    }

    webvttData.close();
    inputStream.close();

    // copy WebvttCue data into arrays for WebvttSubtitle constructor
    String[] cueText = new String[subtitles.size()];
    long[] cueTimesUs = new long[2 * subtitles.size()];
    for (int subtitleIndex = 0; subtitleIndex < subtitles.size(); subtitleIndex++) {
      int arrayIndex = subtitleIndex * 2;
      WebvttCue cue = subtitles.get(subtitleIndex);
      cueTimesUs[arrayIndex] = cue.startTime;
      cueTimesUs[arrayIndex + 1] = cue.endTime;
      cueText[subtitleIndex] = cue.text;
    }

    WebvttSubtitle subtitle = new WebvttSubtitle(cueText, mediaTimestampUs, cueTimesUs);
    return subtitle;
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.TEXT_VTT.equals(mimeType);
  }

  protected long getAdjustedStartTime(long startTimeUs) {
    return startTimeUs;
  }

  protected void handleNoncompliantLine(String line) throws ParserException {
    if (strictParsing) {
      throw new ParserException("Unexpected line: " + line);
    }
  }

  private static long parseTimestampUs(String s) throws NumberFormatException {
    if (!s.matches(WEBVTT_TIMESTAMP_STRING)) {
      throw new NumberFormatException("has invalid format");
    }

    String[] parts = s.split("\\.", 2);
    long value = 0;
    for (String group : parts[0].split(":")) {
      value = value * 60 + Long.parseLong(group);
    }
    return (value * 1000 + Long.parseLong(parts[1])) * 1000;
  }

  private static class WebvttCue {
    public final long startTime;
    public final long endTime;
    public final String text;

    public WebvttCue(long startTime, long endTime, String text) {
      this.startTime = startTime;
      this.endTime = endTime;
      this.text = text;
    }
  }

}
