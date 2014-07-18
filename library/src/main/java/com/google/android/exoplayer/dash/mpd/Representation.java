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

import com.google.android.exoplayer.chunk.Format;
import com.google.android.exoplayer.dash.DashSegmentIndex;
import com.google.android.exoplayer.dash.mpd.SegmentBase.MultiSegmentBase;
import com.google.android.exoplayer.dash.mpd.SegmentBase.SingleSegmentBase;

import android.net.Uri;

/**
 * A DASH representation.
 */
public abstract class Representation {

  /**
   * Identifies the piece of content to which this {@link Representation} belongs.
   * <p>
   * For example, all {@link Representation}s belonging to a video should have the same
   * {@link #contentId}, which should uniquely identify that video.
   */
  public final String contentId;

  /**
   * Identifies the revision of the content.
   * <p>
   * If the media for a given ({@link #contentId} can change over time without a change to the
   * {@link #format}'s {@link Format#id} (e.g. as a result of re-encoding the media with an
   * updated encoder), then this identifier must uniquely identify the revision of the media. The
   * timestamp at which the media was encoded is often a suitable.
   */
  public final long revisionId;

  /**
   * The format of the representation.
   */
  public final Format format;

  /**
   * The start time of the enclosing period in milliseconds since the epoch.
   */
  public final long periodStartMs;

  /**
   * The duration of the enclosing period in milliseconds.
   */
  public final long periodDurationMs;

  /**
   * The offset of the presentation timestamps in the media stream relative to media time.
   */
  public final long presentationTimeOffsetMs;

  private final RangedUri initializationUri;

  /**
   * Constructs a new instance.
   *
   * @param periodStartMs The start time of the enclosing period in milliseconds.
   * @param periodDurationMs The duration of the enclosing period in milliseconds, or -1 if the
   *     duration is unknown.
   * @param contentId Identifies the piece of content to which this representation belongs.
   * @param revisionId Identifies the revision of the content.
   * @param format The format of the representation.
   * @param segmentBase A segment base element for the representation.
   * @return The constructed instance.
   */
  public static Representation newInstance(long periodStartMs, long periodDurationMs,
      String contentId, long revisionId, Format format, SegmentBase segmentBase) {
    if (segmentBase instanceof SingleSegmentBase) {
      return new SingleSegmentRepresentation(periodStartMs, periodDurationMs, contentId, revisionId,
          format, (SingleSegmentBase) segmentBase, -1);
    } else if (segmentBase instanceof MultiSegmentBase) {
      return new MultiSegmentRepresentation(periodStartMs, periodDurationMs, contentId, revisionId,
          format, (MultiSegmentBase) segmentBase);
    } else {
      throw new IllegalArgumentException("segmentBase must be of type SingleSegmentBase or "
          + "MultiSegmentBase");
    }
  }

  private Representation(long periodStartMs, long periodDurationMs, String contentId,
      long revisionId, Format format, SegmentBase segmentBase) {
    this.periodStartMs = periodStartMs;
    this.periodDurationMs = periodDurationMs;
    this.contentId = contentId;
    this.revisionId = revisionId;
    this.format = format;
    initializationUri = segmentBase.getInitialization(this);
    presentationTimeOffsetMs = (segmentBase.presentationTimeOffset * 1000) / segmentBase.timescale;
  }

  /**
   * Gets a {@link RangedUri} defining the location of the representation's initialization data.
   * May be null if no initialization data exists.
   *
   * @return A {@link RangedUri} defining the location of the initialization data, or null.
   */
  public RangedUri getInitializationUri() {
    return initializationUri;
  }

  /**
   * Gets a {@link RangedUri} defining the location of the representation's segment index. Null if
   * the representation provides an index directly.
   *
   * @return The location of the segment index, or null.
   */
  public abstract RangedUri getIndexUri();

  /**
   * Gets a segment index, if the representation is able to provide one directly. Null if the
   * segment index is defined externally.
   *
   * @return The segment index, or null.
   */
  public abstract DashSegmentIndex getIndex();

  /**
   * Generates a cache key for the {@link Representation}, in the format
   * {@code contentId + "." + format.id + "." + revisionId}.
   *
   * @return A cache key.
   */
  public String getCacheKey() {
    return contentId + "." + format.id + "." + revisionId;
  }

  /**
   * A DASH representation consisting of a single segment.
   */
  public static class SingleSegmentRepresentation extends Representation {

