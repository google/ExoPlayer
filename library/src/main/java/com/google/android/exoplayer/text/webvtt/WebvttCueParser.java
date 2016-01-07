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

import com.google.android.exoplayer.text.Cue;
import com.google.android.exoplayer.util.ParsableByteArray;

import android.graphics.Typeface;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for webvtt cue text. (https://w3c.github.io/webvtt/#cue-text)
 */
public final class WebvttCueParser {

  public static final Pattern CUE_HEADER_PATTERN = Pattern
      .compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$");

  private static final Pattern COMMENT = Pattern.compile("^NOTE((\u0020|\u0009).*)?$");
  private static final Pattern CUE_SETTING_PATTERN = Pattern.compile("(\\S+?):(\\S+)");

  private static final char CHAR_LESS_THAN = '<';
  private static final char CHAR_GREATER_THAN = '>';
  private static final char CHAR_SLASH = '/';
  private static final char CHAR_AMPERSAND = '&';
  private static final char CHAR_SEMI_COLON = ';';
  private static final char CHAR_SPACE = ' ';
  private static final String SPACE = " ";

  private static final String ENTITY_LESS_THAN = "lt";
  private static final String ENTITY_GREATER_THAN = "gt";
  private static final String ENTITY_AMPERSAND = "amp";
  private static final String ENTITY_NON_BREAK_SPACE = "nbsp";

  private static final String TAG_BOLD = "b";
  private static final String TAG_ITALIC = "i";
  private static final String TAG_UNDERLINE = "u";
  private static final String TAG_CLASS = "c";
  private static final String TAG_VOICE = "v";
  private static final String TAG_LANG = "lang";

  private static final int STYLE_BOLD = Typeface.BOLD;
  private static final int STYLE_ITALIC = Typeface.ITALIC;

  private static final String TAG = "WebvttCueParser";

  private StringBuilder textBuilder;
  private PositionHolder positionHolder;

  public WebvttCueParser() {
    positionHolder = new PositionHolder();
    textBuilder = new StringBuilder();
  }

  /**
   * Parses the next valid Webvtt cue in a parsable array, including timestamps, settings and text.
   *
   * @param webvttData parsable Webvtt file data.
   * @return a {@link WebvttCue} instance if cue content is found. {@code null} otherwise.
   */
  public WebvttCue parseNextValidCue(ParsableByteArray webvttData) {
    Matcher cueHeaderMatcher;
    while ((cueHeaderMatcher = findNextCueHeader(webvttData)) != null) {
      WebvttCue currentCue = parseCue(cueHeaderMatcher, webvttData);
      if (currentCue != null) {
        return currentCue;
      }
    }
    return null;
  }

  private WebvttCue parseCue(Matcher cueHeaderMatcher, ParsableByteArray webvttData) {
    long cueStartTime;
    long cueEndTime;
    try {
      // Parse the cue start and end times.
      cueStartTime = WebvttParserUtil.parseTimestampUs(cueHeaderMatcher.group(1));
      cueEndTime = WebvttParserUtil.parseTimestampUs(cueHeaderMatcher.group(2));
    } catch (NumberFormatException e) {
      Log.w(TAG, "Skipping cue with bad header: " + cueHeaderMatcher.group());
      return null;
    }

    // Default cue settings.
    Alignment cueTextAlignment = null;
    float cueLine = Cue.DIMEN_UNSET;
    int cueLineType = Cue.TYPE_UNSET;
    int cueLineAnchor = Cue.TYPE_UNSET;
    float cuePosition = Cue.DIMEN_UNSET;
    int cuePositionAnchor = Cue.TYPE_UNSET;
    float cueWidth = Cue.DIMEN_UNSET;

    // Parse the cue settings list.
    Matcher cueSettingMatcher = CUE_SETTING_PATTERN.matcher(cueHeaderMatcher.group(3));
    while (cueSettingMatcher.find()) {
      String name = cueSettingMatcher.group(1);
      String value = cueSettingMatcher.group(2);
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
          cueWidth = WebvttParserUtil.parsePercentage(value);
        } else {
          Log.w(TAG, "Unknown cue setting " + name + ":" + value);
        }
      } catch (NumberFormatException e) {
        Log.w(TAG, "Skipping bad cue setting: " + cueSettingMatcher.group());
      }
    }

    if (cuePosition != Cue.DIMEN_UNSET && cuePositionAnchor == Cue.TYPE_UNSET) {
      // Computed position alignment should be derived from the text alignment if it has not been
      // set explicitly.
      cuePositionAnchor = alignmentToAnchor(cueTextAlignment);
    }

    // Parse the cue text.
    textBuilder.setLength(0);
    String line;
    while ((line = webvttData.readLine()) != null && !line.isEmpty()) {
      if (textBuilder.length() > 0) {
        textBuilder.append("\n");
      }
      textBuilder.append(line.trim());
    }

    CharSequence cueText = parseCueText(textBuilder.toString());

    return new WebvttCue(cueStartTime, cueEndTime, cueText, cueTextAlignment, cueLine,
        cueLineType, cueLineAnchor, cuePosition, cuePositionAnchor, cueWidth);
  }

  /* package */ static Spanned parseCueText(String markup) {
    SpannableStringBuilder spannedText = new SpannableStringBuilder();
    Stack<StartTag> startTagStack = new Stack<>();
    String[] tagTokens;
    int pos = 0;
    while (pos < markup.length()) {
      char curr = markup.charAt(pos);
      switch (curr) {
        case CHAR_LESS_THAN:
          if (pos + 1 >= markup.length()) {
            pos++;
            break; // avoid ArrayOutOfBoundsException
          }
          int ltPos = pos;
          boolean isClosingTag = markup.charAt(ltPos + 1) == CHAR_SLASH;
          pos = findEndOfTag(markup, ltPos + 1);
          boolean isVoidTag = markup.charAt(pos - 2) == CHAR_SLASH;

          tagTokens = tokenizeTag(markup.substring(
              ltPos + (isClosingTag ? 2 : 1), isVoidTag ? pos - 2 : pos - 1));
          if (tagTokens == null || !isSupportedTag(tagTokens[0])) {
            continue;
          }
          if (isClosingTag) {
            StartTag startTag;
            do {
              if (startTagStack.isEmpty()) {
                break;
              }
              startTag = startTagStack.pop();
              applySpansForTag(startTag, spannedText);
            } while(!startTag.name.equals(tagTokens[0]));
          } else if (!isVoidTag) {
            startTagStack.push(new StartTag(tagTokens[0], spannedText.length()));
          }
          break;
        case CHAR_AMPERSAND:
          int semiColonEnd = markup.indexOf(CHAR_SEMI_COLON, pos + 1);
          int spaceEnd = markup.indexOf(CHAR_SPACE, pos + 1);
          int entityEnd = semiColonEnd == -1 ? spaceEnd
              : spaceEnd == -1 ? semiColonEnd : Math.min(semiColonEnd, spaceEnd);
          if (entityEnd != -1) {
            applyEntity(markup.substring(pos + 1, entityEnd), spannedText);
            if (entityEnd == spaceEnd) {
              spannedText.append(" ");
            }
            pos = entityEnd + 1;
          } else {
            spannedText.append(curr);
            pos++;
          }
          break;
        default:
          spannedText.append(curr);
          pos++;
          break;
      }
    }
    // apply unclosed tags
    while (!startTagStack.isEmpty()) {
      applySpansForTag(startTagStack.pop(), spannedText);
    }
    return spannedText;
  }

  /**
   * Reads lines up to and including the next WebVTT cue header.
   *
   * @param input The input from which lines should be read.
   * @return A {@link Matcher} for the WebVTT cue header, or null if the end of the input was
   *     reached without a cue header being found. In the case that a cue header is found, groups 1,
   *     2 and 3 of the returned matcher contain the start time, end time and settings list.
   */
  public static Matcher findNextCueHeader(ParsableByteArray input) {
    String line;
    while ((line = input.readLine()) != null) {
      if (COMMENT.matcher(line).matches()) {
        // Skip until the end of the comment block.
        while ((line = input.readLine()) != null && !line.isEmpty()) {}
      } else {
        Matcher cueHeaderMatcher = WebvttCueParser.CUE_HEADER_PATTERN.matcher(line);
        if (cueHeaderMatcher.matches()) {
          return cueHeaderMatcher;
        }
      }
    }
    return null;
  }

  private static final class PositionHolder {

    public float position;
    public int positionAnchor;
    public int lineType;

  }

  // Internal methods

  private static void parseLineAttribute(String s, PositionHolder out)
      throws NumberFormatException {
    int lineAnchor;
    int commaPosition = s.indexOf(',');
    if (commaPosition != -1) {
      lineAnchor = parsePositionAnchor(s.substring(commaPosition + 1));
      s = s.substring(0, commaPosition);
    } else {
      lineAnchor = Cue.TYPE_UNSET;
    }
    float line;
    int lineType;
    if (s.endsWith("%")) {
      line = WebvttParserUtil.parsePercentage(s);
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
    int commaPosition = s.indexOf(',');
    if (commaPosition != -1) {
      positionAnchor = parsePositionAnchor(s.substring(commaPosition + 1));
      s = s.substring(0, commaPosition);
    } else {
      positionAnchor = Cue.TYPE_UNSET;
    }
    out.position = WebvttParserUtil.parsePercentage(s);
    out.positionAnchor = positionAnchor;
    out.lineType = Cue.TYPE_UNSET;
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

  /**
   * Find end of tag (&gt;). The position returned is the position of the &gt; plus one (exclusive).
   *
   * @param markup The webvtt cue markup to be parsed.
   * @param startPos the position from where to start searching for the end of tag.
   * @return the position of the end of tag plus 1 (one).
   */
  private static int findEndOfTag(String markup, int startPos) {
    int idx = markup.indexOf(CHAR_GREATER_THAN, startPos);
    return idx == -1 ? markup.length() : idx + 1;
  }

  private static void applyEntity(String entity, SpannableStringBuilder spannedText) {
    switch (entity) {
      case ENTITY_LESS_THAN:
        spannedText.append('<');
        break;
      case ENTITY_GREATER_THAN:
        spannedText.append('>');
        break;
      case ENTITY_NON_BREAK_SPACE:
        spannedText.append(' ');
        break;
      case ENTITY_AMPERSAND:
        spannedText.append('&');
        break;
      default:
        Log.w(TAG, "ignoring unsupported entity: '&" + entity + ";'");
        break;
    }
  }

  private static boolean isSupportedTag(String tagName) {
    switch (tagName) {
      case TAG_BOLD:
      case TAG_CLASS:
      case TAG_ITALIC:
      case TAG_LANG:
      case TAG_UNDERLINE:
      case TAG_VOICE:
        return true;
      default:
        return false;
    }
  }

  private static void applySpansForTag(StartTag startTag, SpannableStringBuilder spannedText) {
    switch(startTag.name) {
      case TAG_BOLD:
        spannedText.setSpan(new StyleSpan(STYLE_BOLD), startTag.position,
            spannedText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return;
      case TAG_ITALIC:
        spannedText.setSpan(new StyleSpan(STYLE_ITALIC), startTag.position,
            spannedText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return;
      case TAG_UNDERLINE:
        spannedText.setSpan(new UnderlineSpan(), startTag.position,
            spannedText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return;
      default:
        break;
    }
  }

  /**
   * Tokenizes a tag expression into tag name (pos 0) and classes (pos 1..n).
   *
   * @param fullTagExpression characters between &amp;lt: and &amp;gt; of a start or end tag
   * @return an array of <code>String</code>s with the tag name at pos 0 followed by style classes
   *    or null if it's an empty tag: '&lt;&gt;'
   */
  private static String[] tokenizeTag(String fullTagExpression) {
    fullTagExpression = fullTagExpression.replace("\\s+", " ").trim();
    if (fullTagExpression.length() == 0) {
      return null;
    }
    if (fullTagExpression.contains(SPACE)) {
      fullTagExpression = fullTagExpression.substring(0, fullTagExpression.indexOf(SPACE));
    }
    return fullTagExpression.split("\\.");
  }

  private static final class StartTag {

    public final String name;
    public final int position;

    public StartTag(String name, int position) {
      this.position = position;
      this.name = name;
    }

  }

}
