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

import android.net.Uri;

import java.util.List;

/**
 * An approximate representation of a SegmentBase manifest element.
 */
public abstract class SegmentBase {

  /* package */ final RangedUri initialization;
  /* package */ final long timescale;
  /* package */ final long presentationTimeOffset;

  /**
   * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
   *     exists.
   * @param timescale The timescale in units per second.
   * @param presentationTimeOffset The presentation time offset. The value in seconds is the
   *     division of this value and {@code timescale}.
   */
  public SegmentBase(RangedUri initialization, long timescale, long presentationTimeOffset) {
    this.initialization = initialization;
    this.timescale = timescale;
    this.presentationTimeOffset = presentationTimeOffset;
  }

  /**
   * Gets the {@link RangedUri} defining the location of initialization data for a given
   * representation. May be null if no initialization data exists.
   *
   * @param representation The {@link Representation} for which initialization data is required.
   * @return A {@link RangedUri} defining the location of the initialization data, or null.
   */
  public RangedUri getInitialization(Representation representation) {
    return initialization;
  }

  /**
   * A {@link SegmentBase} that defines a single segment.
   */
  public static class SingleSegmentBase extends SegmentBase {

    /**
     * The uri of the segment.
     */
    public final Uri uri;

    /* package */ final long indexStart;
    /* package */ final long indexLength;

    /**
     * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
     *     exists.
     * @param timescale The timescale in units per second.
     * @param presentationTimeOffset The presentation time offset. The value in seconds is the
     *     division of this value and {@code timescale}.
     * @param uri The uri of the segment.
     * @param indexStart The byte offset of the index data in the segment.
     * @param indexLength The length of the index data in bytes.
     */
    public SingleSegmentBase(RangedUri initialization, long timescale, long presentationTimeOffset,
        Uri uri, long indexStart, long indexLength) {
      super(initialization, timescale, presentationTimeOffset);
      this.uri = uri;
      this.indexStart = indexStart;
      this.indexLength = indexLength;
    }

    public RangedUri getIndex() {
      return new RangedUri(uri, null, indexStart, indexLength);
    }

  }

  /**
   * A {@link SegmentBase} that consists of multiple segments.
   */
  public abstract static class MultiSegmentBase extends SegmentBase {

    /* package */ final long periodDurationMs;
    /* package */ final int startNumber;
    /* package */ final long duration;
    /* package */ final List<SegmentTimelineElement> segmentTimeline;

    /**
     * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
     *     exists.
     * @param timescale The timescale in units per second.
     * @param presentationTimeOffset The presentation time offset. The value in seconds is the
     *     division of this value and {@code timescale}.
     * @param periodDurationMs The duration of the enclosing period in milliseconds.
     * @param startNumber The sequence number of the first segment.
     * @param duration The duration of each segment in the case of fixed duration segments. The
     *     value in seconds is the division of this value and {@code timescale}. If
     *     {@code segmentTimeline} is non-null then this parameter is ignored.
     * @param segmentTimeline A segment timeline corresponding to the segments. If null, then
     *     segments are assumed to be of fixed duration as specified by the {@code duration}
     *     parameter.
     */
    public MultiSegmentBase(RangedUri initialization, long timescale, long presentationTimeOffset,
        long periodDurationMs, int startNumber, long duration,
        List<SegmentTimelineElement> segmentTimeline) {
      super(initialization, timescale, presentationTimeOffset);
      this.periodDurationMs = periodDurationMs;
      this.startNumber = startNumber;
      this.duration = duration;
      this.segmentTimeline = segmentTimeline;
    }

    public final int getSegmentNum(long timeUs) {
      // TODO: Optimize this
      int index = startNumber;
      while (index + 1 <= getLastSegmentNum()) {
        if (getSegmentTimeUs(index + 1) <= timeUs) {
          index++;
        } else {
          return index;
        }
      }
      return index;
    }

    public final long getSegmentDurationUs(int sequenceNumber) {
      if (segmentTimeline != null) {
        return (segmentTimeline.get(sequenceNumber - startNumber).duration * 1000000) / timescale;
      } else {
        return sequenceNumber == getLastSegmentNum()
            ? (periodDurationMs * 1000) - getSegmentTimeUs(sequenceNumber)
            : ((duration * 1000000L) / timescale);
      }
    }

    public final long getSegmentTimeUs(int sequenceNumber) {
      long unscaledSegmentTime;
      if (segmentTimeline != null) {
        unscaledSegmentTime = segmentTimeline.get(sequenceNumber - startNumber).startTime
            - presentationTimeOffset;
      } else {
        unscaledSegmentTime = (sequenceNumber - startNumber) * duration;
      }
      return (unscaledSegmentTime * 1000000) / timescale;
    }

    public abstract RangedUri getSegmentUrl(Representation representation, int index);

    public int getFirstSegmentNum() {
      return startNumber;
    }

    public abstract int getLastSegmentNum();

  }

  /**
   * A {@link MultiSegmentBase} that uses a SegmentList to define its segments.
   */
  public static class SegmentList extends MultiSegmentBase {

    /* package */ final List<RangedUri> mediaSegments;

