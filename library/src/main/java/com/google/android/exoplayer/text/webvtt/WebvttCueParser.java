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
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.AlignmentSpan;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.util.Log;

import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for WebVTT cues. (https://w3c.github.io/webvtt/#cues)
 */
/* package */ final class WebvttCueParser {

  public static final String UNIVERSAL_CUE_ID = "";
  public static final Pattern CUE_HEADER_PATTERN = Pattern
      .compile("^(\\S+)\\s+-->\\s+(\\S+)(.*)?$");

  private static final Pattern CUE_SETTING_PATTERN = Pattern.compile("(\\S+?):(\\S+)");

  private static final char CHAR_LESS_THAN = '<';
  private static final char CHAR_GREATER_THAN = '>';
  private static final char CHAR_SLASH = '/';
  private static final char CHAR_AMPERSAND = '&';
  private static final char CHAR_SEMI_COLON = ';';
  private static final char CHAR_SPACE = ' ';

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
  
  private static final String CUE_ID_PREFIX = "#";
  private static final String CUE_VOICE_PREFIX = "v[voice=\"";
  private static final String CUE_VOICE_SUFFIX = "\"]";

  private static final int STYLE_BOLD = Typeface.BOLD;
  private static final int STYLE_ITALIC = Typeface.ITALIC;

  private static final String TAG = "WebvttCueParser";

  private final StringBuilder textBuilder;

  public WebvttCueParser() {
    textBuilder = new StringBuilder();
  }
  
  /**
   * Parses the next valid WebVTT cue in a parsable array, including timestamps, settings and text.
   *
   * @param webvttData Parsable WebVTT file data.
   * @param builder Builder for WebVTT Cues.
   * @param styleMap Maps selector to style as referenced by the CSS ::cue pseudo-element.
   * @return True if a valid Cue was found, false otherwise.
   */
  /* package */ boolean parseCue(ParsableByteArray webvttData, WebvttCue.Builder builder,
      Map<String, WebvttCssStyle> styleMap) {
    String firstLine = webvttData.readLine();
    Matcher cueHeaderMatcher = WebvttCueParser.CUE_HEADER_PATTERN.matcher(firstLine);
    if (cueHeaderMatcher.matches()) {
      // We have found the timestamps in the first line. No id present.
      return parseCue(null, cueHeaderMatcher, webvttData, builder, textBuilder, styleMap);
    } else {
      // The first line is not the timestamps, but could be the cue id.
      String secondLine = webvttData.readLine();
      cueHeaderMatcher = WebvttCueParser.CUE_HEADER_PATTERN.matcher(secondLine);
      if (cueHeaderMatcher.matches()) {
        // We can do the rest of the parsing, including the id.
        return parseCue(firstLine.trim(), cueHeaderMatcher, webvttData, builder, textBuilder, 
            styleMap);
      }
    }
    return false;
  }

  /**
   * Parses a string containing a list of cue settings.
   *
   * @param cueSettingsList String containing the settings for a given cue.
   * @param builder The {@link WebvttCue.Builder} where incremental construction takes place.
   */
  /* package */ static void parseCueSettingsList(String cueSettingsList,
      WebvttCue.Builder builder) {
    // Parse the cue settings list.
    Matcher cueSettingMatcher = CUE_SETTING_PATTERN.matcher(cueSettingsList);
    while (cueSettingMatcher.find()) {
      String name = cueSettingMatcher.group(1);
      String value = cueSettingMatcher.group(2);
      try {
        if ("line".equals(name)) {
          parseLineAttribute(value, builder);
        } else if ("align".equals(name)) {
          builder.setTextAlignment(parseTextAlignment(value));
        } else if ("position".equals(name)) {
          parsePositionAttribute(value, builder);
        } else if ("size".equals(name)) {
          builder.setWidth(WebvttParserUtil.parsePercentage(value));
        } else {
          Log.w(TAG, "Unknown cue setting " + name + ":" + value);
        }
      } catch (NumberFormatException e) {
        Log.w(TAG, "Skipping bad cue setting: " + cueSettingMatcher.group());
      }
    }
  }

  /**
   * Parses the text payload of a WebVTT Cue and applies modifications on {@link WebvttCue.Builder}.
   *
   * @param id Id of the cue, {@code null} if it is not present.
   * @param markup The markup text to be parsed.
   * @param styleMap Maps selector to style as referenced by the CSS ::cue pseudo-element.
   * @param builder Target builder.
   */
  /* package */ static void parseCueText(String id, String markup, WebvttCue.Builder builder,
      Map<String, WebvttCssStyle> styleMap) {
    SpannableStringBuilder spannedText = new SpannableStringBuilder();
    Stack<StartTag> startTagStack = new Stack<>();
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
          String fullTagExpression = markup.substring(ltPos + (isClosingTag ? 2 : 1),
              isVoidTag ? pos - 2 : pos - 1);
          String tagName = getTagName(fullTagExpression);
          if (tagName == null || !isSupportedTag(tagName)) {
            continue;
          }
          if (isClosingTag) {
            StartTag startTag;
            do {
              if (startTagStack.isEmpty()) {
                break;
              }
              startTag = startTagStack.pop();
              applySpansForTag(startTag, spannedText, styleMap);
            } while(!startTag.name.equals(tagName));
          } else if (!isVoidTag) {
            startTagStack.push(new StartTag(tagName, spannedText.length(),
                TAG_VOICE.equals(tagName) ? getVoiceName(fullTagExpression) : null));
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
      applySpansForTag(startTagStack.pop(), spannedText, styleMap);
    }
    applyStyleToText(spannedText, styleMap.get(UNIVERSAL_CUE_ID), 0, spannedText.length());
    applyStyleToText(spannedText, styleMap.get(CUE_ID_PREFIX + id), 0, spannedText.length());
    builder.setText(spannedText);
  }

  private static boolean parseCue(String id, Matcher cueHeaderMatcher, ParsableByteArray webvttData,
      WebvttCue.Builder builder, StringBuilder textBuilder, Map<String, WebvttCssStyle> styleMap) {
    try {
      // Parse the cue start and end times.
      builder.setStartTime(WebvttParserUtil.parseTimestampUs(cueHeaderMatcher.group(1)))
          .setEndTime(WebvttParserUtil.parseTimestampUs(cueHeaderMatcher.group(2)));
    } catch (NumberFormatException e) {
      Log.w(TAG, "Skipping cue with bad header: " + cueHeaderMatcher.group());
      return false;
    }

    parseCueSettingsList(cueHeaderMatcher.group(3), builder);

    // Parse the cue text.
    textBuilder.setLength(0);
    String line;
    while ((line = webvttData.readLine()) != null && !line.isEmpty()) {
      if (textBuilder.length() > 0) {
        textBuilder.append("\n");
      }
      textBuilder.append(line.trim());
    }
    parseCueText(id, textBuilder.toString(), builder, styleMap);
    return true;
  }

  // Internal methods

  private static void parseLineAttribute(String s, WebvttCue.Builder builder)
      throws NumberFormatException {
    int commaPosition = s.indexOf(',');
    if (commaPosition != -1) {
      builder.setLineAnchor(parsePositionAnchor(s.substring(commaPosition + 1)));
      s = s.substring(0, commaPosition);
    } else {
      builder.setLineAnchor(Cue.TYPE_UNSET);
    }
    if (s.endsWith("%")) {
      builder.setLine(WebvttParserUtil.parsePercentage(s)).setLineType(Cue.LINE_TYPE_FRACTION);
    } else {
      builder.setLine(Integer.parseInt(s)).setLineType(Cue.LINE_TYPE_NUMBER);
    }
  }

  private static void parsePositionAttribute(String s, WebvttCue.Builder builder)
      throws NumberFormatException {
    int commaPosition = s.indexOf(',');
    if (commaPosition != -1) {
      builder.setPositionAnchor(parsePositionAnchor(s.substring(commaPosition + 1)));
      s = s.substring(0, commaPosition);
    } else {
      builder.setPositionAnchor(Cue.TYPE_UNSET);
    }
    builder.setPosition(WebvttParserUtil.parsePercentage(s));
  }

  private static int parsePositionAnchor(String s) {
    switch (s) {
      case "start":
        return Cue.ANCHOR_TYPE_START;
      case "center":
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
      case "center":
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

  /**
   * Find end of tag (&gt;). The position returned is the position of the &gt; plus one (exclusive).
   *
   * @param markup The WebVTT cue markup to be parsed.
   * @param startPos The position from where to start searching for the end of tag.
   * @return The position of the end of tag plus 1 (one).
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

  private static void applySpansForTag(StartTag startTag, SpannableStringBuilder spannedText,
      Map<String, WebvttCssStyle> styleMap) {
    WebvttCssStyle styleForTag = styleMap.get(startTag.name);
    int start = startTag.position;
    int end = spannedText.length();
    switch(startTag.name) {
      case TAG_BOLD:
        spannedText.setSpan(new StyleSpan(STYLE_BOLD), start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        break;
      case TAG_ITALIC:
        spannedText.setSpan(new StyleSpan(STYLE_ITALIC), start, end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        break;
      case TAG_UNDERLINE:
        spannedText.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        break;
      case TAG_CLASS:
      case TAG_LANG:
      case TAG_VOICE:
        break;  
      default:
        return;
    }
    applyStyleToText(spannedText, styleForTag, start, end);
    if (startTag.voiceName != null) {
      WebvttCssStyle styleForVoice = styleMap.get(CUE_VOICE_PREFIX + startTag.voiceName
          + CUE_VOICE_SUFFIX);
      applyStyleToText(spannedText, styleForVoice, start, end);
    }
  }
  
  private static void applyStyleToText(SpannableStringBuilder spannedText, 
      WebvttCssStyle style, int start, int end) {
    if (style == null) {
      return;
    }
    if (style.getStyle() != WebvttCssStyle.UNSPECIFIED) {
      spannedText.setSpan(new StyleSpan(style.getStyle()), start, end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.isLinethrough()) {
      spannedText.setSpan(new StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.isUnderline()) {
      spannedText.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.hasFontColor()) {
      spannedText.setSpan(new ForegroundColorSpan(style.getFontColor()), start, end,
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.hasBackgroundColor()) {
      spannedText.setSpan(new BackgroundColorSpan(style.getBackgroundColor()), start, end,
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.getFontFamily() != null) {
      spannedText.setSpan(new TypefaceSpan(style.getFontFamily()), start, end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.getTextAlign() != null) {
      spannedText.setSpan(new AlignmentSpan.Standard(style.getTextAlign()), start, end,
          Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
    if (style.getFontSizeUnit() != WebvttCssStyle.UNSPECIFIED) {
      switch (style.getFontSizeUnit()) {
        case WebvttCssStyle.FONT_SIZE_UNIT_PIXEL:
          spannedText.setSpan(new AbsoluteSizeSpan((int) style.getFontSize(), true), start, end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          break;
        case WebvttCssStyle.FONT_SIZE_UNIT_EM:
          spannedText.setSpan(new RelativeSizeSpan(style.getFontSize()), start, end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          break;
        case WebvttCssStyle.FONT_SIZE_UNIT_PERCENT:
          spannedText.setSpan(new RelativeSizeSpan(style.getFontSize() / 100), start, end,
              Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
          break;
      }
    }
  }

  /**
   * Gets the tag name for the given tag contents.
   *
   * @param tagExpression Characters between &amp;lt: and &amp;gt; of a start or end tag.
   * @return The name of tag.
   */
  private static String getTagName(String tagExpression) {
    tagExpression = tagExpression.trim();
    if (tagExpression.isEmpty()) {
      return null;
    }
    return tagExpression.split("[ \\.]")[0];
  }
  
  private static String getVoiceName(String fullTagExpression) {
    return fullTagExpression.trim().substring(fullTagExpression.indexOf(" ")).trim();
  }

  private static final class StartTag {

    public final String name;
    public final int position;
    public final String voiceName;

    public StartTag(String name, int position, String voiceName) {
      this.position = position;
      this.name = name;
      this.voiceName = voiceName;
    }

  }

}
