/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.text.subrip;

import android.support.annotation.StringDef;
import android.text.Html;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextUtils;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.LongArray;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A {@link SimpleSubtitleDecoder} for SubRip.
 */
public final class SubripDecoder extends SimpleSubtitleDecoder {

  private static final String TAG = "SubripDecoder";

  private static final String SUBRIP_TIMECODE = "(?:(\\d+):)?(\\d+):(\\d+),(\\d+)";
  private static final Pattern SUBRIP_TIMING_LINE =
      Pattern.compile("\\s*(" + SUBRIP_TIMECODE + ")\\s*-->\\s*(" + SUBRIP_TIMECODE + ")?\\s*");

  private static final Pattern SUBRIP_TAG_PATTERN = Pattern.compile("\\{\\\\.*?\\}");
  private static final String SUBRIP_ALIGNMENT_TAG = "\\{\\\\an[1-9]\\}";

  private static final float DEFAULT_START_FRACTION = 0.08f;
  private static final float DEFAULT_END_FRACTION = 1 - DEFAULT_START_FRACTION;
  private static final float DEFAULT_MID_FRACTION = 0.5f;

  @Retention(RetentionPolicy.SOURCE)
  @StringDef({
      ALIGN_BOTTOM_LEFT, ALIGN_BOTTOM_MID, ALIGN_BOTTOM_RIGHT,
      ALIGN_MID_LEFT, ALIGN_MID_MID, ALIGN_MID_RIGHT,
      ALIGN_TOP_LEFT, ALIGN_TOP_MID, ALIGN_TOP_RIGHT
  })

  private @interface SubRipTag {}

  // Possible valid alignment tags based on SSA v4+ specs
  private static final String ALIGN_BOTTOM_LEFT  = "{\\an1}";
  private static final String ALIGN_BOTTOM_MID   = "{\\an2}";
  private static final String ALIGN_BOTTOM_RIGHT = "{\\an3}";
  private static final String ALIGN_MID_LEFT     = "{\\an4}";
  private static final String ALIGN_MID_MID      = "{\\an5}";
  private static final String ALIGN_MID_RIGHT    = "{\\an6}";
  private static final String ALIGN_TOP_LEFT     = "{\\an7}";
  private static final String ALIGN_TOP_MID      = "{\\an8}";
  private static final String ALIGN_TOP_RIGHT    = "{\\an9}";

  private final StringBuilder textBuilder;

  public SubripDecoder() {
    super("SubripDecoder");
    textBuilder = new StringBuilder();
  }

  @Override
  protected SubripSubtitle decode(byte[] bytes, int length, boolean reset) {
    ArrayList<Cue> cues = new ArrayList<>();
    LongArray cueTimesUs = new LongArray();
    ParsableByteArray subripData = new ParsableByteArray(bytes, length);
    String currentLine;

    while ((currentLine = subripData.readLine()) != null) {
      if (currentLine.length() == 0) {
        // Skip blank lines.
        continue;
      }

      // Parse the index line as a sanity check.
      try {
        Integer.parseInt(currentLine);
      } catch (NumberFormatException e) {
        Log.w(TAG, "Skipping invalid index: " + currentLine);
        continue;
      }

      // Read and parse the timing line.
      boolean haveEndTimecode = false;
      currentLine = subripData.readLine();
      if (currentLine == null) {
        Log.w(TAG, "Unexpected end");
        break;
      }

      Matcher matcher = SUBRIP_TIMING_LINE.matcher(currentLine);
      if (matcher.matches()) {
        cueTimesUs.add(parseTimecode(matcher, 1));
        if (!TextUtils.isEmpty(matcher.group(6))) {
          haveEndTimecode = true;
          cueTimesUs.add(parseTimecode(matcher, 6));
        }
      } else {
        Log.w(TAG, "Skipping invalid timing: " + currentLine);
        continue;
      }

      // Read and parse the text.
      ArrayList<String> tags = new ArrayList<>();
      textBuilder.setLength(0);
      while (!TextUtils.isEmpty(currentLine = subripData.readLine())) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(processLine(currentLine, tags));
      }

      Spanned text = Html.fromHtml(textBuilder.toString());
      Cue cue = null;

      // At end of this loop the clue must be created with the applied tags
      for (String tag : tags) {

        // Check if the tag is an alignment tag
        if (tag.matches(SUBRIP_ALIGNMENT_TAG)) {
          cue = buildCue(text, tag);

          // Based on the specs, in case of alignment tags only the first appearance counts, so break
          break;
        }
      }

      cues.add(cue == null ? new Cue(text) : cue);

      if (haveEndTimecode) {
        cues.add(null);
      }
    }