    /**
     * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
     *     exists.
     * @param timescale The timescale in units per second.
     * @param presentationTimeOffset The presentation time offset. The value in seconds is the
     *     division of this value and {@code timescale}.
     * @param periodDurationMs The duration of the enclosing period in milliseconds.
     * @param startNumber The sequence number of the first segment.
     * @param duration The duration of each segment in the case of fixed duration segments. The
     *     value in seconds is the division of this value and {@code timescale}. If
     *     {@code segmentTimeline} is non-null then this parameter is ignored.
     * @param segmentTimeline A segment timeline corresponding to the segments. If null, then
     *     segments are assumed to be of fixed duration as specified by the {@code duration}
     *     parameter.
     * @param mediaSegments A list of {@link RangedUri}s indicating the locations of the segments.
     */
    public SegmentList(RangedUri initialization, long timescale, long presentationTimeOffset,
        long periodDurationMs, int startNumber, long duration,
        List<SegmentTimelineElement> segmentTimeline, List<RangedUri> mediaSegments) {
      super(initialization, timescale, presentationTimeOffset, periodDurationMs, startNumber,
          duration, segmentTimeline);
      this.mediaSegments = mediaSegments;
    }

    @Override
    public RangedUri getSegmentUrl(Representation representation, int sequenceNumber) {
      return mediaSegments.get(sequenceNumber - startNumber);
    }

    @Override
    public int getLastSegmentNum() {
      return startNumber + mediaSegments.size() - 1;
    }

  }

  /**
   * A {@link MultiSegmentBase} that uses a SegmentTemplate to define its segments.
   */
  public static class SegmentTemplate extends MultiSegmentBase {

    /* package */ final UrlTemplate initializationTemplate;
    /* package */ final UrlTemplate mediaTemplate;

    private final Uri baseUrl;

    /**
     * @param initialization A {@link RangedUri} corresponding to initialization data, if such data
     *     exists. The value of this parameter is ignored if {@code initializationTemplate} is
     *     non-null.
     * @param timescale The timescale in units per second.
     * @param presentationTimeOffset The presentation time offset. The value in seconds is the
     *     division of this value and {@code timescale}.
     * @param periodDurationMs The duration of the enclosing period in milliseconds.
     * @param startNumber The sequence number of the first segment.
     * @param duration The duration of each segment in the case of fixed duration segments. The
     *     value in seconds is the division of this value and {@code timescale}. If
     *     {@code segmentTimeline} is non-null then this parameter is ignored.
     * @param segmentTimeline A segment timeline corresponding to the segments. If null, then
     *     segments are assumed to be of fixed duration as specified by the {@code duration}
     *     parameter.
     * @param initializationTemplate A template defining the location of initialization data, if
     *     such data exists. If non-null then the {@code initialization} parameter is ignored. If
     *     null then {@code initialization} will be used.
     * @param mediaTemplate A template defining the location of each media segment.
     * @param baseUrl A url to use as the base for relative urls generated by the templates.
     */
    public SegmentTemplate(RangedUri initialization, long timescale, long presentationTimeOffset,
        long periodDurationMs, int startNumber, long duration,
        List<SegmentTimelineElement> segmentTimeline, UrlTemplate initializationTemplate,
        UrlTemplate mediaTemplate, Uri baseUrl) {
      super(initialization, timescale, presentationTimeOffset, periodDurationMs, startNumber,
          duration, segmentTimeline);
      this.initializationTemplate = initializationTemplate;
      this.mediaTemplate = mediaTemplate;
      this.baseUrl = baseUrl;
    }

    @Override
    public RangedUri getInitialization(Representation representation) {
      if (initializationTemplate != null) {
        String urlString = initializationTemplate.buildUri(representation.format.id, 0,
            representation.format.bitrate, 0);
        return new RangedUri(baseUrl, urlString, 0, -1);
      } else {
        return super.getInitialization(representation);
      }
    }

    @Override
    public RangedUri getSegmentUrl(Representation representation, int sequenceNumber) {
      long time = 0;
      if (segmentTimeline != null) {
        time = segmentTimeline.get(sequenceNumber - startNumber).startTime;
      } else {
        time = (sequenceNumber - startNumber) * duration;
      }
      String uriString = mediaTemplate.buildUri(representation.format.id, sequenceNumber,
          representation.format.bitrate, time);
      return new RangedUri(baseUrl, uriString, 0, -1);
    }

    @Override
    public int getLastSegmentNum() {
      if (segmentTimeline != null) {
        return segmentTimeline.size() + startNumber - 1;
      } else {
        long durationMs = (duration * 1000) / timescale;
        return startNumber + (int) (periodDurationMs / durationMs);
      }
    }

  }

  /**
   * Represents a timeline segment from the MPD's SegmentTimeline list.
   */
  public static class SegmentTimelineElement {

    /* package */ long startTime;
    /* package */ long duration;

    /**
     * @param startTime The start time of the element. The value in seconds is the division of this
     *     value and the {@code timescale} of the enclosing element.
     * @param duration The duration of the element. The value in seconds is the division of this
     *     value and the {@code timescale} of the enclosing element.
     */
    public SegmentTimelineElement(long startTime, long duration) {
      this.startTime = startTime;
      this.duration = duration;
    }

  }

}
