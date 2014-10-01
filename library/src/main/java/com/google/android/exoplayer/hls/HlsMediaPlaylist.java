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

import android.net.Uri;

import java.util.List;

/**
 * Represents an HLS media playlist.
 */
public final class HlsMediaPlaylist {

  /**
   * Media segment reference.
   */
  public static final class Segment implements Comparable<Long> {
    public final boolean discontinuity;
    public final double durationSecs;
    public final String url;
    public final long startTimeUs;

    public Segment(String uri, double durationSecs, boolean discontinuity, long startTimeUs) {
      this.url = uri;
      this.durationSecs = durationSecs;
      this.discontinuity = discontinuity;
      this.startTimeUs = startTimeUs;
    }

    @Override
    public int compareTo(Long startTimeUs) {
      return (int) (this.startTimeUs - startTimeUs);
    }
  }

  public final Uri baseUri;
  public final int mediaSequence;
  public final int targetDurationSecs;
  public final int version;
  public final List<Segment> segments;
  public final boolean live;
  public final long durationUs;

  public HlsMediaPlaylist(Uri baseUri, int mediaSequence, int targetDurationSecs, int version,
      boolean live, List<Segment> segments) {
    this.baseUri = baseUri;
    this.mediaSequence = mediaSequence;
    this.targetDurationSecs = targetDurationSecs;
    this.version = version;
    this.live = live;
    this.segments = segments;

    if (this.segments.size() > 0) {
      Segment lastSegment = segments.get(this.segments.size() - 1);
      this.durationUs = lastSegment.startTimeUs + (long) (lastSegment.durationSecs * 1000000);
    } else {
      this.durationUs = 0;
    }
  }

}
