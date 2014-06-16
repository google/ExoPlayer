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
package com.google.android.exoplayer.smoothstreaming;

import com.google.android.exoplayer.ParserException;
import com.google.android.exoplayer.parser.mp4.CodecSpecificDataUtil;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.ProtectionElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.StreamElement;
import com.google.android.exoplayer.smoothstreaming.SmoothStreamingManifest.TrackElement;
import com.google.android.exoplayer.util.Assertions;

import android.util.Base64;
import android.util.Pair;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Parses SmoothStreaming client manifests.
 *
 * @see <a href="http://msdn.microsoft.com/en-us/library/ee673436(v=vs.90).aspx">
 * IIS Smooth Streaming Client Manifest Format</a>
 */
public class SmoothStreamingManifestParser {

  private final XmlPullParserFactory xmlParserFactory;

  public SmoothStreamingManifestParser() {
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
   * @return The parsed manifest.
   * @throws IOException If a problem occurred reading from the stream.
   * @throws XmlPullParserException If a problem occurred parsing the stream as xml.
   * @throws ParserException If a problem occurred parsing the xml as a smooth streaming manifest.
   */
  public SmoothStreamingManifest parse(InputStream inputStream, String inputEncoding) throws
      XmlPullParserException, IOException, ParserException {
    XmlPullParser xmlParser = xmlParserFactory.newPullParser();
    xmlParser.setInput(inputStream, inputEncoding);
    SmoothStreamMediaParser smoothStreamMediaParser = new SmoothStreamMediaParser(null);
    return (SmoothStreamingManifest) smoothStreamMediaParser.parse(xmlParser);
  }

  /**
   * Thrown if a required field is missing.
   */
  public static class MissingFieldException extends ParserException {

    public MissingFieldException(String fieldName) {
      super("Missing required field: " + fieldName);
    }

  }

  /**
   * A base class for parsers that parse components of a smooth streaming manifest.
   */
  private static abstract class ElementParser {

    private final String tag;

    private final ElementParser parent;
    private final List<Pair<String, Object>> normalizedAttributes;

    public ElementParser(String tag, ElementParser parent) {
      this.tag = tag;
      this.parent = parent;
      this.normalizedAttributes = new LinkedList<Pair<String, Object>>();
    }

    public final Object parse(XmlPullParser xmlParser) throws XmlPullParserException, IOException,
        ParserException {
      String tagName;
      boolean foundStartTag = false;
      while (true) {
        int eventType = xmlParser.getEventType();
        switch (eventType) {
          case XmlPullParser.START_TAG:
            tagName = xmlParser.getName();
            if (tag.equals(tagName)) {
              foundStartTag = true;
              parseStartTag(xmlParser);
            } else if (foundStartTag) {
              if (handleChildInline(tagName)) {
                parseStartTag(xmlParser);
              } else {
                addChild(newChildParser(this, tagName).parse(xmlParser));
              }
            }
            break;
          case XmlPullParser.TEXT:
            if (foundStartTag) {
              parseText(xmlParser);
            }
            break;
          case XmlPullParser.END_TAG:
            if (foundStartTag) {
              tagName = xmlParser.getName();
              parseEndTag(xmlParser);
              if (!handleChildInline(tagName)) {
                return build();
              }
            }
            break;
          case XmlPullParser.END_DOCUMENT:
            return null;
          default:
            // Do nothing.
            break;
        }
        xmlParser.next();
      }
    }

    private ElementParser newChildParser(ElementParser parent, String name) {
      if (TrackElementParser.TAG.equals(name)) {
        return new TrackElementParser(parent);
      } else if (ProtectionElementParser.TAG.equals(name)) {
        return new ProtectionElementParser(parent);
      } else if (StreamElementParser.TAG.equals(name)) {
        return new StreamElementParser(parent);
      }
      return null;
    }

    /**
     * Stash an attribute that may be normalized at this level. In other words, an attribute that
     * may have been pulled up from the child elements because its value was the same in all
     * children.
     * <p>
     * Stashing an attribute allows child element parsers to retrieve the values of normalized
     * attributes using {@link #getNormalizedAttribute(String)}.
     *
     * @param key The name of the attribute.
     * @param value The value of the attribute.
     */
    protected final void putNormalizedAttribute(String key, Object value) {
      normalizedAttributes.add(Pair.create(key, value));
    }

    /**
     * Attempt to retrieve a stashed normalized attribute. If there is no stashed attribute with
     * the provided name, the parent element parser will be queried, and so on up the chain.
     *
     * @param key The name of the attribute.
     * @return The stashed value, or null if the attribute was not be found.
     */
    protected final Object getNormalizedAttribute(String key) {
      for (int i = 0; i < normalizedAttributes.size(); i++) {
        Pair<String, Object> pair = normalizedAttributes.get(i);
        if (pair.first.equals(key)) {
          return pair.second;
        }
      }
      return parent == null ? null : parent.getNormalizedAttribute(key);
    }

    /**
     * Whether this {@link ElementParser} parses a child element inline.
     *
     * @param tagName The name of the child element.
     * @return Whether the child is parsed inline.
     */
    protected boolean handleChildInline(String tagName) {
      return false;
    }

    /**
     * @param xmlParser The underlying {@link XmlPullParser}
     * @throws ParserException
     */
    protected void parseStartTag(XmlPullParser xmlParser) throws ParserException {
      // Do nothing.
    }

    /**
     * @param xmlParser The underlying {@link XmlPullParser}
     * @throws ParserException
     */
    protected void parseText(XmlPullParser xmlParser) throws ParserException {
      // Do nothing.
    }

    /**
     * @param xmlParser The underlying {@link XmlPullParser}
     * @throws ParserException
     */
    protected void parseEndTag(XmlPullParser xmlParser) throws ParserException {
      // Do nothing.
    }

    /**
     * @param parsedChild A parsed child object.
     */
    protected void addChild(Object parsedChild) {
      // Do nothing.
    }

    protected abstract Object build();

    protected final String parseRequiredString(XmlPullParser parser, String key)
        throws MissingFieldException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        return value;
      } else {
        throw new MissingFieldException(key);
      }
    }

