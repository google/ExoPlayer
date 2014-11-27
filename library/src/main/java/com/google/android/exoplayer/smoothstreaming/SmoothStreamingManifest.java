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

import com.google.android.exoplayer.util.Assertions;
import com.google.android.exoplayer.util.Util;

import android.net.Uri;

import java.util.List;
import java.util.UUID;

/**
 * Represents a SmoothStreaming manifest.
 *
 * @see <a href="http://msdn.microsoft.com/en-us/library/ee673436(v=vs.90).aspx">
 * IIS Smooth Streaming Client Manifest Format</a>
 */
public class SmoothStreamingManifest {

  private static final long MICROS_PER_SECOND = 1000000L;

  public final int majorVersion;
  public final int minorVersion;
  public final long timescale;
  public final int lookAheadCount;
  public final boolean isLive;
  public final ProtectionElement protectionElement;
  public final StreamElement[] streamElements;
  public final long durationUs;
  public final long dvrWindowLengthUs;

  public SmoothStreamingManifest(int majorVersion, int minorVersion, long timescale, long duration,
      long dvrWindowLength, int lookAheadCount, boolean isLive, ProtectionElement protectionElement,
      StreamElement[] streamElements) {
    this.majorVersion = majorVersion;
    this.minorVersion = minorVersion;
    this.timescale = timescale;
    this.lookAheadCount = lookAheadCount;
    this.isLive = isLive;
    this.protectionElement = protectionElement;
    this.streamElements = streamElements;
    dvrWindowLengthUs = Util.scaleLargeTimestamp(dvrWindowLength, MICROS_PER_SECOND, timescale);
    durationUs = Util.scaleLargeTimestamp(duration, MICROS_PER_SECOND, timescale);
  }

  /**
   * Represents a protection element containing a single header.
   */
  public static class ProtectionElement {

    public final UUID uuid;
    public final byte[] data;

    public ProtectionElement(UUID uuid, byte[] data) {
      this.uuid = uuid;
      this.data = data;
    }

  }

  /**
   * Represents a QualityLevel element.
   */
  public static class TrackElement {

    // Required for all
    public final int index;
    public final int bitrate;

    // Audio-video
    public final byte[][] csd;
    public final int profile;
    public final int level;
    public final String mimeType;

    // Video-only
    public final int maxWidth;
    public final int maxHeight;

    // Audio-only
    public final int sampleRate;
    public final int numChannels;
    public final int packetSize;
    public final int audioTag;
    public final int bitPerSample;

    public final int nalUnitLengthField;
    public final String content;

    public TrackElement(int index, int bitrate, String mimeType, byte[][] csd, int profile,
        int level, int maxWidth, int maxHeight, int sampleRate, int channels, int packetSize,
        int audioTag, int bitPerSample, int nalUnitLengthField, String content) {
      this.index = index;
      this.bitrate = bitrate;
      this.mimeType = mimeType;
      this.csd = csd;
      this.profile = profile;
      this.level = level;
      this.maxWidth = maxWidth;
      this.maxHeight = maxHeight;
      this.sampleRate = sampleRate;
      this.numChannels = channels;
      this.packetSize = packetSize;
      this.audioTag = audioTag;
      this.bitPerSample = bitPerSample;
      this.nalUnitLengthField = nalUnitLengthField;
      this.content = content;
    }

  }

  /**
   * Represents a StreamIndex element.
   */
  public static class StreamElement {

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_AUDIO = 0;
    public static final int TYPE_VIDEO = 1;
    public static final int TYPE_TEXT = 2;

    private static final String URL_PLACEHOLDER_START_TIME = "{start time}";
    private static final String URL_PLACEHOLDER_BITRATE = "{bitrate}";

    public final int type;
    public final String subType;
    public final long timescale;
    public final String name;
    public final int qualityLevels;
    public final int maxWidth;
    public final int maxHeight;
    public final int displayWidth;
    public final int displayHeight;
    public final String language;
    public final TrackElement[] tracks;
    public final int chunkCount;

    private final Uri baseUri;
    private final String chunkTemplate;

    private final List<Long> chunkStartTimes;
    private final long[] chunkStartTimesUs;
    private final long lastChunkDurationUs;

    public StreamElement(Uri baseUri, String chunkTemplate, int type, String subType,
        long timescale, String name, int qualityLevels, int maxWidth, int maxHeight,
        int displayWidth, int displayHeight, String language, TrackElement[] tracks,
        List<Long> chunkStartTimes, long lastChunkDuration) {
      this.baseUri = baseUri;
      this.chunkTemplate = chunkTemplate;
      this.type = type;
      this.subType = subType;
      this.timescale = timescale;
      this.name = name;
      this.qualityLevels = qualityLevels;
      this.maxWidth = maxWidth;
      this.maxHeight = maxHeight;
      this.displayWidth = displayWidth;
      this.displayHeight = displayHeight;
      this.language = language;
      this.tracks = tracks;
      this.chunkCount = chunkStartTimes.size();
      this.chunkStartTimes = chunkStartTimes;
      lastChunkDurationUs =
          Util.scaleLargeTimestamp(lastChunkDuration, MICROS_PER_SECOND, timescale);
      chunkStartTimesUs =
          Util.scaleLargeTimestamps(chunkStartTimes, MICROS_PER_SECOND, timescale);
    }

    /**
     * Gets the index of the chunk that contains the specified time.
     *
     * @param timeUs The time in microseconds.
     * @return The index of the corresponding chunk.
     */
    public int getChunkIndex(long timeUs) {
      return Util.binarySearchFloor(chunkStartTimesUs, timeUs, true, true);
    }

    /**
     * Gets the start time of the specified chunk.
     *
     * @param chunkIndex The index of the chunk.
     * @return The start time of the chunk, in microseconds.
     */
    public long getStartTimeUs(int chunkIndex) {
      return chunkStartTimesUs[chunkIndex];
    }

    /**
     * Gets the duration of the specified chunk.
     *
     * @param chunkIndex The index of the chunk.
     * @return The duration of the chunk, in microseconds.
     */
    public long getChunkDurationUs(int chunkIndex) {
      return (chunkIndex == chunkCount - 1) ? lastChunkDurationUs
          : chunkStartTimesUs[chunkIndex + 1] - chunkStartTimesUs[chunkIndex];
    }

    /**
     * Builds a uri for requesting the specified chunk of the specified track.
     *
     * @param track The index of the track for which to build the URL.
     * @param chunkIndex The index of the chunk for which to build the URL.
     * @return The request uri.
     */
    public Uri buildRequestUri(int track, int chunkIndex) {
      Assertions.checkState(tracks != null);
      Assertions.checkState(chunkStartTimes != null);
      Assertions.checkState(chunkIndex < chunkStartTimes.size());
      String chunkUrl = chunkTemplate
          .replace(URL_PLACEHOLDER_BITRATE, Integer.toString(tracks[track].bitrate))
          .replace(URL_PLACEHOLDER_START_TIME, Long.toString(chunkStartTimes.get(chunkIndex)));
      return baseUri.buildUpon().appendEncodedPath(chunkUrl).build();
    }

  }

}
