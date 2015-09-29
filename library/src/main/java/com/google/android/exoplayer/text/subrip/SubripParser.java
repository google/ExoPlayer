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

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.util.LongArray;
import com.google.android.exoplayer.util.MimeTypes;

import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple SubRip parser.
 */
public final class SubripParser implements SubtitleParser {

  private static final String TAG = "SubripParser";

  private static final Pattern SUBRIP_TIMING_LINE = Pattern.compile("(\\S*)\\s*-->\\s*(\\S*)");
  private static final Pattern SUBRIP_TIMESTAMP =
      Pattern.compile("(?:(\\d+):)?(\\d+):(\\d+),(\\d+)");

  private final StringBuilder textBuilder;
  private final boolean strictParsing;

  /**
   * Equivalent to {@code SubripParser(false)}.
   */
  public SubripParser() {
    this(false);
  }

  /**
   * @param strictParsing If true, {@link #parse(InputStream)} will throw a {@link ParserException}
   *     if the stream contains invalid data. If false, the parser will make a best effort to ignore
   *     minor errors in the stream. Note however that a {@link ParserException} will still be
   *     thrown when this is not possible.
   */
  public SubripParser(boolean strictParsing) {
    this.strictParsing = strictParsing;
    textBuilder = new StringBuilder();
  }

  @Override
  public SubripSubtitle parse(InputStream inputStream) throws IOException {
    ArrayList<Cue> cues = new ArrayList<>();
    LongArray cueTimesUs = new LongArray();
    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, C.UTF8_NAME));
    boolean haveEndTimecode;
    String currentLine;

    while ((currentLine = reader.readLine()) != null) {
      if (currentLine.length() == 0) {
        // Skip blank lines.
        continue;
      }

      // Parse the index line as a sanity check.
      try {
        Integer.parseInt(currentLine);
      } catch (NumberFormatException e) {
        if (!strictParsing) {
          Log.w(TAG, "Skipping invalid index: " + currentLine);
          continue;
        } else {
          throw new ParserException("Expected numeric counter: " + currentLine);
        }
      }

      // Read and parse the timing line.
      haveEndTimecode = false;
      currentLine = reader.readLine();
      Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
      if (matcher.find()) {
        cueTimesUs.add(parseTimecode(matcher.group(1)));
        String endTimecode = matcher.group(2);
        if (!TextUtils.isEmpty(endTimecode)) {
          haveEndTimecode = true;
          cueTimesUs.add(parseTimecode(matcher.group(2)));
        }
      } else if (!strictParsing) {
        Log.w(TAG, "Skipping invalid timing: " + currentLine);
        continue;
      } else {
        throw new ParserException("Expected timing line: " + currentLine);
      }

      // Read and parse the text.
      textBuilder.setLength(0);
      while (!TextUtils.isEmpty(currentLine = reader.readLine())) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(currentLine.trim());
      }

      Spanned text = Html.fromHtml(textBuilder.toString());
      cues.add(new Cue(text));
      if (haveEndTimecode) {
        cues.add(null);
      }
    }

    Cue[] cuesArray = new Cue[cues.size()];
    cues.toArray(cuesArray);
    long[] cueTimesUsArray = cueTimesUs.toArray();
    return new SubripSubtitle(cuesArray, cueTimesUsArray);
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_SUBRIP.equals(mimeType);
  }

  private static long parseTimecode(String s) throws NumberFormatException {
    Matcher matcher = SUBRIP_TIMESTAMP.matcher(s);
    if (!matcher.matches()) {
      throw new NumberFormatException("has invalid format");
    }
    long timestampMs = Long.parseLong(matcher.group(1)) * 60 * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(2)) * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(3)) * 1000;
    timestampMs += Long.parseLong(matcher.group(4));
    return timestampMs * 1000;
  }

}
