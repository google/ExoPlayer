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

import android.text.TextUtils;
import android.util.Pair;
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

  private int playResX;
  private int playResY;

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
        playResX = Integer.valueOf(currentLine.substring(9).trim());
      }
      if (currentLine.startsWith("PlayResY:")) {
        playResY = Integer.valueOf(currentLine.substring(9).trim());
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

    // Parse \pos{x,y} attribute
    Pair<Float, Float> position = parsePosition(lineValues[formatTextIndex]);

    String text = lineValues[formatTextIndex]
        .replaceAll("\\{.*?\\}", "")
        .replaceAll("\\\\N", "\n")
        .replaceAll("\\\\n", "\n");

    Cue cue;
    if (position != null && playResX != 0 && playResY != 0) {
      cue = new Cue(
          text,
          /* textAlignment */ null,
          position.second / playResY,
          Cue.LINE_TYPE_FRACTION,
          Cue.ANCHOR_TYPE_START,
          position.first / playResX,
          Cue.ANCHOR_TYPE_MIDDLE,
          Cue.DIMEN_UNSET);
    } else {
      cue = new Cue(text);
    }

    int startTimeIndex = insertToCueTimes(cueTimesUs, startTimeUs);

    List<Cue> startCueList = new ArrayList<>();
    if (startTimeIndex != 0) {
      startCueList.addAll(cues.get(startTimeIndex - 1));
    }
    cues.add(startTimeIndex, startCueList);

    if (endTimeUs != C.TIME_UNSET) {
      int endTimeIndex = insertToCueTimes(cueTimesUs, endTimeUs);
      List<Cue> endList = new ArrayList<>(cues.get(endTimeIndex - 1));
      cues.add(endTimeIndex, endList);

      int i = startTimeIndex;
      do {
        cues.get(i).add(cue);
        i++;
      } while (i != endTimeIndex);
    }
  }

  /**
   * Insert the given cue time into the given array keeping the array sorted.
   *
   * @param cueTimes The array with sorted cue times
   * @param timeUs The cue time to be inserted
   * @return The index where the cue time was inserted
   */
  private static int insertToCueTimes(List<Long> cueTimes, long timeUs) {
    for (int i = cueTimes.size() - 1; i >= 0; i--) {
      if (cueTimes.get(i) <= timeUs) {
        cueTimes.add(i + 1, timeUs);
        return i + 1;
      }
    }

    cueTimes.add(0, timeUs);
    return 0;
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

  @Nullable
  public static Pair<Float, Float> parsePosition(String line){
    Matcher matcher = SSA_POSITION_PATTERN.matcher(line);
    if(!matcher.find()){
      return null;
    }
    float x = Float.parseFloat(matcher.group(1));
    float y = Float.parseFloat(matcher.group(3));
    return new Pair<>(x,y);
  }

}
