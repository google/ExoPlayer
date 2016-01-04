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
package com.google.android.exoplayer.text.ttml;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.ParserUtil;
import com.google.android.exoplayer.util.Util;

import android.text.Layout;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple TTML parser that supports DFXP presentation profile.
 * <p>
 * Supported features in this parser are:
 * <ul>
 *   <li>content
 *   <li>core
 *   <li>presentation
 *   <li>profile
 *   <li>structure
 *   <li>time-offset
 *   <li>timing
 *   <li>tickRate
 *   <li>time-clock-with-frames
 *   <li>time-clock
 *   <li>time-offset-with-frames
 *   <li>time-offset-with-ticks
 * </ul>
 * </p>
 * @see <a href="http://www.w3.org/TR/ttaf1-dfxp/">TTML specification</a>
 */
public final class TtmlParser implements SubtitleParser {

  private static final String TAG = "TtmlParser";

  private static final String ATTR_BEGIN = "begin";
  private static final String ATTR_DURATION = "dur";
  private static final String ATTR_END = "end";
  private static final String ATTR_STYLE = "style";

  private static final Pattern CLOCK_TIME =
      Pattern.compile("^([0-9][0-9]+):([0-9][0-9]):([0-9][0-9])"
          + "(?:(\\.[0-9]+)|:([0-9][0-9])(?:\\.([0-9]+))?)?$");
  private static final Pattern OFFSET_TIME =
      Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)(h|m|s|ms|f|t)$");
  private static final Pattern FONT_SIZE =
      Pattern.compile("^(([0-9]*.)?[0-9]+)(px|em|%)$");

  // TODO: read and apply the following attributes if specified.
  private static final int DEFAULT_FRAMERATE = 30;
  private static final int DEFAULT_SUBFRAMERATE = 1;
  private static final int DEFAULT_TICKRATE = 1;

  private final XmlPullParserFactory xmlParserFactory;

  public TtmlParser() {
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_TTML.equals(mimeType);
  }

  @Override
  public TtmlSubtitle parse(byte[] bytes, int offset, int length) throws ParserException {
    try {
      XmlPullParser xmlParser = xmlParserFactory.newPullParser();
      Map<String, TtmlStyle> globalStyles = new HashMap<>();
      ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes, offset, length);
      xmlParser.setInput(inputStream, null);
      TtmlSubtitle ttmlSubtitle = null;
      LinkedList<TtmlNode> nodeStack = new LinkedList<>();
      int unsupportedNodeDepth = 0;
      int eventType = xmlParser.getEventType();
      while (eventType != XmlPullParser.END_DOCUMENT) {
        TtmlNode parent = nodeStack.peekLast();
        if (unsupportedNodeDepth == 0) {
          String name = xmlParser.getName();
          if (eventType == XmlPullParser.START_TAG) {
            if (!isSupportedTag(name)) {
              Log.i(TAG, "Ignoring unsupported tag: " + xmlParser.getName());
              unsupportedNodeDepth++;
            } else if (TtmlNode.TAG_HEAD.equals(name)) {
              parseHeader(xmlParser, globalStyles);
            } else {
              try {
                TtmlNode node = parseNode(xmlParser, parent);
                nodeStack.addLast(node);
                if (parent != null) {
                  parent.addChild(node);
                }
              } catch (ParserException e) {
                Log.w(TAG, "Suppressing parser error", e);
                // Treat the node (and by extension, all of its children) as unsupported.
                unsupportedNodeDepth++;
              }
            }
          } else if (eventType == XmlPullParser.TEXT) {
            parent.addChild(TtmlNode.buildTextNode(xmlParser.getText()));
          } else if (eventType == XmlPullParser.END_TAG) {
            if (xmlParser.getName().equals(TtmlNode.TAG_TT)) {
              ttmlSubtitle = new TtmlSubtitle(nodeStack.getLast(), globalStyles);
            }
            nodeStack.removeLast();
          }
        } else {
          if (eventType == XmlPullParser.START_TAG) {
            unsupportedNodeDepth++;
          } else if (eventType == XmlPullParser.END_TAG) {
            unsupportedNodeDepth--;
          }
        }
        xmlParser.next();
        eventType = xmlParser.getEventType();
      }
      return ttmlSubtitle;
    } catch (XmlPullParserException xppe) {
      throw new ParserException("Unable to parse source", xppe);
    } catch (IOException e) {
      throw new IllegalStateException("Unexpected error when reading input.", e);
    }
  }

  private Map<String, TtmlStyle> parseHeader(XmlPullParser xmlParser,
      Map<String, TtmlStyle> globalStyles)
      throws IOException, XmlPullParserException {

    do {
      xmlParser.next();
      if (ParserUtil.isStartTag(xmlParser, TtmlNode.TAG_STYLE)) {
        String parentStyleId = xmlParser.getAttributeValue(null, ATTR_STYLE);
        TtmlStyle style = parseStyleAttributes(xmlParser, new TtmlStyle());
        if (parentStyleId != null) {
          String[] ids = parseStyleIds(parentStyleId);
          for (int i = 0; i < ids.length; i++) {
            style.chain(globalStyles.get(ids[i]));
          }
        }
        if (style.getId() != null) {
          globalStyles.put(style.getId(), style);
        }
      }
    } while (!ParserUtil.isEndTag(xmlParser, TtmlNode.TAG_HEAD));
    return globalStyles;
  }

  private String[] parseStyleIds(String parentStyleIds) {
    return parentStyleIds.split("\\s+");
  }

  private TtmlStyle parseStyleAttributes(XmlPullParser parser, TtmlStyle style) {
    int attributeCount = parser.getAttributeCount();
    for (int i = 0; i < attributeCount; i++) {
      String attributeName = parser.getAttributeName(i);
      String attributeValue = parser.getAttributeValue(i);
      switch (ParserUtil.removeNamespacePrefix(attributeName)) {
        case TtmlNode.ATTR_ID:
          if (TtmlNode.TAG_STYLE.equals(parser.getName())) {
            style = createIfNull(style).setId(attributeValue);
          }
          break;
        case TtmlNode.ATTR_TTS_BACKGROUND_COLOR:
          style = createIfNull(style);
          try {
            style.setBackgroundColor(TtmlColorParser.parseColor(attributeValue));
          } catch (IllegalArgumentException e) {
            Log.w(TAG, "failed parsing background value: '" + attributeValue + "'");
          }
          break;
        case TtmlNode.ATTR_TTS_COLOR:
          style = createIfNull(style);
          try {
            style.setColor(TtmlColorParser.parseColor(attributeValue));
          } catch (IllegalArgumentException e) {
            Log.w(TAG, "failed parsing color value: '" + attributeValue + "'");
          }
          break;
        case TtmlNode.ATTR_TTS_FONT_FAMILY:
          style = createIfNull(style).setFontFamily(attributeValue);
          break;
        case TtmlNode.ATTR_TTS_FONT_SIZE:
          try {
            style = createIfNull(style);
            parseFontSize(attributeValue, style);
          } catch (ParserException e) {
            Log.w(TAG, "failed parsing fontSize value: '" + attributeValue + "'");
          }
          break;
        case TtmlNode.ATTR_TTS_FONT_WEIGHT:
          style = createIfNull(style).setBold(
              TtmlNode.BOLD.equalsIgnoreCase(attributeValue));
          break;
        case TtmlNode.ATTR_TTS_FONT_STYLE:
          style = createIfNull(style).setItalic(
              TtmlNode.ITALIC.equalsIgnoreCase(attributeValue));
          break;
        case TtmlNode.ATTR_TTS_TEXT_ALIGN:
          switch (Util.toLowerInvariant(attributeValue)) {
            case TtmlNode.LEFT:
              style = createIfNull(style).setTextAlign(Layout.Alignment.ALIGN_NORMAL);
              break;
            case TtmlNode.START:
              style = createIfNull(style).setTextAlign(Layout.Alignment.ALIGN_NORMAL);
              break;
            case TtmlNode.RIGHT:
              style = createIfNull(style).setTextAlign(Layout.Alignment.ALIGN_OPPOSITE);
              break;
            case TtmlNode.END:
              style = createIfNull(style).setTextAlign(Layout.Alignment.ALIGN_OPPOSITE);
              break;
            case TtmlNode.CENTER:
              style = createIfNull(style).setTextAlign(Layout.Alignment.ALIGN_CENTER);
              break;
          }
          break;
        case TtmlNode.ATTR_TTS_TEXT_DECORATION:
          switch (Util.toLowerInvariant(attributeValue)) {
            case TtmlNode.LINETHROUGH:
              style = createIfNull(style).setLinethrough(true);
              break;
            case TtmlNode.NO_LINETHROUGH:
              style = createIfNull(style).setLinethrough(false);
              break;
            case TtmlNode.UNDERLINE:
              style = createIfNull(style).setUnderline(true);
              break;
            case TtmlNode.NO_UNDERLINE:
              style = createIfNull(style).setUnderline(false);
              break;
          }
          break;
        default:
          // ignore
          break;
      }
    }
    return style;
  }

  private TtmlStyle createIfNull(TtmlStyle style) {
    return style == null ? new TtmlStyle() : style;
  }

  private TtmlNode parseNode(XmlPullParser parser, TtmlNode parent) throws ParserException {
    long duration = 0;
    long startTime = TtmlNode.UNDEFINED_TIME;
    long endTime = TtmlNode.UNDEFINED_TIME;
    String[] styleIds = null;
    int attributeCount = parser.getAttributeCount();
    TtmlStyle style = parseStyleAttributes(parser, null);
    for (int i = 0; i < attributeCount; i++) {
      String attr = ParserUtil.removeNamespacePrefix(parser.getAttributeName(i));
      String value = parser.getAttributeValue(i);
      if (attr.equals(ATTR_BEGIN)) {
        startTime = parseTimeExpression(value,
            DEFAULT_FRAMERATE, DEFAULT_SUBFRAMERATE, DEFAULT_TICKRATE);
      } else if (attr.equals(ATTR_END)) {
        endTime = parseTimeExpression(value,
            DEFAULT_FRAMERATE, DEFAULT_SUBFRAMERATE, DEFAULT_TICKRATE);
      } else if (attr.equals(ATTR_DURATION)) {
        duration = parseTimeExpression(value,
            DEFAULT_FRAMERATE, DEFAULT_SUBFRAMERATE, DEFAULT_TICKRATE);
      } else if (attr.equals(ATTR_STYLE)) {
        // IDREFS: potentially multiple space delimited ids
        String[] ids = parseStyleIds(value);
        if (ids.length > 0) {
          styleIds = ids;
        }
      } else {
        // Do nothing.
      }
    }
    if (parent != null && parent.startTimeUs != TtmlNode.UNDEFINED_TIME) {
      if (startTime != TtmlNode.UNDEFINED_TIME) {
        startTime += parent.startTimeUs;
      }
      if (endTime != TtmlNode.UNDEFINED_TIME) {
        endTime += parent.startTimeUs;
      }
    }
    if (endTime == TtmlNode.UNDEFINED_TIME) {
      if (duration > 0) {
        // Infer the end time from the duration.
        endTime = startTime + duration;
      } else if (parent != null && parent.endTimeUs != TtmlNode.UNDEFINED_TIME) {
        // If the end time remains unspecified, then it should be inherited from the parent.
        endTime = parent.endTimeUs;
      }
    }
    return TtmlNode.buildNode(parser.getName(), startTime, endTime, style, styleIds);
  }

  private static boolean isSupportedTag(String tag) {
    if (tag.equals(TtmlNode.TAG_TT)
        || tag.equals(TtmlNode.TAG_HEAD)
        || tag.equals(TtmlNode.TAG_BODY)
        || tag.equals(TtmlNode.TAG_DIV)
        || tag.equals(TtmlNode.TAG_P)
        || tag.equals(TtmlNode.TAG_SPAN)
        || tag.equals(TtmlNode.TAG_BR)
        || tag.equals(TtmlNode.TAG_STYLE)
        || tag.equals(TtmlNode.TAG_STYLING)
        || tag.equals(TtmlNode.TAG_LAYOUT)
        || tag.equals(TtmlNode.TAG_REGION)
        || tag.equals(TtmlNode.TAG_METADATA)
        || tag.equals(TtmlNode.TAG_SMPTE_IMAGE)
        || tag.equals(TtmlNode.TAG_SMPTE_DATA)
        || tag.equals(TtmlNode.TAG_SMPTE_INFORMATION)) {
      return true;
    }
    return false;
  }

  private static void parseFontSize(String expression, TtmlStyle out) throws ParserException {
    String[] expressions = expression.split("\\s+");
    Matcher matcher;
    if (expressions.length == 1) {
      matcher = FONT_SIZE.matcher(expression);
    } else if (expressions.length == 2){
      matcher = FONT_SIZE.matcher(expressions[1]);
      Log.w(TAG, "multiple values in fontSize attribute. Picking the second "
          + "value for vertical font size and ignoring the first.");
    } else {
      throw new ParserException();
    }

    if (matcher.matches()) {
      String unit = matcher.group(3);
      switch (unit) {
        case "px":
          out.setFontSizeUnit(TtmlStyle.FONT_SIZE_UNIT_PIXEL);
          break;
        case "em":
          out.setFontSizeUnit(TtmlStyle.FONT_SIZE_UNIT_EM);
          break;
        case "%":
          out.setFontSizeUnit(TtmlStyle.FONT_SIZE_UNIT_PERCENT);
          break;
        default:
          throw new ParserException();
      }
      out.setFontSize(Float.valueOf(matcher.group(1)));
    } else {
      throw new ParserException();
    }
  }

  /**
   * Parses a time expression, returning the parsed timestamp.
   * <p>
   * For the format of a time expression, see:
   * <a href="http://www.w3.org/TR/ttaf1-dfxp/#timing-value-timeExpression">timeExpression</a>
   *
   * @param time A string that includes the time expression.
   * @param frameRate The frame rate of the stream.
   * @param subframeRate The sub-frame rate of the stream
   * @param tickRate The tick rate of the stream.
   * @return The parsed timestamp in microseconds.
   * @throws ParserException If the given string does not contain a valid time expression.
   */
  private static long parseTimeExpression(String time, int frameRate, int subframeRate,
      int tickRate) throws ParserException {
    Matcher matcher = CLOCK_TIME.matcher(time);
    if (matcher.matches()) {
      String hours = matcher.group(1);
      double durationSeconds = Long.parseLong(hours) * 3600;
      String minutes = matcher.group(2);
      durationSeconds += Long.parseLong(minutes) * 60;
      String seconds = matcher.group(3);
      durationSeconds += Long.parseLong(seconds);
      String fraction = matcher.group(4);
      durationSeconds += (fraction != null) ? Double.parseDouble(fraction) : 0;
      String frames = matcher.group(5);
      durationSeconds += (frames != null) ? ((double) Long.parseLong(frames)) / frameRate : 0;
      String subframes = matcher.group(6);
      durationSeconds += (subframes != null) ?
          ((double) Long.parseLong(subframes)) / subframeRate / frameRate : 0;
      return (long) (durationSeconds * C.MICROS_PER_SECOND);
    }
    matcher = OFFSET_TIME.matcher(time);
    if (matcher.matches()) {
      String timeValue = matcher.group(1);
      double offsetSeconds = Double.parseDouble(timeValue);
      String unit = matcher.group(2);
      if (unit.equals("h")) {
        offsetSeconds *= 3600;
      } else if (unit.equals("m")) {
        offsetSeconds *= 60;
      } else if (unit.equals("s")) {
        // Do nothing.
      } else if (unit.equals("ms")) {
        offsetSeconds /= 1000;
      } else if (unit.equals("f")) {
        offsetSeconds /= frameRate;
      } else if (unit.equals("t")) {
        offsetSeconds /= tickRate;
      }
      return (long) (offsetSeconds * C.MICROS_PER_SECOND);
    }
    throw new ParserException("Malformed time expression: " + time);
  }

}
