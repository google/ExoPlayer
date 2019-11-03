/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.exoplayer2.text.ssa;

import android.graphics.PointF;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.text.Subtitle;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.ParsableByteArray;
import com.google.android.exoplayer2.util.Util;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link SimpleSubtitleDecoder} for SSA/ASS.
 */
public final class SsaDecoder extends SimpleSubtitleDecoder {

  private static final String TAG = "SsaDecoder";

  private static final Pattern SSA_TIMECODE_PATTERN = Pattern.compile(
      "(?:(\\d+):)?(\\d+):(\\d+)(?::|\\.)(\\d+)");
  private static final Pattern SSA_POSITION_PATTERN = Pattern.compile(
      "\\\\pos\\((\\d+(\\.\\d+)?),\\s*(\\d+(\\.\\d+)?)");

  private static final String FORMAT_LINE_PREFIX = "Format: ";
  private static final String DIALOGUE_LINE_PREFIX = "Dialogue: ";

  private final boolean haveInitializationData;

  private int formatKeyCount;
  private int formatStartIndex;
  private int formatEndIndex;
  private int formatTextIndex;

  private int playResX = C.LENGTH_UNSET;
  private int playResY = C.LENGTH_UNSET;

  public SsaDecoder() {
    this(/* initializationData= */ null);
  }

  /**
   * @param initializationData Optional initialization data for the decoder. If not null or empty,
   *     the initialization data must consist of two byte arrays. The first must contain an SSA
   *     format line. The second must contain an SSA header that will be assumed common to all
   *     samples.
   */
  public SsaDecoder(@Nullable List<byte[]> initializationData) {
    super("SsaDecoder");
    if (initializationData != null && !initializationData.isEmpty()) {
      haveInitializationData = true;
      String formatLine = Util.fromUtf8Bytes(initializationData.get(0));
      Assertions.checkArgument(formatLine.startsWith(FORMAT_LINE_PREFIX));
      parseFormatLine(formatLine);
      parseHeader(new ParsableByteArray(initializationData.get(1)));
    } else {
      haveInitializationData = false;
    }
  }

  @Override
  protected Subtitle decode(byte[] bytes, int length, boolean reset) {
    ArrayList<List<Cue>> cues = new ArrayList<>();
    List<Long> cueTimesUs = new ArrayList<>();

    ParsableByteArray data = new ParsableByteArray(bytes, length);
    if (!haveInitializationData) {
      parseHeader(data);
    }
    parseEventBody(data, cues, cueTimesUs);
    return new SsaSubtitle(cues, cueTimesUs);
  }

  /**
   * Parses the header of the subtitle.
   *
   * @param data A {@link ParsableByteArray} from which the header should be read.
   */
  private void parseHeader(ParsableByteArray data) {
    String currentLine;
    while ((currentLine = data.readLine()) != null) {
      if (currentLine.startsWith("PlayResX:")) {
        playResX = Integer.valueOf(currentLine.substring("PlayResX:".length()).trim());
      }
      if (currentLine.startsWith("PlayResY:")) {
        playResY = Integer.valueOf(currentLine.substring("PlayResY:".length()).trim());
      }
      // TODO: Parse useful data from the header.
      if (currentLine.startsWith("[Events]")) {
        // We've reached the event body.
        return;
      }
    }
  }

  /**
   * Parses the event body of the subtitle.
   *
   * @param data A {@link ParsableByteArray} from which the body should be read.
   * @param cues A list to which parsed cues will be added.
   * @param cueTimesUs An array to which parsed cue timestamps will be added.
   */
  private void parseEventBody(ParsableByteArray data, List<List<Cue>> cues, List<Long> cueTimesUs) {
    String currentLine;
    while ((currentLine = data.readLine()) != null) {
      if (!haveInitializationData && currentLine.startsWith(FORMAT_LINE_PREFIX)) {
        parseFormatLine(currentLine);
      } else if (currentLine.startsWith(DIALOGUE_LINE_PREFIX)) {
        parseDialogueLine(currentLine, cues, cueTimesUs);
      }
    }
  }

  /**
   * Parses a format line.
   *
   * @param formatLine The line to parse.
   */
  private void parseFormatLine(String formatLine) {
    String[] values = TextUtils.split(formatLine.substring(FORMAT_LINE_PREFIX.length()), ",");
    formatKeyCount = values.length;
    formatStartIndex = C.INDEX_UNSET;
    formatEndIndex = C.INDEX_UNSET;
    formatTextIndex = C.INDEX_UNSET;
    for (int i = 0; i < formatKeyCount; i++) {
      String key = Util.toLowerInvariant(values[i].trim());
      switch (key) {
        case "start":
          formatStartIndex = i;
          break;
        case "end":
          formatEndIndex = i;
          break;
        case "text":
          formatTextIndex = i;
          break;
        default:
          // Do nothing.
          break;
      }
    }
    if (formatStartIndex == C.INDEX_UNSET
        || formatEndIndex == C.INDEX_UNSET
        || formatTextIndex == C.INDEX_UNSET) {
      // Set to 0 so that parseDialogueLine skips lines until a complete format line is found.
      formatKeyCount = 0;
    }
  }

