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

import java.util.Comparator;

/**
 * A format definition for streams.
 */
public final class Format {

  /**
   * Sorts {@link Format} objects in order of decreasing bandwidth.
   */
  public static final class DecreasingBandwidthComparator implements Comparator<Format> {

    @Override
    public int compare(Format a, Format b) {
      return b.bandwidth - a.bandwidth;
    }

  }

  /**
   * An identifier for the format.
   */
  public final int id;

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
   * The average bandwidth in bytes per second.
   */
  public final int bandwidth;

  /**
   * @param id The format identifier.
   * @param mimeType The format mime type.
   * @param width The width of the video in pixels, or -1 for non-video formats.
   * @param height The height of the video in pixels, or -1 for non-video formats.
   * @param numChannels The number of audio channels, or -1 for non-audio formats.
   * @param audioSamplingRate The audio sampling rate in Hz, or -1 for non-audio formats.
   * @param bandwidth The average bandwidth of the format in bytes per second.
   */
  public Format(int id, String mimeType, int width, int height, int numChannels,
      int audioSamplingRate, int bandwidth) {
    this.id = id;
    this.mimeType = mimeType;
    this.width = width;
    this.height = height;
    this.numChannels = numChannels;
    this.audioSamplingRate = audioSamplingRate;
    this.bandwidth = bandwidth;
  }

}
