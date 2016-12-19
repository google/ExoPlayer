/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.google.android.exoplayer2.source.hls.playlist;

import com.google.android.exoplayer2.C;
import java.util.Collections;
import java.util.List;

/**
 * Represents an HLS media playlist.
 */
public final class HlsMediaPlaylist extends HlsPlaylist {

  /**
   * Media segment reference.
   */
  public static final class Segment implements Comparable<Long> {

    public final String url;
    public final long durationUs;
    public final int discontinuitySequenceNumber;
    public final long relativeStartTimeUs;
    public final boolean isEncrypted;
    public final String encryptionKeyUri;
    public final String encryptionIV;
    public final long byterangeOffset;
    public final long byterangeLength;

    public Segment(String uri, long byterangeOffset, long byterangeLength) {
      this(uri, 0, -1, C.TIME_UNSET, false, null, null, byterangeOffset, byterangeLength);
    }

    public Segment(String uri, long durationUs, int discontinuitySequenceNumber,
        long relativeStartTimeUs, boolean isEncrypted, String encryptionKeyUri, String encryptionIV,
        long byterangeOffset, long byterangeLength) {
      this.url = uri;
      this.durationUs = durationUs;
      this.discontinuitySequenceNumber = discontinuitySequenceNumber;
      this.relativeStartTimeUs = relativeStartTimeUs;
      this.isEncrypted = isEncrypted;
      this.encryptionKeyUri = encryptionKeyUri;
      this.encryptionIV = encryptionIV;
      this.byterangeOffset = byterangeOffset;
      this.byterangeLength = byterangeLength;
    }

    @Override
    public int compareTo(Long relativeStartTimeUs) {
      return this.relativeStartTimeUs > relativeStartTimeUs
          ? 1 : (this.relativeStartTimeUs < relativeStartTimeUs ? -1 : 0);
    }

  }

  public final long startTimeUs;
  public final int mediaSequence;
  public final int version;
  public final long targetDurationUs;
  public final boolean hasEndTag;
  public final boolean hasProgramDateTime;
  public final Segment initializationSegment;
  public final List<Segment> segments;
  public final long durationUs;

  public HlsMediaPlaylist(String baseUri, long startTimeUs, int mediaSequence,
      int version, long targetDurationUs, boolean hasEndTag, boolean hasProgramDateTime,
      Segment initializationSegment, List<Segment> segments) {
    super(baseUri, HlsPlaylist.TYPE_MEDIA);
    this.startTimeUs = startTimeUs;
    this.mediaSequence = mediaSequence;
    this.version = version;
    this.targetDurationUs = targetDurationUs;
    this.hasEndTag = hasEndTag;
    this.hasProgramDateTime = hasProgramDateTime;
    this.initializationSegment = initializationSegment;
    this.segments = Collections.unmodifiableList(segments);

    if (!segments.isEmpty()) {
      Segment last = segments.get(segments.size() - 1);
      durationUs = last.relativeStartTimeUs + last.durationUs;
    } else {
      durationUs = 0;
    }
  }

  public boolean isNewerThan(HlsMediaPlaylist other) {
    return other == null || mediaSequence > other.mediaSequence
        || (mediaSequence == other.mediaSequence && segments.size() > other.segments.size())
        || (hasEndTag && !other.hasEndTag);
  }

  public long getEndTimeUs() {
    return startTimeUs + durationUs;
  }

  public HlsMediaPlaylist copyWithStartTimeUs(long startTimeUs) {
    return new HlsMediaPlaylist(baseUri, startTimeUs, mediaSequence, version, targetDurationUs,
        hasEndTag, hasProgramDateTime, initializationSegment, segments);
  }

}
