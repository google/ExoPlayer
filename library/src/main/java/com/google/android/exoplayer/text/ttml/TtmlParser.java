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

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.text.Subtitle;
import com.google.android.exoplayer.text.SubtitleParser;
import com.google.android.exoplayer.util.MimeTypes;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
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
public class TtmlParser implements SubtitleParser {

  private static final String TAG = "TtmlParser";

  private static final String ATTR_BEGIN = "begin";
  private static final String ATTR_DURATION = "dur";
  private static final String ATTR_END = "end";

  private static final Pattern CLOCK_TIME =
      Pattern.compile("^([0-9][0-9]+):([0-9][0-9]):([0-9][0-9])"
          + "(?:(\\.[0-9]+)|:([0-9][0-9])(?:\\.([0-9]+))?)?$");
  private static final Pattern OFFSET_TIME =
      Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)(h|m|s|ms|f|t)$");

  // TODO: read and apply the following attributes if specified.
  private static final int DEFAULT_FRAMERATE = 30;
  private static final int DEFAULT_SUBFRAMERATE = 1;
  private static final int DEFAULT_TICKRATE = 1;

  private final XmlPullParserFactory xmlParserFactory;
  private final boolean strictParsing;

  /**
   * Equivalent to {@code TtmlParser(true)}.
   */
  public TtmlParser() {
    this(true);
  }

  /**
   * @param strictParsing If true, {@link #parse(InputStream, String, long)} will throw a
   *     {@link ParserException} if the stream contains invalid ttml. If false, the parser will
   *     make a best effort to ignore minor errors in the stream. Note however that a
   *     {@link ParserException} will still be thrown when this is not possible.
   */
  public TtmlParser(boolean strictParsing) {
    this.strictParsing = strictParsing;
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  @Override
  public Subtitle parse(InputStream inputStream, String inputEncoding, long startTimeUs)
      throws IOException {
    try {
      XmlPullParser xmlParser = xmlParserFactory.newPullParser();
      xmlParser.setInput(inputStream, inputEncoding);
      TtmlSubtitle ttmlSubtitle = null;
      LinkedList<TtmlNode> nodeStack = new LinkedList<TtmlNode>();
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
            } else {
              try {
                TtmlNode node = parseNode(xmlParser, parent);
                nodeStack.addLast(node);
                if (parent != null) {
                  parent.addChild(node);
                }
              } catch (ParserException e) {
                if (strictParsing) {
                  throw e;
                } else {
                  Log.e(TAG, "Suppressing parser error", e);
                  // Treat the node (and by extension, all of its children) as unsupported.
                  unsupportedNodeDepth++;
                }
              }
            }
          } else if (eventType == XmlPullParser.TEXT) {
            parent.addChild(TtmlNode.buildTextNode(xmlParser.getText()));
          } else if (eventType == XmlPullParser.END_TAG) {
            if (xmlParser.getName().equals(TtmlNode.TAG_TT)) {
              ttmlSubtitle = new TtmlSubtitle(nodeStack.getLast(), startTimeUs);
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
    }
  }

  @Override
  public boolean canParse(String mimeType) {
    return MimeTypes.APPLICATION_TTML.equals(mimeType);
  }

  private TtmlNode parseNode(XmlPullParser parser, TtmlNode parent) throws ParserException {
    long duration = 0;
    long startTime = TtmlNode.UNDEFINED_TIME;
    long endTime = TtmlNode.UNDEFINED_TIME;
    int attributeCount = parser.getAttributeCount();
    for (int i = 0; i < attributeCount; i++) {
      // TODO: check if it's safe to ignore the namespace of attributes as follows.
      String attr = parser.getAttributeName(i).replaceFirst("^.*:", "");
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
    return TtmlNode.buildNode(parser.getName(), startTime, endTime);
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

  /**
   * Parses a time expression, returning the parsed timestamp.
   * <p>
   * For the format of a time expression, see:
   * <a href="http://www.w3.org/TR/ttaf1-dfxp/#timing-value-timeExpression">timeExpression</a>
   *
   * @param time A string that includes the time expression.
   * @param frameRate The framerate of the stream.
   * @param subframeRate The sub-framerate of the stream
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
      return (long) (durationSeconds * 1000000);
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
      return (long) (offsetSeconds * 1000000);
    }
    throw new ParserException("Malformed time expression: " + time);
  }

}
