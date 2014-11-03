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
package com.google.android.exoplayer.dash.mpd;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentList;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentTemplate;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SegmentTimelineElement;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SingleSegmentBase;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.ManifestParser;
import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;
import android.text.TextUtils;

import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * A parser of media presentation description files.
 */
public class MediaPresentationDescriptionParser extends DefaultHandler
    implements ManifestParser<MediaPresentationDescription> {

  private final XmlPullParserFactory xmlParserFactory;

  public MediaPresentationDescriptionParser() {
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  // MPD parsing.

  @Override
  public MediaPresentationDescription parse(InputStream inputStream, String inputEncoding,
      String contentId, Uri baseUrl) throws IOException, ParserException {
    try {
      XmlPullParser xpp = xmlParserFactory.newPullParser();
      xpp.setInput(inputStream, inputEncoding);
      int eventType = xpp.next();
      if (eventType != XmlPullParser.START_TAG || !"MPD".equals(xpp.getName())) {
        throw new ParserException(
            "inputStream does not contain a valid media presentation description");
      }
      return parseMediaPresentationDescription(xpp, contentId, baseUrl);
    } catch (XmlPullParserException e) {
      throw new ParserException(e);
    } catch (ParseException e) {
      throw new ParserException(e);
    }
  }

  protected MediaPresentationDescription parseMediaPresentationDescription(XmlPullParser xpp,
      String contentId, Uri baseUrl) throws XmlPullParserException, IOException, ParseException {
    long availabilityStartTime = parseDateTime(xpp, "availabilityStartTime", -1);
    long durationMs = parseDuration(xpp, "mediaPresentationDuration", -1);
    long minBufferTimeMs = parseDuration(xpp, "minBufferTime", -1);
    String typeString = xpp.getAttributeValue(null, "type");
    boolean dynamic = (typeString != null) ? typeString.equals("dynamic") : false;
    long minUpdateTimeMs = (dynamic) ? parseDuration(xpp, "minimumUpdatePeriod", -1) : -1;
    long timeShiftBufferDepthMs = (dynamic) ? parseDuration(xpp, "timeShiftBufferDepth", -1)
        : -1;
    UtcTimingElement utcTiming = null;

    List<Period> periods = new ArrayList<Period>();
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        baseUrl = parseBaseUrl(xpp, baseUrl);
      } else if (isStartTag(xpp, "UTCTiming")) {
        utcTiming = parseUtcTiming(xpp);
      } else if (isStartTag(xpp, "Period")) {
        periods.add(parsePeriod(xpp, contentId, baseUrl, durationMs));
      }
    } while (!isEndTag(xpp, "MPD"));

    return buildMediaPresentationDescription(availabilityStartTime, durationMs, minBufferTimeMs,
        dynamic, minUpdateTimeMs, timeShiftBufferDepthMs, utcTiming, periods);
  }

  protected MediaPresentationDescription buildMediaPresentationDescription(
      long availabilityStartTime, long durationMs, long minBufferTimeMs, boolean dynamic,
      long minUpdateTimeMs, long timeShiftBufferDepthMs, UtcTimingElement utcTiming,
      List<Period> periods) {
    return new MediaPresentationDescription(availabilityStartTime, durationMs, minBufferTimeMs,
        dynamic, minUpdateTimeMs, timeShiftBufferDepthMs, utcTiming, periods);
  }

  protected UtcTimingElement parseUtcTiming(XmlPullParser xpp) {
    String schemeIdUri = xpp.getAttributeValue(null, "schemeIdUri");
    String value = xpp.getAttributeValue(null, "value");
    return buildUtcTimingElement(schemeIdUri, value);
  }

  protected UtcTimingElement buildUtcTimingElement(String schemeIdUri, String value) {
    return new UtcTimingElement(schemeIdUri, value);
  }

  protected Period parsePeriod(XmlPullParser xpp, String contentId, Uri baseUrl, long mpdDurationMs)
      throws XmlPullParserException, IOException {
    String id = xpp.getAttributeValue(null, "id");
    long startMs = parseDuration(xpp, "start", 0);
    long durationMs = parseDuration(xpp, "duration", mpdDurationMs);
    SegmentBase segmentBase = null;
    List<AdaptationSet> adaptationSets = new ArrayList<AdaptationSet>();
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        baseUrl = parseBaseUrl(xpp, baseUrl);
      } else if (isStartTag(xpp, "AdaptationSet")) {
        adaptationSets.add(parseAdaptationSet(xpp, contentId, baseUrl, startMs, durationMs,
            segmentBase));
      } else if (isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, baseUrl, null);
      } else if (isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, baseUrl, null, durationMs);
      } else if (isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, baseUrl, null, durationMs);
      }
    } while (!isEndTag(xpp, "Period"));

    return buildPeriod(id, startMs, durationMs, adaptationSets);
  }

  protected Period buildPeriod(
      String id, long startMs, long durationMs, List<AdaptationSet> adaptationSets) {
    return new Period(id, startMs, durationMs, adaptationSets);
  }

  // AdaptationSet parsing.

  protected AdaptationSet parseAdaptationSet(XmlPullParser xpp, String contentId, Uri baseUrl,
      long periodStartMs, long periodDurationMs, SegmentBase segmentBase)
      throws XmlPullParserException, IOException {

    String mimeType = xpp.getAttributeValue(null, "mimeType");
    String language = xpp.getAttributeValue(null, "lang");
    int contentType = parseAdaptationSetTypeFromMimeType(mimeType);

    int id = -1;
    List<ContentProtection> contentProtections = null;
    List<Representation> representations = new ArrayList<Representation>();
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        baseUrl = parseBaseUrl(xpp, baseUrl);
      } else if (isStartTag(xpp, "ContentProtection")) {
        if (contentProtections == null) {
          contentProtections = new ArrayList<ContentProtection>();
        }
        contentProtections.add(parseContentProtection(xpp));
      } else if (isStartTag(xpp, "ContentComponent")) {
        id = Integer.parseInt(xpp.getAttributeValue(null, "id"));
        contentType = checkAdaptationSetTypeConsistency(contentType,
            parseAdaptationSetType(xpp.getAttributeValue(null, "contentType")));
      } else if (isStartTag(xpp, "Representation")) {
        Representation representation = parseRepresentation(xpp, contentId, baseUrl, periodStartMs,
            periodDurationMs, mimeType, language, segmentBase);
        contentType = checkAdaptationSetTypeConsistency(contentType,
            parseAdaptationSetTypeFromMimeType(representation.format.mimeType));
        representations.add(representation);
      } else if (isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, baseUrl, (SingleSegmentBase) segmentBase);
      } else if (isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, baseUrl, (SegmentList) segmentBase, periodDurationMs);
      } else if (isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, baseUrl, (SegmentTemplate) segmentBase,
            periodDurationMs);
      } else if (isStartTag(xpp)) {
        parseAdaptationSetChild(xpp);
      }
    } while (!isEndTag(xpp, "AdaptationSet"));

    return buildAdaptationSet(id, contentType, representations, contentProtections);
  }

  protected AdaptationSet buildAdaptationSet(int id, int contentType,
      List<Representation> representations, List<ContentProtection> contentProtections) {
    return new AdaptationSet(id, contentType, representations, contentProtections);
  }

  protected int parseAdaptationSetType(String contentType) {
    return TextUtils.isEmpty(contentType) ? AdaptationSet.TYPE_UNKNOWN
        : MimeTypes.BASE_TYPE_AUDIO.equals(contentType) ? AdaptationSet.TYPE_AUDIO
        : MimeTypes.BASE_TYPE_VIDEO.equals(contentType) ? AdaptationSet.TYPE_VIDEO
        : MimeTypes.BASE_TYPE_TEXT.equals(contentType) ? AdaptationSet.TYPE_TEXT
        : AdaptationSet.TYPE_UNKNOWN;
  }

  protected int parseAdaptationSetTypeFromMimeType(String mimeType) {
    return TextUtils.isEmpty(mimeType) ? AdaptationSet.TYPE_UNKNOWN
        : MimeTypes.isAudio(mimeType) ? AdaptationSet.TYPE_AUDIO
        : MimeTypes.isVideo(mimeType) ? AdaptationSet.TYPE_VIDEO
        : MimeTypes.isText(mimeType) || MimeTypes.isTtml(mimeType) ? AdaptationSet.TYPE_TEXT
        : AdaptationSet.TYPE_UNKNOWN;
  }

  /**
   * Checks two adaptation set types for consistency, returning the consistent type, or throwing an
   * {@link IllegalStateException} if the types are inconsistent.
   * <p>
   * Two types are consistent if they are equal, or if one is {@link AdaptationSet#TYPE_UNKNOWN}.
   * Where one of the types is {@link AdaptationSet#TYPE_UNKNOWN}, the other is returned.
   *
   * @param firstType The first type.
   * @param secondType The second type.
   * @return The consistent type.
   */
  private int checkAdaptationSetTypeConsistency(int firstType, int secondType) {
    if (firstType == AdaptationSet.TYPE_UNKNOWN) {
      return secondType;
    } else if (secondType == AdaptationSet.TYPE_UNKNOWN) {
      return firstType;
    } else {
      Assertions.checkState(firstType == secondType);
      return firstType;
    }
  }

  /**
   * Parses a ContentProtection element.
   *
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   **/
  protected ContentProtection parseContentProtection(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    String schemeIdUri = xpp.getAttributeValue(null, "schemeIdUri");
    return buildContentProtection(schemeIdUri);
  }

  protected ContentProtection buildContentProtection(String schemeIdUri) {
    return new ContentProtection(schemeIdUri);
  }

  /**
   * Parses children of AdaptationSet elements not specifically parsed elsewhere.
   *
   * @param xpp The XmpPullParser from which the AdaptationSet child should be parsed.
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   **/
  protected void parseAdaptationSetChild(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    // pass
  }

  // Representation parsing.

  protected Representation parseRepresentation(XmlPullParser xpp, String contentId, Uri baseUrl,
      long periodStartMs, long periodDurationMs, String mimeType, String language,
      SegmentBase segmentBase) throws XmlPullParserException, IOException {
    String id = xpp.getAttributeValue(null, "id");
    int bandwidth = parseInt(xpp, "bandwidth");
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate");
    int width = parseInt(xpp, "width");
    int height = parseInt(xpp, "height");
    mimeType = parseString(xpp, "mimeType", mimeType);

    int numChannels = -1;
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        baseUrl = parseBaseUrl(xpp, baseUrl);
      } else if (isStartTag(xpp, "AudioChannelConfiguration")) {
        numChannels = Integer.parseInt(xpp.getAttributeValue(null, "value"));
      } else if (isStartTag(xpp, "SegmentBase")) {
        segmentBase = parseSegmentBase(xpp, baseUrl, (SingleSegmentBase) segmentBase);
      } else if (isStartTag(xpp, "SegmentList")) {
        segmentBase = parseSegmentList(xpp, baseUrl, (SegmentList) segmentBase, periodDurationMs);
      } else if (isStartTag(xpp, "SegmentTemplate")) {
        segmentBase = parseSegmentTemplate(xpp, baseUrl, (SegmentTemplate) segmentBase,
            periodDurationMs);
      }
    } while (!isEndTag(xpp, "Representation"));

    Format format = buildFormat(id, mimeType, width, height, numChannels, audioSamplingRate,
        bandwidth, language);
    return buildRepresentation(periodStartMs, periodDurationMs, contentId, -1, format,
        segmentBase);
  }

  protected Format buildFormat(String id, String mimeType, int width, int height, int numChannels,
      int audioSamplingRate, int bandwidth, String language) {
    return new Format(id, mimeType, width, height, numChannels, audioSamplingRate,
        bandwidth, language);
  }

  protected Representation buildRepresentation(long periodStartMs, long periodDurationMs,
      String contentId, int revisionId, Format format, SegmentBase segmentBase) {
    return Representation.newInstance(periodStartMs, periodDurationMs, contentId, revisionId,
        format, segmentBase);
  }

  // SegmentBase, SegmentList and SegmentTemplate parsing.

  protected SingleSegmentBase parseSegmentBase(XmlPullParser xpp, Uri baseUrl,
      SingleSegmentBase parent) throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);

    long indexStart = parent != null ? parent.indexStart : 0;
    long indexLength = parent != null ? parent.indexLength : -1;
    String indexRangeText = xpp.getAttributeValue(null, "indexRange");
    if (indexRangeText != null) {
      String[] indexRange = indexRangeText.split("-");
      indexStart = Long.parseLong(indexRange[0]);
      indexLength = Long.parseLong(indexRange[1]) - indexStart + 1;
    }

    RangedUri initialization = parent != null ? parent.initialization : null;
    do {
      xpp.next();
      if (isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp, baseUrl);
      }
    } while (!isEndTag(xpp, "SegmentBase"));

    return buildSingleSegmentBase(initialization, timescale, presentationTimeOffset, baseUrl,
        indexStart, indexLength);
  }

  protected SingleSegmentBase buildSingleSegmentBase(RangedUri initialization, long timescale,
      long presentationTimeOffset, Uri baseUrl, long indexStart, long indexLength) {
    return new SingleSegmentBase(initialization, timescale, presentationTimeOffset, baseUrl,
        indexStart, indexLength);
  }

  protected SegmentList parseSegmentList(XmlPullParser xpp, Uri baseUrl, SegmentList parent,
      long periodDuration) throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);
    long duration = parseLong(xpp, "duration", parent != null ? parent.duration : -1);
    int startNumber = parseInt(xpp, "startNumber", parent != null ? parent.startNumber : 1);

    RangedUri initialization = null;
    List<SegmentTimelineElement> timeline = null;
    List<RangedUri> segments = null;

    do {
      xpp.next();
      if (isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp, baseUrl);
      } else if (isStartTag(xpp, "SegmentTimeline")) {
        timeline = parseSegmentTimeline(xpp);
      } else if (isStartTag(xpp, "SegmentURL")) {
        if (segments == null) {
          segments = new ArrayList<RangedUri>();
        }
        segments.add(parseSegmentUrl(xpp, baseUrl));
      }
    } while (!isEndTag(xpp, "SegmentList"));

    if (parent != null) {
      initialization = initialization != null ? initialization : parent.initialization;
      timeline = timeline != null ? timeline : parent.segmentTimeline;
      segments = segments != null ? segments : parent.mediaSegments;
    }

    return buildSegmentList(initialization, timescale, presentationTimeOffset, periodDuration,
        startNumber, duration, timeline, segments);
  }

  protected SegmentList buildSegmentList(RangedUri initialization, long timescale,
      long presentationTimeOffset, long periodDuration, int startNumber, long duration,
      List<SegmentTimelineElement> timeline, List<RangedUri> segments) {
    return new SegmentList(initialization, timescale, presentationTimeOffset, periodDuration,
        startNumber, duration, timeline, segments);
  }

  protected SegmentTemplate parseSegmentTemplate(XmlPullParser xpp, Uri baseUrl,
      SegmentTemplate parent, long periodDuration) throws XmlPullParserException, IOException {

    long timescale = parseLong(xpp, "timescale", parent != null ? parent.timescale : 1);
    long presentationTimeOffset = parseLong(xpp, "presentationTimeOffset",
        parent != null ? parent.presentationTimeOffset : 0);
    long duration = parseLong(xpp, "duration", parent != null ? parent.duration : -1);
    int startNumber = parseInt(xpp, "startNumber", parent != null ? parent.startNumber : 1);
    UrlTemplate mediaTemplate = parseUrlTemplate(xpp, "media",
        parent != null ? parent.mediaTemplate : null);
    UrlTemplate initializationTemplate = parseUrlTemplate(xpp, "initialization",
        parent != null ? parent.initializationTemplate : null);

    RangedUri initialization = null;
    List<SegmentTimelineElement> timeline = null;

    do {
      xpp.next();
      if (isStartTag(xpp, "Initialization")) {
        initialization = parseInitialization(xpp, baseUrl);
      } else if (isStartTag(xpp, "SegmentTimeline")) {
        timeline = parseSegmentTimeline(xpp);
      }
    } while (!isEndTag(xpp, "SegmentTemplate"));

    if (parent != null) {
      initialization = initialization != null ? initialization : parent.initialization;
      timeline = timeline != null ? timeline : parent.segmentTimeline;
    }

    return buildSegmentTemplate(initialization, timescale, presentationTimeOffset, periodDuration,
        startNumber, duration, timeline, initializationTemplate, mediaTemplate, baseUrl);
  }

  protected SegmentTemplate buildSegmentTemplate(RangedUri initialization, long timescale,
      long presentationTimeOffset, long periodDuration, int startNumber, long duration,
      List<SegmentTimelineElement> timeline, UrlTemplate initializationTemplate,
      UrlTemplate mediaTemplate, Uri baseUrl) {
    return new SegmentTemplate(initialization, timescale, presentationTimeOffset, periodDuration,
        startNumber, duration, timeline, initializationTemplate, mediaTemplate, baseUrl);
  }

  protected List<SegmentTimelineElement> parseSegmentTimeline(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    List<SegmentTimelineElement> segmentTimeline = new ArrayList<SegmentTimelineElement>();
    long elapsedTime = 0;
    do {
      xpp.next();
      if (isStartTag(xpp, "S")) {
        elapsedTime = parseLong(xpp, "t", elapsedTime);
        long duration = parseLong(xpp, "d");
        int count = 1 + parseInt(xpp, "r", 0);
        for (int i = 0; i < count; i++) {
          segmentTimeline.add(buildSegmentTimelineElement(elapsedTime, duration));
          elapsedTime += duration;
        }
      }
    } while (!isEndTag(xpp, "SegmentTimeline"));
    return segmentTimeline;
  }

  protected SegmentTimelineElement buildSegmentTimelineElement(long elapsedTime, long duration) {
    return new SegmentTimelineElement(elapsedTime, duration);
  }

  protected UrlTemplate parseUrlTemplate(XmlPullParser xpp, String name,
      UrlTemplate defaultValue) {
    String valueString = xpp.getAttributeValue(null, name);
    if (valueString != null) {
      return UrlTemplate.compile(valueString);
    }
    return defaultValue;
  }

  protected RangedUri parseInitialization(XmlPullParser xpp, Uri baseUrl) {
    return parseRangedUrl(xpp, baseUrl, "sourceURL", "range");
  }

  protected RangedUri parseSegmentUrl(XmlPullParser xpp, Uri baseUrl) {
    return parseRangedUrl(xpp, baseUrl, "media", "mediaRange");
  }

  protected RangedUri parseRangedUrl(XmlPullParser xpp, Uri baseUrl, String urlAttribute,
      String rangeAttribute) {
    String urlText = xpp.getAttributeValue(null, urlAttribute);
    long rangeStart = 0;
    long rangeLength = -1;
    String rangeText = xpp.getAttributeValue(null, rangeAttribute);
    if (rangeText != null) {
      String[] rangeTextArray = rangeText.split("-");
      rangeStart = Long.parseLong(rangeTextArray[0]);
      rangeLength = Long.parseLong(rangeTextArray[1]) - rangeStart + 1;
    }
    return buildRangedUri(baseUrl, urlText, rangeStart, rangeLength);
  }

  protected RangedUri buildRangedUri(Uri baseUrl, String urlText, long rangeStart,
      long rangeLength) {
    return new RangedUri(baseUrl, urlText, rangeStart, rangeLength);
  }

  // Utility methods.

  protected static boolean isEndTag(XmlPullParser xpp, String name) throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.END_TAG && name.equals(xpp.getName());
  }

  protected static boolean isStartTag(XmlPullParser xpp, String name)
      throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.START_TAG && name.equals(xpp.getName());
  }

  protected static boolean isStartTag(XmlPullParser xpp) throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.START_TAG;
  }

  protected static long parseDuration(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    if (value == null) {
      return defaultValue;
    } else {
      return Util.parseXsDuration(value);
    }
  }

  protected static long parseDateTime(XmlPullParser xpp, String name, long defaultValue)
      throws ParseException {
    String value = xpp.getAttributeValue(null, name);
    if (value == null) {
      return defaultValue;
    } else {
      return Util.parseXsDateTime(value);
    }
  }

  protected static Uri parseBaseUrl(XmlPullParser xpp, Uri parentBaseUrl)
      throws XmlPullParserException, IOException {
    xpp.next();
    String newBaseUrlText = xpp.getText();
    Uri newBaseUri = Uri.parse(newBaseUrlText);
    if (!newBaseUri.isAbsolute()) {
      newBaseUri = Uri.withAppendedPath(parentBaseUrl, newBaseUrlText);
    }
    return newBaseUri;
  }

  protected static int parseInt(XmlPullParser xpp, String name) {
    return parseInt(xpp, name, -1);
  }

  protected static int parseInt(XmlPullParser xpp, String name, int defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Integer.parseInt(value);
  }

  protected static long parseLong(XmlPullParser xpp, String name) {
    return parseLong(xpp, name, -1);
  }

  protected static long parseLong(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Long.parseLong(value);
  }

  protected static String parseString(XmlPullParser xpp, String name, String defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : value;
  }

}
