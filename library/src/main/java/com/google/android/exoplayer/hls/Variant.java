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
package com.google.android.exoplayer.hls;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;

/**
 * Variant stream reference.
 */
public final class Variant {

  /**
   * Sorts {@link Variant} objects in order of decreasing bandwidth.
   * <p>
   * When two {@link Variant}s have the same bandwidth, the one with the lowest index comes first.
   */
  public static final class DecreasingBandwidthComparator implements Comparator<Variant> {

    @Override
    public int compare(Variant a, Variant b) {
      int bandwidthDifference = b.bandwidth - a.bandwidth;
      return bandwidthDifference != 0 ? bandwidthDifference : a.index - b.index;
    }

  }

  public final int index;
  public final int bandwidth;
  public final String url;
  public final String[] codecs;
  public final int width;
  public final int height;
  public final String audioGroup;
  public final String videoGroup;
  public final String closedCaptionsGroup;
  public final String subtitlesGroup;

  public final List<AlternateMedia> subtitles = new ArrayList<AlternateMedia>();
  public final List<AlternateMedia> closedCaptions = new ArrayList<AlternateMedia>();
  public final List<AlternateMedia> alternateAudio = new ArrayList<AlternateMedia>();
  public final List<AlternateMedia> alternateVideo = new ArrayList<AlternateMedia>();


  public Variant(int index, String url, int bandwidth, String[] codecs, int width, int height,
                 String videoGroup, String audioGroup, String subtitlesGroup, String closedCaptionsGroup) {
    this.index = index;
    this.bandwidth = bandwidth;
    this.url = url;
    this.codecs = codecs;
    this.width = width;
    this.height = height;
    this.videoGroup = videoGroup;
    this.audioGroup = audioGroup;
    this.closedCaptionsGroup = closedCaptionsGroup;
    this.subtitlesGroup = subtitlesGroup;
  }

}
