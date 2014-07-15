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
import com.google.android.exoplayer.upstream.DataSpec;
import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.MimeTypes;

import android.net.Uri;
import android.text.TextUtils;

import org.xml.sax.helpers.DefaultHandler;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser of media presentation description files.
 */
/*
 * TODO: Parse representation base attributes at multiple levels, and normalize the resulting
 * datastructure.
 * TODO: Decide how best to represent missing integer/double/long attributes.
 */
public class MediaPresentationDescriptionParser extends DefaultHandler {

  // Note: Does not support the date part of ISO 8601
  private static final Pattern DURATION =
      Pattern.compile("^PT(([0-9]*)H)?(([0-9]*)M)?(([0-9.]*)S)?$");

  private final XmlPullParserFactory xmlParserFactory;

  public MediaPresentationDescriptionParser() {
    try {
      xmlParserFactory = XmlPullParserFactory.newInstance();
    } catch (XmlPullParserException e) {
      throw new RuntimeException("Couldn't create XmlPullParserFactory instance", e);
    }
  }

  /**
   * Parses a manifest from the provided {@link InputStream}.
   *
   * @param inputStream The stream from which to parse the manifest.
   * @param inputEncoding The encoding of the input.
   * @param contentId The content id of the media.
   * @param baseUrl The url that any relative urls defined within the manifest are relative to.
   * @return The parsed manifest.
   * @throws IOException If a problem occurred reading from the stream.
   * @throws XmlPullParserException If a problem occurred parsing the stream as xml.
   * @throws ParserException If a problem occurred parsing the xml as a DASH mpd.
   */
  public MediaPresentationDescription parseMediaPresentationDescription(InputStream inputStream,
      String inputEncoding, String contentId, Uri baseUrl) throws XmlPullParserException,
      IOException, ParserException {
    XmlPullParser xpp = xmlParserFactory.newPullParser();
    xpp.setInput(inputStream, inputEncoding);
    int eventType = xpp.next();
    if (eventType != XmlPullParser.START_TAG || !"MPD".equals(xpp.getName())) {
      throw new ParserException(
          "inputStream does not contain a valid media presentation description");
    }
    return parseMediaPresentationDescription(xpp, contentId, baseUrl);
  }

