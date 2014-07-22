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

import com.google.android.exoplayer.util.MimeTypes;
import com.google.android.exoplayer.util.Util;

import java.util.UUID;

/**
 * Represents a SmoothStreaming manifest.
 *
 * @see <a href="http://msdn.microsoft.com/en-us/library/ee673436(v=vs.90).aspx">
 * IIS Smooth Streaming Client Manifest Format</a>
 */
public class SmoothStreamingManifest {

  public final int majorVersion;
  public final int minorVersion;
  public final long timeScale;
  public final int lookAheadCount;
  public final ProtectionElement protectionElement;
  public final StreamElement[] streamElements;

  private final long duration;

  public SmoothStreamingManifest(int majorVersion, int minorVersion, long timeScale, long duration,
      int lookAheadCount, ProtectionElement protectionElement, StreamElement[] streamElements) {
    this.majorVersion = majorVersion;
    this.minorVersion = minorVersion;
    this.timeScale = timeScale;
    this.duration = duration;
    this.lookAheadCount = lookAheadCount;
    this.protectionElement = protectionElement;
    this.streamElements = streamElements;
  }

  /**
   * Gets the duration of the media.
   *
     *
   * @return The duration of the media, in microseconds.
   */
  public long getDurationUs() {
    return (duration * 1000000L) / timeScale;
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
    public final String fourCC;
    public final byte[][] csd;
    public final int profile;
    public final int level;

    // Audio-video (derived)
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

    public TrackElement(int index, int bitrate, String fourCC, byte[][] csd, int profile, int level,
        int maxWidth, int maxHeight, int sampleRate, int channels, int packetSize, int audioTag,
        int bitPerSample, int nalUnitLengthField, String content) {
      this.index = index;
      this.bitrate = bitrate;
      this.fourCC = fourCC;
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
      this.mimeType = fourCCToMimeType(fourCC);
    }

    private static String fourCCToMimeType(String fourCC) {
      if (fourCC.equalsIgnoreCase("H264") || fourCC.equalsIgnoreCase("AVC1")
          || fourCC.equalsIgnoreCase("DAVC")) {
        return MimeTypes.VIDEO_H264;
      } else if (fourCC.equalsIgnoreCase("AACL") || fourCC.equalsIgnoreCase("AACH")) {
        return MimeTypes.AUDIO_AAC;
      } else if (fourCC.equalsIgnoreCase("TTML")) {
        return MimeTypes.APPLICATION_TTML;
      }
      return null;
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
    public final long timeScale;
    public final String name;
    public final int qualityLevels;
    public final String url;
    public final int maxWidth;
    public final int maxHeight;
    public final int displayWidth;
    public final int displayHeight;
    public final String language;
    public final TrackElement[] tracks;
    public final int chunkCount;

    private final long[] chunkStartTimes;

    public StreamElement(int type, String subType, long timeScale, String name,
        int qualityLevels, String url, int maxWidth, int maxHeight, int displayWidth,
        int displayHeight, String language, TrackElement[] tracks, long[] chunkStartTimes) {
      this.type = type;
      this.subType = subType;
      this.timeScale = timeScale;
      this.name = name;
      this.qualityLevels = qualityLevels;
      this.url = url;
      this.maxWidth = maxWidth;
      this.maxHeight = maxHeight;
      this.displayWidth = displayWidth;
      this.displayHeight = displayHeight;
      this.language = language;
      this.tracks = tracks;
      this.chunkCount = chunkStartTimes.length;
      this.chunkStartTimes = chunkStartTimes;
    }

    /**
     * Gets the index of the chunk that contains the specified time.
     *
     * @param timeUs The time in microseconds.
     * @return The index of the corresponding chunk.
     */
    public int getChunkIndex(long timeUs) {
      return Util.binarySearchFloor(chunkStartTimes, (timeUs * timeScale) / 1000000L, true, true);
    }

    /**
     * Gets the start time of the specified chunk.
     *
     * @param chunkIndex The index of the chunk.
     * @return The start time of the chunk, in microseconds.
     */
    public long getStartTimeUs(int chunkIndex) {
      return (chunkStartTimes[chunkIndex] * 1000000L) / timeScale;
    }

    /**
     * Builds a URL for requesting the specified chunk of the specified track.
     *
     * @param track The index of the track for which to build the URL.
     * @param chunkIndex The index of the chunk for which to build the URL.
     * @return The request URL.
     */
    public String buildRequestUrl(int track, int chunkIndex) {
      assert (tracks != null);
      assert (chunkStartTimes != null);
      assert (chunkIndex < chunkStartTimes.length);
      return url.replace(URL_PLACEHOLDER_BITRATE, Integer.toString(tracks[track].bitrate))
          .replace(URL_PLACEHOLDER_START_TIME, Long.toString(chunkStartTimes[chunkIndex]));
    }

  }

}
