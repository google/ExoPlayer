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
import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.util.MimeTypes;

import android.text.Html;
import android.text.Layout.Alignment;
import android.util.Log;

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
 */
public final class WebvttParser implements SubtitleParser {

  private static final String TAG = "WebvttParser";

  private static final Pattern HEADER = Pattern.compile("^\uFEFF?WEBVTT((\u0020|\u0009).*)?$");
  private static final Pattern COMMENT_BLOCK = Pattern.compile("^NOTE((\u0020|\u0009).*)?$");
  private static final Pattern CUE_HEADER = Pattern.compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$");
  private static final Pattern CUE_SETTING = Pattern.compile("\\S+?:\\S+");

  private final PositionHolder positionHolder;
  private final StringBuilder textBuilder;

  public WebvttParser() {
    positionHolder = new PositionHolder();
    textBuilder = new StringBuilder();
  }

  @Override
  public final boolean canParse(String mimeType) {
    return MimeTypes.TEXT_VTT.equals(mimeType);
  }

  @Override
  public final WebvttSubtitle parse(InputStream inputStream) throws IOException {
    ArrayList<WebvttCue> subtitles = new ArrayList<>();

    BufferedReader webvttData = new BufferedReader(new InputStreamReader(inputStream, C.UTF8_NAME));
    String line;

    // File should start with "WEBVTT".
    line = webvttData.readLine();
    if (line == null || !HEADER.matcher(line).matches()) {
      throw new ParserException("Expected WEBVTT. Got " + line);
    }

    // Parse the remainder of the header.
    while (true) {
      line = webvttData.readLine();
      if (line == null) {
        // We reached EOF before finishing the header.
        throw new ParserException("Expected an empty line after webvtt header");
      } else if (line.isEmpty()) {
        // We read the newline that separates the header from the body.
        break;
      }
    }

    // Process the cues and text.
    while ((line = webvttData.readLine()) != null) {
      // Skip a comment block, if present.
      Matcher matcher = COMMENT_BLOCK.matcher(line);
      if (matcher.find()) {
        // Skip until the end of the comment block.
        while ((line = webvttData.readLine()) != null && !line.isEmpty()) {
          // Ignore comment text.
        }
        continue;
      }

      // Skip anything other than a cue header.
      matcher = CUE_HEADER.matcher(line);
      if (!matcher.matches()) {
        continue;
      }

      // Parse the cue start and end times.
      long cueStartTime = parseTimestampUs(matcher.group(1));
      long cueEndTime = parseTimestampUs(matcher.group(2));

      // Default cue settings.
      Alignment cueTextAlignment = null;
      float cueLine = Cue.DIMEN_UNSET;
      int cueLineType = Cue.TYPE_UNSET;
      int cueLineAnchor = Cue.TYPE_UNSET;
      float cuePosition = Cue.DIMEN_UNSET;
      int cuePositionAnchor = Cue.TYPE_UNSET;
      float cueWidth = Cue.DIMEN_UNSET;

      // Parse the cue settings list.
      matcher = CUE_SETTING.matcher(matcher.group(3));
      while (matcher.find()) {
        String match = matcher.group();
        String[] parts = match.split(":", 2);
        String name = parts[0];
        String value = parts[1];
        try {
          if ("line".equals(name)) {
            parseLineAttribute(value, positionHolder);
            cueLine = positionHolder.position;
            cueLineType = positionHolder.lineType;
            cueLineAnchor = positionHolder.positionAnchor;
          } else if ("align".equals(name)) {
            cueTextAlignment = parseTextAlignment(value);
          } else if ("position".equals(name)) {
            parsePositionAttribute(value, positionHolder);
            cuePosition = positionHolder.position;
            cuePositionAnchor = positionHolder.positionAnchor;
          } else if ("size".equals(name)) {
            cueWidth = parsePercentage(value);
          } else {
            Log.w(TAG, "Unknown cue setting " + name + ":" + value);
          }
        } catch (NumberFormatException e) {
          Log.w(TAG, e.getMessage() + ": " + match);
        }
      }

      if (cuePosition != Cue.DIMEN_UNSET && cuePositionAnchor == Cue.TYPE_UNSET) {
        // Computed position alignment should be derived from the text alignment if it has not been
        // set explicitly.
        cuePositionAnchor = alignmentToAnchor(cueTextAlignment);
      }

      // Parse the cue text.
      textBuilder.setLength(0);
      while ((line = webvttData.readLine()) != null && !line.isEmpty()) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(line.trim());
      }
      CharSequence cueText = Html.fromHtml(textBuilder.toString());

      WebvttCue cue = new WebvttCue(cueStartTime, cueEndTime, cueText, cueTextAlignment, cueLine,
          cueLineType, cueLineAnchor, cuePosition, cuePositionAnchor, cueWidth);
      subtitles.add(cue);
    }

