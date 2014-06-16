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
import com.google.android.exoplayer.util.MimeTypes;

import android.net.Uri;
import android.util.Log;

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

  private static final String TAG = "MediaPresentationDescriptionParser";

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
   * @return The parsed manifest.
   * @throws IOException If a problem occurred reading from the stream.
   * @throws XmlPullParserException If a problem occurred parsing the stream as xml.
   * @throws ParserException If a problem occurred parsing the xml as a DASH mpd.
   */
  public MediaPresentationDescription parseMediaPresentationDescription(InputStream inputStream,
      String inputEncoding, String contentId) throws XmlPullParserException, IOException,
      ParserException {
    XmlPullParser xpp = xmlParserFactory.newPullParser();
    xpp.setInput(inputStream, inputEncoding);
    int eventType = xpp.next();
    if (eventType != XmlPullParser.START_TAG || !"MPD".equals(xpp.getName())) {
      throw new ParserException(
          "inputStream does not contain a valid media presentation description");
    }
    return parseMediaPresentationDescription(xpp, contentId);
  }

  private MediaPresentationDescription parseMediaPresentationDescription(XmlPullParser xpp,
      String contentId) throws XmlPullParserException, IOException {
    long duration = parseDurationMs(xpp, "mediaPresentationDuration");
    long minBufferTime = parseDurationMs(xpp, "minBufferTime");
    String typeString = xpp.getAttributeValue(null, "type");
    boolean dynamic = (typeString != null) ? typeString.equals("dynamic") : false;
    long minUpdateTime = (dynamic) ? parseDurationMs(xpp, "minimumUpdatePeriod", -1) : -1;

    List<Period> periods = new ArrayList<Period>();
    do {
      xpp.next();
      if (isStartTag(xpp, "Period")) {
        periods.add(parsePeriod(xpp, contentId, duration));
      }
    } while (!isEndTag(xpp, "MPD"));

    return new MediaPresentationDescription(duration, minBufferTime, dynamic, minUpdateTime,
        periods);
  }

  private Period parsePeriod(XmlPullParser xpp, String contentId, long mediaPresentationDuration)
      throws XmlPullParserException, IOException {
    int id = parseInt(xpp, "id");
    long start = parseDurationMs(xpp, "start", 0);
    long duration = parseDurationMs(xpp, "duration", mediaPresentationDuration);

    List<AdaptationSet> adaptationSets = new ArrayList<AdaptationSet>();
    List<Segment.Timeline> segmentTimelineList = null;
    int segmentStartNumber = 0;
    int segmentTimescale = 0;
    long presentationTimeOffset = 0;
    do {
      xpp.next();
      if (isStartTag(xpp, "AdaptationSet")) {
        adaptationSets.add(parseAdaptationSet(xpp, contentId, start, duration,
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

  private AdaptationSet parseAdaptationSet(XmlPullParser xpp, String contentId, long periodStart,
      long periodDuration, List<Segment.Timeline> segmentTimelineList)
      throws XmlPullParserException, IOException {
    int id = -1;
    int contentType = AdaptationSet.TYPE_UNKNOWN;

    // TODO: Correctly handle other common attributes and elements. See 23009-1 Table 9.
    String mimeType = xpp.getAttributeValue(null, "mimeType");
    if (mimeType != null) {
      if (MimeTypes.isAudio(mimeType)) {
        contentType = AdaptationSet.TYPE_AUDIO;
      } else if (MimeTypes.isVideo(mimeType)) {
        contentType = AdaptationSet.TYPE_VIDEO;
      } else if (MimeTypes.isText(mimeType)
          || mimeType.equalsIgnoreCase(MimeTypes.APPLICATION_TTML)) {
        contentType = AdaptationSet.TYPE_TEXT;
      }
    }

    List<ContentProtection> contentProtections = null;
    List<Representation> representations = new ArrayList<Representation>();
    do {
      xpp.next();
      if (contentType != AdaptationSet.TYPE_UNKNOWN) {
        if (isStartTag(xpp, "ContentProtection")) {
          if (contentProtections == null) {
            contentProtections = new ArrayList<ContentProtection>();
          }
          contentProtections.add(parseContentProtection(xpp));
        } else if (isStartTag(xpp, "ContentComponent")) {
          id = Integer.parseInt(xpp.getAttributeValue(null, "id"));
          String contentTypeString = xpp.getAttributeValue(null, "contentType");
          contentType = "video".equals(contentTypeString) ? AdaptationSet.TYPE_VIDEO
              : "audio".equals(contentTypeString) ? AdaptationSet.TYPE_AUDIO
              : AdaptationSet.TYPE_UNKNOWN;
        } else if (isStartTag(xpp, "Representation")) {
          representations.add(parseRepresentation(xpp, contentId, periodStart, periodDuration,
              mimeType, segmentTimelineList));
        }
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

  private Representation parseRepresentation(XmlPullParser xpp, String contentId, long periodStart,
      long periodDuration, String parentMimeType, List<Segment.Timeline> segmentTimelineList)
      throws XmlPullParserException, IOException {
    int id;
    try {
      id = parseInt(xpp, "id");
    } catch (NumberFormatException nfe) {
      Log.d(TAG, "Unable to parse id; " + nfe.getMessage());
      // TODO: need a way to generate a unique and stable id; use hashCode for now
      id = xpp.getAttributeValue(null, "id").hashCode();
    }
    int bandwidth = parseInt(xpp, "bandwidth") / 8;
    int audioSamplingRate = parseInt(xpp, "audioSamplingRate");
    int width = parseInt(xpp, "width");
    int height = parseInt(xpp, "height");

    String mimeType = xpp.getAttributeValue(null, "mimeType");
    if (mimeType == null) {
      mimeType = parentMimeType;
    }

    String representationUrl = null;
    long indexStart = -1;
    long indexEnd = -1;
    long initializationStart = -1;
    long initializationEnd = -1;
    int numChannels = -1;
    List<Segment> segmentList = null;
    do {
      xpp.next();
      if (isStartTag(xpp, "BaseURL")) {
        xpp.next();
        representationUrl = xpp.getText();
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

    Uri uri = Uri.parse(representationUrl);
    Format format = new Format(id, mimeType, width, height, numChannels, audioSamplingRate,
        bandwidth);
    if (segmentList == null) {
      return new Representation(contentId, -1, format, uri, DataSpec.LENGTH_UNBOUNDED,
          initializationStart, initializationEnd, indexStart, indexEnd, periodStart,
          periodDuration);
    } else {
      return new SegmentedRepresentation(contentId, format, uri, initializationStart,
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

  private long parseDurationMs(XmlPullParser xpp, String name, long defaultValue) {
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

}