    Cue[] cuesArray = new Cue[cues.size()];
    cues.toArray(cuesArray);
    long[] cueTimesUsArray = cueTimesUs.toArray();
    return new SubripSubtitle(cuesArray, cueTimesUsArray);
  }

  /**
   * Process the given line by first trimming it then extracting the tags from it
   * <p>
   * The pattern that is used to extract the tags is specified in SSA v4+ specs and
   * has the following form: "{\...}".
   * <p>
   * "All override codes appear within braces {}"
   * "All override codes are always preceded by a backslash \"
   *
   * @param currentLine Current line
   * @param tags        Extracted tags will be stored in this array list
   * @return Processed line
   */
  private String processLine(String currentLine, ArrayList<String> tags) {
    // Trim line
    String trimmedLine = currentLine.trim();

    // Extract tags
    int replacedCharacters = 0;
    StringBuilder processedLine = new StringBuilder(trimmedLine);
    Matcher matcher = SUBRIP_TAG_PATTERN.matcher(trimmedLine);

    while (matcher.find()) {
      String tag = matcher.group();
      tags.add(tag);
      processedLine.replace(matcher.start() - replacedCharacters, matcher.end() - replacedCharacters, "");
      replacedCharacters += tag.length();
    }

    return processedLine.toString();
  }

  /**
   * Build a {@link Cue} based on the given text and tag
   * <p>
   * Match the alignment tag and calculate the line, position, position anchor accordingly
   * <p>
   * Based on SSA v4+ specs the alignment tag can have the following form: {\an[1-9},
   * where the number specifies the direction (based on the numpad layout).
   * Note. older SSA scripts may contain tags like {\a1[1-9]} but these are based on
   * other direction rules, but multiple sources says that these are deprecated, so no support here either
   *
   * @param alignmentTag Alignment tag
   * @return Built cue
   */
  private Cue buildCue(Spanned text, String alignmentTag) {
    float line, position;
    @Cue.AnchorType int positionAnchor;
    @Cue.AnchorType int lineAnchor;

    // Set position and position anchor (horizontal alignment)
    switch (alignmentTag) {
      case ALIGN_BOTTOM_LEFT:
      case ALIGN_MID_LEFT:
      case ALIGN_TOP_LEFT:
        position = DEFAULT_START_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_START;
        break;
      case ALIGN_BOTTOM_MID:
      case ALIGN_MID_MID:
      case ALIGN_TOP_MID:
        position = DEFAULT_MID_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        break;
      case ALIGN_BOTTOM_RIGHT:
      case ALIGN_MID_RIGHT:
      case ALIGN_TOP_RIGHT:
        position = DEFAULT_END_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_END;
        break;
      default:
        position = DEFAULT_MID_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        break;
    }

    // Set line and line anchor (vertical alignment)
    switch (alignmentTag) {
      case ALIGN_BOTTOM_LEFT:
      case ALIGN_BOTTOM_MID:
      case ALIGN_BOTTOM_RIGHT:
        line = DEFAULT_END_FRACTION;
        lineAnchor = Cue.ANCHOR_TYPE_END;
        break;
      case ALIGN_MID_LEFT:
      case ALIGN_MID_MID:
      case ALIGN_MID_RIGHT:
        line = DEFAULT_MID_FRACTION;
        lineAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        break;
      case ALIGN_TOP_LEFT:
      case ALIGN_TOP_MID:
      case ALIGN_TOP_RIGHT:
        line = DEFAULT_START_FRACTION;
        lineAnchor = Cue.ANCHOR_TYPE_START;
        break;
      default:
        line = DEFAULT_END_FRACTION;
        lineAnchor = Cue.ANCHOR_TYPE_END;
        break;
    }

    return new Cue(text, null, line, Cue.LINE_TYPE_FRACTION, lineAnchor, position, positionAnchor, Cue.DIMEN_UNSET);
  }

  private static long parseTimecode(Matcher matcher, int groupOffset) {
    long timestampMs = Long.parseLong(matcher.group(groupOffset + 1)) * 60 * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 2)) * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 3)) * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 4));
    return timestampMs * 1000;
  }
}