    return new WebvttSubtitle(subtitles);
  }

  private static long parseTimestampUs(String s) throws NumberFormatException {
    long value = 0;
    String[] parts = s.split("\\.", 2);
    String[] subparts = parts[0].split(":");
    for (int i = 0; i < subparts.length; i++) {
      value = value * 60 + Long.parseLong(subparts[i]);
    }
    return (value * 1000 + Long.parseLong(parts[1])) * 1000;
  }

  private static void parseLineAttribute(String s, PositionHolder out)
      throws NumberFormatException {
    int lineAnchor;
    int commaPosition = s.indexOf(",");
    if (commaPosition != -1) {
      lineAnchor = parsePositionAnchor(s.substring(commaPosition + 1));
      s = s.substring(0, commaPosition);
    } else {
      lineAnchor = Cue.TYPE_UNSET;
    }
    float line;
    int lineType;
    if (s.endsWith("%")) {
      line = parsePercentage(s);
      lineType = Cue.LINE_TYPE_FRACTION;
    } else {
      line = Integer.parseInt(s);
      lineType = Cue.LINE_TYPE_NUMBER;
    }
    out.position = line;
    out.positionAnchor = lineAnchor;
    out.lineType = lineType;
  }

  private static void parsePositionAttribute(String s, PositionHolder out)
      throws NumberFormatException {
    int positionAnchor;
    int commaPosition = s.indexOf(",");
    if (commaPosition != -1) {
      positionAnchor = parsePositionAnchor(s.substring(commaPosition + 1));
      s = s.substring(0, commaPosition);
    } else {
      positionAnchor = Cue.TYPE_UNSET;
    }
    out.position = parsePercentage(s);
    out.positionAnchor = positionAnchor;
    out.lineType = Cue.TYPE_UNSET;
  }

  private static float parsePercentage(String s) throws NumberFormatException {
    if (!s.endsWith("%")) {
      throw new NumberFormatException("Percentages must end with %");
    }
    s = s.substring(0, s.length() - 1);
    return Float.parseFloat(s) / 100;
  }

  private static int parsePositionAnchor(String s) {
    switch (s) {
      case "start":
        return Cue.ANCHOR_TYPE_START;
      case "middle":
        return Cue.ANCHOR_TYPE_MIDDLE;
      case "end":
        return Cue.ANCHOR_TYPE_END;
      default:
        Log.w(TAG, "Invalid anchor value: " + s);
        return Cue.TYPE_UNSET;
    }
  }

  private static Alignment parseTextAlignment(String s) {
    switch (s) {
      case "start":
      case "left":
        return Alignment.ALIGN_NORMAL;
      case "middle":
        return Alignment.ALIGN_CENTER;
      case "end":
      case "right":
        return Alignment.ALIGN_OPPOSITE;
      default:
        Log.w(TAG, "Invalid alignment value: " + s);
        return null;
    }
  }

  private static int alignmentToAnchor(Alignment alignment) {
    if (alignment == null) {
      return Cue.TYPE_UNSET;
    }
    switch (alignment) {
      case ALIGN_NORMAL:
        return Cue.ANCHOR_TYPE_START;
      case ALIGN_CENTER:
        return Cue.ANCHOR_TYPE_MIDDLE;
      case ALIGN_OPPOSITE:
        return Cue.ANCHOR_TYPE_END;
      default:
        Log.w(TAG, "Unrecognized alignment: " + alignment);
        return Cue.ANCHOR_TYPE_START;
    }
  }

  private static final class PositionHolder {

    public float position;
    public int positionAnchor;
    public int lineType;

  }

}
