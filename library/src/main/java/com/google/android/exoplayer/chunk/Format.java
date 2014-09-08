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
package com.google.android.exoplayer.chunk;

import com.google.android.exoplayer.util.Assertions;

import java.util.Comparator;

/**
 * Defines the high level format of a media stream.
 */
public class Format {

  /**
   * Sorts {@link Format} objects in order of decreasing bandwidth.
   */
  public static final class DecreasingBandwidthComparator implements Comparator<Format> {

    @Override
    public int compare(Format a, Format b) {
      return b.bitrate - a.bitrate;
    }

  }

  /**
   * An identifier for the format.
   */
  public final String id;

  /**
   * The mime type of the format.
   */
  public final String mimeType;

  /**
   * The width of the video in pixels, or -1 for non-video formats.
   */
  public final int width;

  /**
   * The height of the video in pixels, or -1 for non-video formats.
   */
  public final int height;

  /**
   * The number of audio channels, or -1 for non-audio formats.
   */
  public final int numChannels;

  /**
   * The audio sampling rate in Hz, or -1 for non-audio formats.
   */
  public final int audioSamplingRate;

  /**
   * The average bandwidth in bits per second.
   */
  public final int bitrate;

  /**
   * The language of the format. Can be null if unknown.
   * <p>
   * The language codes are two-letter lowercase ISO language codes (such as "en") as defined by
   * ISO 639-1.
   */
  public final String language;

  /**
   * The average bandwidth in bytes per second.
   *
   * @deprecated Use {@link #bitrate}. However note that the units of measurement are different.
   */
  @Deprecated
  public final int bandwidth;

  /**
   * @param id The format identifier.
   * @param mimeType The format mime type.
   * @param width The width of the video in pixels, or -1 for non-video formats.
   * @param height The height of the video in pixels, or -1 for non-video formats.
   * @param numChannels The number of audio channels, or -1 for non-audio formats.
   * @param audioSamplingRate The audio sampling rate in Hz, or -1 for non-audio formats.
   * @param bitrate The average bandwidth of the format in bits per second.
   */
  public Format(String id, String mimeType, int width, int height, int numChannels,
      int audioSamplingRate, int bitrate) {
    this(id, mimeType, width, height, numChannels, audioSamplingRate, bitrate, null);
  }

  /**
   * @param id The format identifier.
   * @param mimeType The format mime type.
   * @param width The width of the video in pixels, or -1 for non-video formats.
   * @param height The height of the video in pixels, or -1 for non-video formats.
   * @param numChannels The number of audio channels, or -1 for non-audio formats.
   * @param audioSamplingRate The audio sampling rate in Hz, or -1 for non-audio formats.
   * @param bitrate The average bandwidth of the format in bits per second.
   * @param language The language of the format.
   */
  public Format(String id, String mimeType, int width, int height, int numChannels,
      int audioSamplingRate, int bitrate, String language) {
    this.id = Assertions.checkNotNull(id);
    this.mimeType = mimeType;
    this.width = width;
    this.height = height;
    this.numChannels = numChannels;
    this.audioSamplingRate = audioSamplingRate;
    this.bitrate = bitrate;
    this.language = language;
    this.bandwidth = bitrate / 8;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  /**
   * Implements equality based on {@link #id} only.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    Format other = (Format) obj;
    return other.id.equals(id);
  }

}