    protected final int parseInt(XmlPullParser parser, String key, int defaultValue)
        throws ParserException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        try {
          return Integer.parseInt(value);
        } catch (NumberFormatException e) {
          throw new ParserException(e);
        }
      } else {
        return defaultValue;
      }
    }

    protected final int parseRequiredInt(XmlPullParser parser, String key) throws ParserException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        try {
          return Integer.parseInt(value);
        } catch (NumberFormatException e) {
          throw new ParserException(e);
        }
      } else {
        throw new MissingFieldException(key);
      }
    }

    protected final long parseLong(XmlPullParser parser, String key, long defaultValue)
        throws ParserException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        try {
          return Long.parseLong(value);
        } catch (NumberFormatException e) {
          throw new ParserException(e);
        }
      } else {
        return defaultValue;
      }
    }

    protected final long parseRequiredLong(XmlPullParser parser, String key)
        throws ParserException {
      String value = parser.getAttributeValue(null, key);
      if (value != null) {
        try {
          return Long.parseLong(value);
        } catch (NumberFormatException e) {
          throw new ParserException(e);
        }
      } else {
        throw new MissingFieldException(key);
      }
    }

  }

  private static class SmoothStreamMediaParser extends ElementParser {

    public static final String TAG = "SmoothStreamingMedia";

    private static final String KEY_MAJOR_VERSION = "MajorVersion";
    private static final String KEY_MINOR_VERSION = "MinorVersion";
    private static final String KEY_TIME_SCALE = "TimeScale";
    private static final String KEY_DURATION = "Duration";
    private static final String KEY_LOOKAHEAD_COUNT = "LookaheadCount";

    private int majorVersion;
    private int minorVersion;
    private long timeScale;
    private long duration;
    private int lookAheadCount;
    private ProtectionElement protectionElement;
    private List<StreamElement> streamElements;

    public SmoothStreamMediaParser(ElementParser parent) {
      super(TAG, parent);
      lookAheadCount = -1;
      protectionElement = null;
      streamElements = new LinkedList<StreamElement>();
    }

    @Override
    public void parseStartTag(XmlPullParser parser) throws ParserException {
      majorVersion = parseRequiredInt(parser, KEY_MAJOR_VERSION);
      minorVersion = parseRequiredInt(parser, KEY_MINOR_VERSION);
      timeScale = parseLong(parser, KEY_TIME_SCALE, 10000000L);
      duration = parseRequiredLong(parser, KEY_DURATION);
      lookAheadCount = parseInt(parser, KEY_LOOKAHEAD_COUNT, -1);
      putNormalizedAttribute(KEY_TIME_SCALE, timeScale);
    }

    @Override
    public void addChild(Object child) {
      if (child instanceof StreamElement) {
        streamElements.add((StreamElement) child);
      } else if (child instanceof ProtectionElement) {
        Assertions.checkState(protectionElement == null);
        protectionElement = (ProtectionElement) child;
      }
    }

    @Override
    public Object build() {
      StreamElement[] streamElementArray = new StreamElement[streamElements.size()];
      streamElements.toArray(streamElementArray);
      return new SmoothStreamingManifest(majorVersion, minorVersion, timeScale, duration,
          lookAheadCount, protectionElement, streamElementArray);
    }

  }

  private static class ProtectionElementParser extends ElementParser {

    public static final String TAG = "Protection";
    public static final String TAG_PROTECTION_HEADER = "ProtectionHeader";

    public static final String KEY_SYSTEM_ID = "SystemID";

    private UUID uuid;
    private byte[] initData;

    public ProtectionElementParser(ElementParser parent) {
      super(TAG, parent);
    }

    @Override
    public boolean handleChildInline(String tag) {
      return TAG_PROTECTION_HEADER.equals(tag);
    }

    @Override
    public void parseStartTag(XmlPullParser parser) {
      if (!TAG_PROTECTION_HEADER.equals(parser.getName())) {
        return;
      }
      String uuidString = parser.getAttributeValue(null, KEY_SYSTEM_ID);
      uuid = UUID.fromString(uuidString);
    }

    @Override
    public void parseText(XmlPullParser parser) {
      initData = Base64.decode(parser.getText(), Base64.DEFAULT);
    }

    @Override
    public Object build() {
      return new ProtectionElement(uuid, initData);
    }

  }

  private static class StreamElementParser extends ElementParser {

    public static final String TAG = "StreamIndex";
    private static final String TAG_STREAM_FRAGMENT = "c";

    private static final String KEY_TYPE = "Type";
    private static final String KEY_TYPE_AUDIO = "audio";
    private static final String KEY_TYPE_VIDEO = "video";
    private static final String KEY_TYPE_TEXT = "text";
    private static final String KEY_SUB_TYPE = "Subtype";
    private static final String KEY_NAME = "Name";
    private static final String KEY_CHUNKS = "Chunks";
    private static final String KEY_QUALITY_LEVELS = "QualityLevels";
    private static final String KEY_URL = "Url";
    private static final String KEY_MAX_WIDTH = "MaxWidth";
    private static final String KEY_MAX_HEIGHT = "MaxHeight";
    private static final String KEY_DISPLAY_WIDTH = "DisplayWidth";
    private static final String KEY_DISPLAY_HEIGHT = "DisplayHeight";
    private static final String KEY_LANGUAGE = "Language";
    private static final String KEY_TIME_SCALE = "TimeScale";

    private static final String KEY_FRAGMENT_DURATION = "d";
    private static final String KEY_FRAGMENT_START_TIME = "t";

    private final List<TrackElement> tracks;

    private int type;
    private String subType;
    private long timeScale;
    private String name;
    private int qualityLevels;
    private String url;
    private int maxWidth;
    private int maxHeight;
    private int displayWidth;
    private int displayHeight;
    private String language;
    private long[] startTimes;

    private int chunkIndex;
    private long previousChunkDuration;

    public StreamElementParser(ElementParser parent) {
      super(TAG, parent);
      tracks = new LinkedList<TrackElement>();
    }

    @Override
    public boolean handleChildInline(String tag) {
      return TAG_STREAM_FRAGMENT.equals(tag);
    }

    @Override
    public void parseStartTag(XmlPullParser parser) throws ParserException {
      if (TAG_STREAM_FRAGMENT.equals(parser.getName())) {
        parseStreamFragmentStartTag(parser);
      } else {
        parseStreamElementStartTag(parser);
      }
    }

    private void parseStreamFragmentStartTag(XmlPullParser parser) throws ParserException {
      startTimes[chunkIndex] = parseLong(parser, KEY_FRAGMENT_START_TIME, -1L);
      if (startTimes[chunkIndex] == -1L) {
        if (chunkIndex == 0) {
          // Assume the track starts at t = 0.
          startTimes[chunkIndex] = 0;
        } else if (previousChunkDuration != -1L) {
          // Infer the start time from the previous chunk's start time and duration.
          startTimes[chunkIndex] = startTimes[chunkIndex - 1] + previousChunkDuration;
        } else {
          // We don't have the start time, and we're unable to infer it.
          throw new ParserException("Unable to infer start time");
        }
      }
      previousChunkDuration = parseLong(parser, KEY_FRAGMENT_DURATION, -1L);
      chunkIndex++;
    }

    private void parseStreamElementStartTag(XmlPullParser parser) throws ParserException {
      type = parseType(parser);
      putNormalizedAttribute(KEY_TYPE, type);
      if (type == StreamElement.TYPE_TEXT) {
        subType = parseRequiredString(parser, KEY_SUB_TYPE);
      } else {
        subType = parser.getAttributeValue(null, KEY_SUB_TYPE);
      }
      name = parser.getAttributeValue(null, KEY_NAME);
      qualityLevels = parseInt(parser, KEY_QUALITY_LEVELS, -1);
      url = parseRequiredString(parser, KEY_URL);
      maxWidth = parseInt(parser, KEY_MAX_WIDTH, -1);
      maxHeight = parseInt(parser, KEY_MAX_HEIGHT, -1);
      displayWidth = parseInt(parser, KEY_DISPLAY_WIDTH, -1);
      displayHeight = parseInt(parser, KEY_DISPLAY_HEIGHT, -1);
      language = parser.getAttributeValue(null, KEY_LANGUAGE);
      timeScale = parseInt(parser, KEY_TIME_SCALE, -1);
      if (timeScale == -1) {
        timeScale = (Long) getNormalizedAttribute(KEY_TIME_SCALE);
      }
      startTimes = new long[parseRequiredInt(parser, KEY_CHUNKS)];
    }

    private int parseType(XmlPullParser parser) throws ParserException {
      String value = parser.getAttributeValue(null, KEY_TYPE);
      if (value != null) {
        if (KEY_TYPE_AUDIO.equalsIgnoreCase(value)) {
          return StreamElement.TYPE_AUDIO;
        } else if (KEY_TYPE_VIDEO.equalsIgnoreCase(value)) {
          return StreamElement.TYPE_VIDEO;
        } else if (KEY_TYPE_TEXT.equalsIgnoreCase(value)) {
          return StreamElement.TYPE_TEXT;
        } else {
          throw new ParserException("Invalid key value[" + value + "]");
        }
      }
      throw new MissingFieldException(KEY_TYPE);
    }

    @Override
    public void addChild(Object child) {
      if (child instanceof TrackElement) {
        tracks.add((TrackElement) child);
      }
    }

    @Override
    public Object build() {
      TrackElement[] trackElements = new TrackElement[tracks.size()];
      tracks.toArray(trackElements);
      return new StreamElement(type, subType, timeScale, name, qualityLevels, url, maxWidth,
          maxHeight, displayWidth, displayHeight, language, trackElements, startTimes);
    }

  }

  private static class TrackElementParser extends ElementParser {

    public static final String TAG = "QualityLevel";

    private static final String KEY_INDEX = "Index";
    private static final String KEY_BITRATE = "Bitrate";
    private static final String KEY_CODEC_PRIVATE_DATA = "CodecPrivateData";
    private static final String KEY_SAMPLING_RATE = "SamplingRate";
    private static final String KEY_CHANNELS = "Channels";
    private static final String KEY_BITS_PER_SAMPLE = "BitsPerSample";
    private static final String KEY_PACKET_SIZE = "PacketSize";
    private static final String KEY_AUDIO_TAG = "AudioTag";
    private static final String KEY_FOUR_CC = "FourCC";
    private static final String KEY_NAL_UNIT_LENGTH_FIELD = "NALUnitLengthField";
    private static final String KEY_TYPE = "Type";
    private static final String KEY_MAX_WIDTH = "MaxWidth";
    private static final String KEY_MAX_HEIGHT = "MaxHeight";

    private final List<byte[]> csd;

    private int index;
    private int bitrate;
    private String fourCC;
    private int profile;
    private int level;
    private int maxWidth;
    private int maxHeight;
    private int samplingRate;
    private int channels;
    private int packetSize;
    private int audioTag;
    private int bitPerSample;

    private int nalUnitLengthField;
    private String content;

    public TrackElementParser(ElementParser parent) {
      super(TAG, parent);
      this.csd = new LinkedList<byte[]>();
    }

    @Override
    public void parseStartTag(XmlPullParser parser) throws ParserException {
      int type = (Integer) getNormalizedAttribute(KEY_TYPE);
      content = null;
      String value;

      index = parseInt(parser, KEY_INDEX, -1);
      bitrate = parseRequiredInt(parser, KEY_BITRATE);
      nalUnitLengthField = parseInt(parser, KEY_NAL_UNIT_LENGTH_FIELD, 4);

      if (type == StreamElement.TYPE_VIDEO) {
        maxHeight = parseRequiredInt(parser, KEY_MAX_HEIGHT);
        maxWidth = parseRequiredInt(parser, KEY_MAX_WIDTH);
      } else {
        maxHeight = -1;
        maxWidth = -1;
      }

      if (type == StreamElement.TYPE_AUDIO) {
        samplingRate = parseRequiredInt(parser, KEY_SAMPLING_RATE);
        channels = parseRequiredInt(parser, KEY_CHANNELS);
        bitPerSample = parseRequiredInt(parser, KEY_BITS_PER_SAMPLE);
        packetSize = parseRequiredInt(parser, KEY_PACKET_SIZE);
        audioTag = parseRequiredInt(parser, KEY_AUDIO_TAG);
        fourCC = parseRequiredString(parser, KEY_FOUR_CC);
      } else {
        samplingRate = -1;
        channels = -1;
        bitPerSample = -1;
        packetSize = -1;
        audioTag = -1;
        fourCC = parser.getAttributeValue(null, KEY_FOUR_CC);
      }

      value = parser.getAttributeValue(null, KEY_CODEC_PRIVATE_DATA);
      if (value != null && value.length() > 0) {
        byte[] codecPrivateData = hexStringToByteArray(value);
        byte[][] split = CodecSpecificDataUtil.splitNalUnits(codecPrivateData);
        if (split == null) {
          csd.add(codecPrivateData);
        } else {
          for (int i = 0; i < split.length; i++) {
            Pair<Integer, Integer> spsParameters = CodecSpecificDataUtil.parseSpsNalUnit(split[i]);
            if (spsParameters != null) {
              profile = spsParameters.first;
              level = spsParameters.second;
            }
            csd.add(split[i]);
          }
        }
      }
    }

    private byte[] hexStringToByteArray(String hexString) {
      int length = hexString.length();
      byte[] data = new byte[length / 2];
      for (int i = 0; i < data.length; i++) {
        int stringOffset = i * 2;
        data[i] = (byte) ((Character.digit(hexString.charAt(stringOffset), 16) << 4)
            + Character.digit(hexString.charAt(stringOffset + 1), 16));
      }
      return data;
    }

    @Override
    public void parseText(XmlPullParser parser) {
      content = parser.getText();
    }

    @Override
    public Object build() {
      byte[][] csdArray = null;
      if (!csd.isEmpty()) {
        csdArray = new byte[csd.size()][];
        csd.toArray(csdArray);
      }
      return new TrackElement(index, bitrate, fourCC, csdArray, profile, level, maxWidth, maxHeight,
          samplingRate, channels, packetSize, audioTag, bitPerSample, nalUnitLengthField, content);
    }

  }

}
