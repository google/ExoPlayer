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
import android.util.Log;
import com.google.android.exoplayer2.text.Cue;
import com.google.android.exoplayer2.text.SimpleSubtitleDecoder;
import com.google.android.exoplayer2.util.LongArray;
import com.google.android.exoplayer2.util.ParsableByteArray;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
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
      textBuilder.setLength(0);
      while (!TextUtils.isEmpty(currentLine = subripData.readLine())) {
        if (textBuilder.length() > 0) {
          textBuilder.append("<br>");
        }
        textBuilder.append(currentLine.trim());
      }

      // Extract tags
      SubtitleTagResult tagResult = extractTags(textBuilder);
      Spanned text = Html.fromHtml(tagResult.cue);

      Cue cue = null;

      // Check if tags are present
      if (tagResult.tags.length > 0) {

        boolean alignTagFound = false;

        // At end of this loop the clue must be created with the applied tags
        for (String tag : tagResult.tags) {

          // Check if the tag is an alignment tag
          if (tag.matches(SUBRIP_ALIGNMENT_TAG)) {

            // Based on the specs, in case of the alignment tags only the first appearance counts
            if (alignTagFound) continue;
            alignTagFound = true;

            AlignmentResult alignmentResult = getAlignmentValues(tag);
            cue = new Cue(text, Layout.Alignment.ALIGN_NORMAL, alignmentResult.line, Cue.LINE_TYPE_FRACTION,
                alignmentResult.lineAnchor, alignmentResult.position, alignmentResult.positionAnchor, Cue.DIMEN_UNSET);
          }
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
   * Extracts the tags from the given {@code cue}
   * The pattern that is used to extract the tags is specified in SSA v4+ specs and
   * has the following form: "{\...}".
   * <p>
   * "All override codes appear within braces {}"
   * "All override codes are always preceded by a backslash \"
   *
   * @param cue Cue text
   * @return {@link SubtitleTagResult} that holds new cue and also the extracted tags
   */
  private SubtitleTagResult extractTags(StringBuilder cue) {
    StringBuilder cueCopy = new StringBuilder(cue.toString());
    List<String> tags = new ArrayList<>();

    int replacedCharacters = 0;

    Matcher matcher = SUBRIP_TAG_PATTERN.matcher(cue.toString());
    while (matcher.find()) {
      String tag = matcher.group();
      tags.add(tag);
      cueCopy.replace(matcher.start() - replacedCharacters, matcher.end() - replacedCharacters, "");
      replacedCharacters += tag.length();
    }

    return new SubtitleTagResult(tags.toArray(new String[tags.size()]), cueCopy.toString());
  }

  /**
   * Match the alignment tag and calculate the line, position, position anchor accordingly
   *
   * Based on SSA v4+ specs the alignment tag can have the following form: {\an[1-9},
   * where the number specifies the direction (based on the numpad layout).
   * Note. older SSA scripts may contain tags like {\a1[1-9]} but these are based on
   * other direction rules, but multiple sources says that these are deprecated, so no support here either
   *
   * @param tag Alignment tag
   * @return {@link AlignmentResult} that holds the line, position, position anchor values
   */
  private AlignmentResult getAlignmentValues(String tag) {
    // Default values used for positioning the subtitle in case of align tags
    float line = DEFAULT_END_FRACTION, position = DEFAULT_MID_FRACTION;
    @Cue.AnchorType int positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
    @Cue.AnchorType int lineAnchor = Cue.ANCHOR_TYPE_END;

    switch (tag) {
      case ALIGN_BOTTOM_LEFT:
        line = DEFAULT_END_FRACTION;
        position = DEFAULT_START_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_START;
        lineAnchor = Cue.ANCHOR_TYPE_END;
        break;
      case ALIGN_BOTTOM_MID:
        line = DEFAULT_END_FRACTION;
        position = DEFAULT_MID_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        lineAnchor = Cue.ANCHOR_TYPE_END;
        break;
      case ALIGN_BOTTOM_RIGHT:
        line = DEFAULT_END_FRACTION;
        position = DEFAULT_END_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_END;
        lineAnchor = Cue.ANCHOR_TYPE_END;
        break;
      case ALIGN_MID_LEFT:
        line = DEFAULT_MID_FRACTION;
        position = DEFAULT_START_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_START;
        lineAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        break;
      case ALIGN_MID_MID:
        line = DEFAULT_MID_FRACTION;
        position = DEFAULT_MID_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        lineAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        break;
      case ALIGN_MID_RIGHT:
        line = DEFAULT_MID_FRACTION;
        position = DEFAULT_END_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_END;
        lineAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        break;
      case ALIGN_TOP_LEFT:
        line = DEFAULT_START_FRACTION;
        position = DEFAULT_START_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_START;
        lineAnchor = Cue.ANCHOR_TYPE_START;
        break;
      case ALIGN_TOP_MID:
        line = DEFAULT_START_FRACTION;
        position = DEFAULT_MID_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_MIDDLE;
        lineAnchor = Cue.ANCHOR_TYPE_START;
        break;
      case ALIGN_TOP_RIGHT:
        line = DEFAULT_START_FRACTION;
        position = DEFAULT_END_FRACTION;
        positionAnchor = Cue.ANCHOR_TYPE_END;
        lineAnchor = Cue.ANCHOR_TYPE_START;
        break;
    }

    return new AlignmentResult(positionAnchor, position, lineAnchor, line);
  }

  private static long parseTimecode(Matcher matcher, int groupOffset) {
    long timestampMs = Long.parseLong(matcher.group(groupOffset + 1)) * 60 * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 2)) * 60 * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 3)) * 1000;
    timestampMs += Long.parseLong(matcher.group(groupOffset + 4));
    return timestampMs * 1000;
  }

  /**
   * Class that holds the tags, new clue after the tag extraction
   */
  private static final class SubtitleTagResult {
    public final String[] tags;
    public final String cue;

    public SubtitleTagResult(String[] tags, String cue) {
      this.tags = tags;
      this.cue = cue;
    }
  }

  /**
   * Class that holds the parsed and mapped alignment values (such as line,
   * position and anchor type of line)
   */
  private static final class AlignmentResult {

    public @Cue.AnchorType int positionAnchor;
    public @Cue.AnchorType int lineAnchor;
    public float position, line;

    public AlignmentResult(@Cue.AnchorType int positionAnchor, float position, @Cue.AnchorType int lineAnchor, float line) {
      this.positionAnchor = positionAnchor;
      this.position = position;
      this.line = line;
      this.lineAnchor = lineAnchor;
    }
  }

}
