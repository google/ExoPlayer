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

import android.net.Uri;

/**
 * A flat version of a DASH representation.
 */
public class Representation {

  /**
   * Identifies the piece of content to which this {@link Representation} belongs.
   * <p>
   * For example, all {@link Representation}s belonging to a video should have the same
   * {@link #contentId}, which should uniquely identify that video.
   */
  public final String contentId;

  /**
   * Identifies the revision of the {@link Representation}.
   * <p>
   * If the media for a given ({@link #contentId} can change over time without a change to the
   * {@link #format}'s {@link Format#id} (e.g. as a result of re-encoding the media with an
   * updated encoder), then this identifier must uniquely identify the revision of the media. The
   * timestamp at which the media was encoded is often a suitable.
   */
  public final long revisionId;

  /**
   * The format in which the {@link Representation} is encoded.
   */
  public final Format format;

  public final long contentLength;

  public final long initializationStart;

  public final long initializationEnd;

  public final long indexStart;

  public final long indexEnd;

  public final long periodStart;

  public final long periodDuration;

  public final Uri uri;

  public Representation(String contentId, long revisionId, Format format, Uri uri,
      long contentLength, long initializationStart, long initializationEnd, long indexStart,
      long indexEnd, long periodStart, long periodDuration) {
    this.contentId = contentId;
    this.revisionId = revisionId;
    this.format = format;
    this.contentLength = contentLength;
    this.initializationStart = initializationStart;
    this.initializationEnd = initializationEnd;
    this.indexStart = indexStart;
    this.indexEnd = indexEnd;
    this.periodStart = periodStart;
    this.periodDuration = periodDuration;
    this.uri = uri;
  }

  /**
   * Generates a cache key for the {@link Representation}, in the format
   * {@link #contentId}.{@link #format.id}.{@link #revisionId}.
   *
   * @return A cache key.
   */
  public String getCacheKey() {
    return contentId + "." + format.id + "." + revisionId;
  }

}