  /**
   * Parses a dialogue line.
   *
   * @param dialogueLine The line to parse.
   * @param cues A list to which parsed cues will be added.
   * @param cueTimesUs An array to which parsed cue timestamps will be added.
   */
  private void parseDialogueLine(String dialogueLine, List<List<Cue>> cues, List<Long> cueTimesUs) {
    if (formatKeyCount == 0) {
      Log.w(TAG, "Skipping dialogue line before complete format: " + dialogueLine);
      return;
    }

    String[] lineValues = dialogueLine.substring(DIALOGUE_LINE_PREFIX.length())
        .split(",", formatKeyCount);
    if (lineValues.length != formatKeyCount) {
      Log.w(TAG, "Skipping dialogue line with fewer columns than format: " + dialogueLine);
      return;
    }

    long startTimeUs = SsaDecoder.parseTimecodeUs(lineValues[formatStartIndex]);
    if (startTimeUs == C.TIME_UNSET) {
      Log.w(TAG, "Skipping invalid timing: " + dialogueLine);
      return;
    }

    long endTimeUs = C.TIME_UNSET;
    String endTimeString = lineValues[formatEndIndex];
    if (!endTimeString.trim().isEmpty()) {
      endTimeUs = SsaDecoder.parseTimecodeUs(endTimeString);
      if (endTimeUs == C.TIME_UNSET) {
        Log.w(TAG, "Skipping invalid timing: " + dialogueLine);
        return;
      }
    }

    PointF position = parsePosition(lineValues[formatTextIndex]);

    String text = lineValues[formatTextIndex]
        .replaceAll("\\{.*?\\}", "")
        .replaceAll("\\\\N", "\n")
        .replaceAll("\\\\n", "\n");

    Cue cue;
    if (position != null && playResX != C.LENGTH_UNSET && playResY != C.LENGTH_UNSET) {
      cue = new Cue(
          text,
          /* textAlignment */ null,
          position.y / playResY,
          Cue.LINE_TYPE_FRACTION,
          Cue.ANCHOR_TYPE_START,
          position.x / playResX,
          Cue.ANCHOR_TYPE_MIDDLE,
          Cue.DIMEN_UNSET);
    } else {
      cue = new Cue(text);
    }

    int startTimeIndex = 0;
    boolean startTimeFound = false;
    // Search the insertion index for startTimeUs in cueTimesUs
    for (int i = cueTimesUs.size() - 1; i >= 0; i--) {
      if (cueTimesUs.get(i) == startTimeUs) {
        startTimeIndex = i;
        startTimeFound = true;
        break;
      }

      if (cueTimesUs.get(i) < startTimeUs) {
        startTimeIndex = i + 1;
        break;
      }
    }

    if (startTimeIndex == 0) {
      // Handle first cue
      cueTimesUs.add(startTimeIndex, startTimeUs);
      cues.add(startTimeIndex, new ArrayList<>());
    } else {
      if (!startTimeFound) {
        // Add the startTimeUs only if it wasn't found in cueTimesUs
        cueTimesUs.add(startTimeIndex, startTimeUs);
        // Copy over cues from left
        List<Cue> startCueList = new ArrayList<>(cues.get(startTimeIndex - 1));
        cues.add(startTimeIndex, startCueList);
      }
    }

    int endTimeIndex = 0;
    if (endTimeUs != C.TIME_UNSET) {
      boolean endTimeFound = false;

      // Search the insertion index for endTimeUs in cueTimesUs
      for (int i = cueTimesUs.size() - 1; i >= 0; i--) {
        if (cueTimesUs.get(i) == endTimeUs) {
          endTimeIndex = i;
          endTimeFound = true;
          break;
        }

        if (cueTimesUs.get(i) < endTimeUs) {
          endTimeIndex = i + 1;
          break;
        }
      }

      if (!endTimeFound) {
        // Add the endTimeUs only if it wasn't found in cueTimesUs
        cueTimesUs.add(endTimeIndex, endTimeUs);
        // Copy over cues from left
        cues.add(endTimeIndex, new ArrayList<>(cues.get(endTimeIndex - 1)));
      }
    }

    // Iterate on cues from startTimeIndex until endTimeIndex, add the current cue
    int i = startTimeIndex;
    do {
      cues.get(i).add(cue);
      i++;
    } while (i < endTimeIndex);
  }

  /**
   * Parses an SSA timecode string.
   *
   * @param timeString The string to parse.
   * @return The parsed timestamp in microseconds.
   */
  private static long parseTimecodeUs(String timeString) {
    Matcher matcher = SSA_TIMECODE_PATTERN.matcher(timeString);
    if (!matcher.matches()) {
      return C.TIME_UNSET;
    }
    long timestampUs = Long.parseLong(matcher.group(1)) * 60 * 60 * C.MICROS_PER_SECOND;
    timestampUs += Long.parseLong(matcher.group(2)) * 60 * C.MICROS_PER_SECOND;
    timestampUs += Long.parseLong(matcher.group(3)) * C.MICROS_PER_SECOND;
    timestampUs += Long.parseLong(matcher.group(4)) * 10000; // 100ths of a second.
    return timestampUs;
  }

  /**
   * Parses the position of an SSA dialogue line.
   * The attribute is expected to be in this form: "\pos{x,y}".
   *
   * @param line The string to parse.
   * @return The parsed position.
   */
  @Nullable
  private static PointF parsePosition(String line) {
    Matcher matcher = SSA_POSITION_PATTERN.matcher(line);
    if (!matcher.find()) {
      return null;
    }
    float x = Float.parseFloat(matcher.group(1));
    float y = Float.parseFloat(matcher.group(3));
    return new PointF(x, y);
  }

}