    /**
     * The {@link Uri} of the single segment.
     */
    public final Uri uri;

    /**
     * The content length, or -1 if unknown.
     */
    public final long contentLength;

    private final RangedUri indexUri;

    /**
     * @param periodStartMs The start time of the enclosing period in milliseconds.
     * @param periodDurationMs The duration of the enclosing period in milliseconds, or -1 if the
     *     duration is unknown.
     * @param contentId Identifies the piece of content to which this representation belongs.
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param uri The uri of the media.
     * @param initializationStart The offset of the first byte of initialization data.
     * @param initializationEnd The offset of the last byte of initialization data.
     * @param indexStart The offset of the first byte of index data.
     * @param indexEnd The offset of the last byte of index data.
     * @param contentLength The content length, or -1 if unknown.
     */
    public static SingleSegmentRepresentation newInstance(long periodStartMs, long periodDurationMs,
        String contentId, long revisionId, Format format, Uri uri, long initializationStart,
        long initializationEnd, long indexStart, long indexEnd, long contentLength) {
      RangedUri rangedUri = new RangedUri(uri, null, initializationStart,
          initializationEnd - initializationStart + 1);
      SingleSegmentBase segmentBase = new SingleSegmentBase(rangedUri, 1, 0, uri, indexStart,
          indexEnd - indexStart + 1);
      return new SingleSegmentRepresentation(periodStartMs, periodDurationMs, contentId, revisionId,
          format, segmentBase, contentLength);
    }

    /**
     * @param periodStartMs The start time of the enclosing period in milliseconds.
     * @param periodDurationMs The duration of the enclosing period in milliseconds, or -1 if the
     *     duration is unknown.
     * @param contentId Identifies the piece of content to which this representation belongs.
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param segmentBase The segment base underlying the representation.
     * @param contentLength The content length, or -1 if unknown.
     */
    public SingleSegmentRepresentation(long periodStartMs, long periodDurationMs, String contentId,
        long revisionId, Format format, SingleSegmentBase segmentBase, long contentLength) {
      super(periodStartMs, periodDurationMs, contentId, revisionId, format, segmentBase);
      this.uri = segmentBase.uri;
      this.indexUri = segmentBase.getIndex();
      this.contentLength = contentLength;
    }

    @Override
    public RangedUri getIndexUri() {
      return indexUri;
    }

    @Override
    public DashSegmentIndex getIndex() {
      return null;
    }

  }

  /**
   * A DASH representation consisting of multiple segments.
   */
  public static class MultiSegmentRepresentation extends Representation
      implements DashSegmentIndex {

    private final MultiSegmentBase segmentBase;

    /**
     * @param periodStartMs The start time of the enclosing period in milliseconds.
     * @param periodDurationMs The duration of the enclosing period in milliseconds, or -1 if the
     *     duration is unknown.
     * @param contentId Identifies the piece of content to which this representation belongs.
     * @param revisionId Identifies the revision of the content.
     * @param format The format of the representation.
     * @param segmentBase The segment base underlying the representation.
     */
    public MultiSegmentRepresentation(long periodStartMs, long periodDurationMs, String contentId,
        long revisionId, Format format, MultiSegmentBase segmentBase) {
      super(periodStartMs, periodDurationMs, contentId, revisionId, format, segmentBase);
      this.segmentBase = segmentBase;
    }

    @Override
    public RangedUri getIndexUri() {
      return null;
    }

    @Override
    public DashSegmentIndex getIndex() {
      return this;
    }

    // DashSegmentIndex implementation.

    @Override
    public RangedUri getSegmentUrl(int segmentIndex) {
      return segmentBase.getSegmentUrl(this, segmentIndex);
    }

    @Override
    public int getSegmentNum(long timeUs) {
      return segmentBase.getSegmentNum(timeUs);
    }

    @Override
    public long getTimeUs(int segmentIndex) {
      return segmentBase.getSegmentTimeUs(segmentIndex);
    }

    @Override
    public long getDurationUs(int segmentIndex) {
      return segmentBase.getSegmentDurationUs(segmentIndex);
    }

    @Override
    public int getFirstSegmentNum() {
      return segmentBase.getFirstSegmentNum();
    }

    @Override
    public int getLastSegmentNum() {
      return segmentBase.getLastSegmentNum();
    }

  }

}