  private MediaPresentationDescription parseMediaPresentationDescription(XmlPullParser xpp,
      String contentId, Uri parentBaseUrl) throws XmlPullParserException, IOException {
    Uri baseUrl = parentBaseUrl;
    long duration = parseDurationMs(xpp, "mediaPresentationDuration");
    long minBufferTime = parseDurationMs(xpp, "minBufferTime");
    String typeString = xpp.getAttributeValue(null, "type");
    boolean dynamic = (typeString != null) ? typeString.equals("dynamic") : false;
    long minUpdateTime = (dynamic) ? parseDurationMs(xpp, "minimumUpdatePeriod", -1) : -1;

    List<Period> periods = new ArrayList<Period>();
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        baseUrl = parseBaseUrl(xpp, parentBaseUrl);
      } else if (isStartTag(xpp, "Period")) {
        periods.add(parsePeriod(xpp, contentId, baseUrl, duration));
      }
    } while (!isEndTag(xpp, "MPD"));

    return new MediaPresentationDescription(duration, minBufferTime, dynamic, minUpdateTime,
        periods);
  }

  private Period parsePeriod(XmlPullParser xpp, String contentId, Uri parentBaseUrl,
      long mediaPresentationDuration) throws XmlPullParserException, IOException {
    Uri baseUrl = parentBaseUrl;
    String id = xpp.getAttributeValue(null, "id");
    long start = parseDurationMs(xpp, "start", 0);
    long duration = parseDurationMs(xpp, "duration", mediaPresentationDuration);

    List<AdaptationSet> adaptationSets = new ArrayList<AdaptationSet>();
    List<Segment.Timeline> segmentTimelineList = null;
    int segmentStartNumber = 0;
    int segmentTimescale = 0;
    long presentationTimeOffset = 0;
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        baseUrl = parseBaseUrl(xpp, parentBaseUrl);
      } else if (isStartTag(xpp, "AdaptationSet")) {
        adaptationSets.add(parseAdaptationSet(xpp, contentId, baseUrl, start, duration,
            segmentTimelineList));
      } else if (isStartTag(xpp, "SegmentList")) {
        segmentStartNumber = parseInt(xpp, "startNumber");
        segmentTimescale = parseInt(xpp, "timescale");
        presentationTimeOffset = parseLong(xpp, "presentationTimeOffset", 0);
        segmentTimelineList = parsePeriodSegmentList(xpp, segmentStartNumber);
      }
    } while (!isEndTag(xpp, "Period"));

    return new Period(id, start, duration, adaptationSets, segmentTimelineList,
        segmentStartNumber, segmentTimescale, presentationTimeOffset);
  }

  private List<Segment.Timeline> parsePeriodSegmentList(
      XmlPullParser xpp, long segmentStartNumber) throws XmlPullParserException, IOException {
    List<Segment.Timeline> segmentTimelineList = new ArrayList<Segment.Timeline>();

    do {
      xpp.next();
      if (isStartTag(xpp, "SegmentTimeline")) {
        do {
          xpp.next();
          if (isStartTag(xpp, "S")) {
            long duration = parseLong(xpp, "d");
            segmentTimelineList.add(new Segment.Timeline(segmentStartNumber, duration));
            segmentStartNumber++;
          }
        } while (!isEndTag(xpp, "SegmentTimeline"));
      }
    } while (!isEndTag(xpp, "SegmentList"));

    return segmentTimelineList;
  }

  private AdaptationSet parseAdaptationSet(XmlPullParser xpp, String contentId, Uri parentBaseUrl,
      long periodStart, long periodDuration, List<Segment.Timeline> segmentTimelineList)
      throws XmlPullParserException, IOException {
    Uri baseUrl = parentBaseUrl;
    int id = -1;

    // TODO: Correctly handle other common attributes and elements. See 23009-1 Table 9.
    String mimeType = xpp.getAttributeValue(null, "mimeType");
    int contentType = parseAdaptationSetTypeFromMimeType(mimeType);

    List<ContentProtection> contentProtections = null;
    List<Representation> representations = new ArrayList<Representation>();
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        baseUrl = parseBaseUrl(xpp, parentBaseUrl);
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
        Representation representation = parseRepresentation(xpp, contentId, baseUrl, periodStart,
            periodDuration, mimeType, segmentTimelineList);
        contentType = checkAdaptationSetTypeConsistency(contentType,
            parseAdaptationSetTypeFromMimeType(representation.format.mimeType));
        representations.add(representation);
      }
    } while (!isEndTag(xpp, "AdaptationSet"));

    return new AdaptationSet(id, contentType, representations, contentProtections);
  }

  /**
   * Parses a ContentProtection element.
   *
   * @throws XmlPullParserException If an error occurs parsing the element.
   * @throws IOException If an error occurs reading the element.
   **/
  protected ContentProtection parseContentProtection(XmlPullParser xpp)
      throws XmlPullParserException, IOException {
    String schemeUriId = xpp.getAttributeValue(null, "schemeUriId");
    return new ContentProtection(schemeUriId, null);
  }

  private Representation parseRepresentation(XmlPullParser xpp, String contentId, Uri parentBaseUrl,
      long periodStart, long periodDuration, String parentMimeType,
      List<Segment.Timeline> segmentTimelineList) throws XmlPullParserException, IOException {
    Uri baseUrl = parentBaseUrl;
    String id = xpp.getAttributeValue(null, "id");
    int bandwidth = parseInt(xpp, "bandwidth");
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate");
    int width = parseInt(xpp, "width");
    int height = parseInt(xpp, "height");

    String mimeType = xpp.getAttributeValue(null, "mimeType");
    if (mimeType == null) {
      mimeType = parentMimeType;
    }

    long indexStart = -1;
    long indexEnd = -1;
    long initializationStart = -1;
    long initializationEnd = -1;
    int numChannels = -1;
    List<Segment> segmentList = null;
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        baseUrl = parseBaseUrl(xpp, parentBaseUrl);
      } else if (isStartTag(xpp, "AudioChannelConfiguration")) {
        numChannels = Integer.parseInt(xpp.getAttributeValue(null, "value"));
      } else if (isStartTag(xpp, "SegmentBase")) {
        String[] indexRange = xpp.getAttributeValue(null, "indexRange").split("-");
        indexStart = Long.parseLong(indexRange[0]);
        indexEnd = Long.parseLong(indexRange[1]);
      } else if (isStartTag(xpp, "SegmentList")) {
        segmentList = parseRepresentationSegmentList(xpp, segmentTimelineList);
      } else if (isStartTag(xpp, "Initialization")) {
        String[] indexRange = xpp.getAttributeValue(null, "range").split("-");
        initializationStart = Long.parseLong(indexRange[0]);
        initializationEnd = Long.parseLong(indexRange[1]);
      }
    } while (!isEndTag(xpp, "Representation"));

    Format format = new Format(id, mimeType, width, height, numChannels, audioSamplingRate,
        bandwidth);
    if (segmentList == null) {
      return new Representation(contentId, -1, format, baseUrl, DataSpec.LENGTH_UNBOUNDED,
          initializationStart, initializationEnd, indexStart, indexEnd, periodStart,
          periodDuration);
    } else {
      return new SegmentedRepresentation(contentId, format, baseUrl, initializationStart,
          initializationEnd, indexStart, indexEnd, periodStart, periodDuration, segmentList);
    }
  }

  private List<Segment> parseRepresentationSegmentList(XmlPullParser xpp,
      List<Segment.Timeline> segmentTimelineList) throws XmlPullParserException, IOException {
    List<Segment> segmentList = new ArrayList<Segment>();
    int i = 0;

    do {
      xpp.next();
      if (isStartTag(xpp, "Initialization")) {
        String url = xpp.getAttributeValue(null, "sourceURL");
        String[] indexRange = xpp.getAttributeValue(null, "range").split("-");
        long initializationStart = Long.parseLong(indexRange[0]);
        long initializationEnd = Long.parseLong(indexRange[1]);
        segmentList.add(new Segment.Initialization(url, initializationStart, initializationEnd));
      } else if (isStartTag(xpp, "SegmentURL")) {
        String url = xpp.getAttributeValue(null, "media");
        String mediaRange = xpp.getAttributeValue(null, "mediaRange");
        long sequenceNumber = segmentTimelineList.get(i).sequenceNumber;
        long duration = segmentTimelineList.get(i).duration;
        i++;
        if (mediaRange != null) {
          String[] mediaRangeArray = xpp.getAttributeValue(null, "mediaRange").split("-");
          long mediaStart = Long.parseLong(mediaRangeArray[0]);
          segmentList.add(new Segment.Media(url, mediaStart, sequenceNumber, duration));
        } else {
          segmentList.add(new Segment.Media(url, sequenceNumber, duration));
        }
      }
    } while (!isEndTag(xpp, "SegmentList"));

    return segmentList;
  }

  protected static boolean isEndTag(XmlPullParser xpp, String name) throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.END_TAG && name.equals(xpp.getName());
  }

  protected static boolean isStartTag(XmlPullParser xpp, String name)
      throws XmlPullParserException {
    return xpp.getEventType() == XmlPullParser.START_TAG && name.equals(xpp.getName());
  }

  protected static int parseInt(XmlPullParser xpp, String name) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? -1 : Integer.parseInt(value);
  }

  protected static long parseLong(XmlPullParser xpp, String name) {
    return parseLong(xpp, name, -1);
  }

  protected static long parseLong(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    return value == null ? defaultValue : Long.parseLong(value);
  }

  private long parseDurationMs(XmlPullParser xpp, String name) {
    return parseDurationMs(xpp, name, -1);
  }

  private static long parseDurationMs(XmlPullParser xpp, String name, long defaultValue) {
    String value = xpp.getAttributeValue(null, name);
    if (value != null) {
      Matcher matcher = DURATION.matcher(value);
      if (matcher.matches()) {
        String hours = matcher.group(2);
        double durationSeconds = (hours != null) ? Double.parseDouble(hours) * 3600 : 0;
        String minutes = matcher.group(4);
        durationSeconds += (minutes != null) ? Double.parseDouble(minutes) * 60 : 0;
        String seconds = matcher.group(6);
        durationSeconds += (seconds != null) ? Double.parseDouble(seconds) : 0;
        return (long) (durationSeconds * 1000);
      } else {
        return (long) (Double.parseDouble(value) * 3600 * 1000);
      }
    }
    return defaultValue;
  }

  private static Uri parseBaseUrl(XmlPullParser xpp, Uri parentBaseUrl)
      throws XmlPullParserException, IOException {
    xpp.next();
    String newBaseUrlText = xpp.getText();
    Uri newBaseUri = Uri.parse(newBaseUrlText);
    if (newBaseUri.isAbsolute()) {
      return newBaseUri;
    } else {
      return parentBaseUrl.buildUpon().appendEncodedPath(newBaseUrlText).build();
    }
  }

  private static int parseAdaptationSetType(String contentType) {
    return TextUtils.isEmpty(contentType) ? AdaptationSet.TYPE_UNKNOWN
        : MimeTypes.BASE_TYPE_AUDIO.equals(contentType) ? AdaptationSet.TYPE_AUDIO
        : MimeTypes.BASE_TYPE_VIDEO.equals(contentType) ? AdaptationSet.TYPE_VIDEO
        : MimeTypes.BASE_TYPE_TEXT.equals(contentType) ? AdaptationSet.TYPE_TEXT
        : AdaptationSet.TYPE_UNKNOWN;
  }

  private static int parseAdaptationSetTypeFromMimeType(String mimeType) {
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
  private static int checkAdaptationSetTypeConsistency(int firstType, int secondType) {
    if (firstType == AdaptationSet.TYPE_UNKNOWN) {
      return secondType;
    } else if (secondType == AdaptationSet.TYPE_UNKNOWN) {
      return firstType;
    } else {
      Assertions.checkState(firstType == secondType);
      return firstType;
    }
  }

}
