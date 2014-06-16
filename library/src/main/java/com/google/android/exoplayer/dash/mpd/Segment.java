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

/**
 * Represents a particular segment in a Representation.
 * 
 */
public abstract class Segment {

  public final String relativeUri;

  public final long sequenceNumber;

  public final long duration;

  public Segment(String relativeUri, long sequenceNumber, long duration) {
    this.relativeUri = relativeUri;
    this.sequenceNumber = sequenceNumber;
    this.duration = duration;
  }

  /**
   * Represents a timeline segment from the MPD's SegmentTimeline list.
   */
  public static class Timeline extends Segment {

    public Timeline(long sequenceNumber, long duration) {
      super(null, sequenceNumber, duration);
    }

  }

  /**
   * Represents an initialization segment.
   */
  public static class Initialization extends Segment {

    public final long initializationStart;
    public final long initializationEnd;

    public Initialization(String relativeUri, long initializationStart,
        long initializationEnd) {
      super(relativeUri, -1, -1);
      this.initializationStart = initializationStart;
      this.initializationEnd = initializationEnd;
    }

  }

  /**
   * Represents a media segment.
   */
  public static class Media extends Segment {

    public final long mediaStart;

    public Media(String relativeUri, long sequenceNumber, long duration) {
      this(relativeUri, 0, sequenceNumber, duration);
    }

    public Media(String uri, long mediaStart, long sequenceNumber, long duration) {
      super(uri, sequenceNumber, duration);
      this.mediaStart = mediaStart;
    }

  }
}
