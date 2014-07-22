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

import com.google.android.exoplayer.util.Assertions;

import android.net.Uri;

/**
 * Defines a range of data located at a {@link Uri}.
 */
public final class RangedUri {

  /**
   * The (zero based) index of the first byte of the range.
   */
  public final long start;

  /**
   * The length of the range, or -1 to indicate that the range is unbounded.
   */
  public final long length;

  // The {@link Uri} is stored internally in two parts, {@link #baseUri} and {@link uriString}.
  // This helps optimize memory usage in the same way that DASH manifests allow many URLs to be
  // expressed concisely in the form of a single BaseURL and many relative paths. Note that this
  // optimization relies on the same {@code Uri} being passed as the {@link #baseUri} to many
  // instances of this class.
  private final Uri baseUri;
  private final String stringUri;

  private int hashCode;

  /**
   * Constructs an ranged uri.
   * <p>
   * The uri is built according to the following rules:
   * <ul>
   * <li>If {@code baseUri} is null or if {@code stringUri} is absolute, then {@code baseUri} is
   * ignored and the url consists solely of {@code stringUri}.
   * <li>If {@code stringUri} is null, then the url consists solely of {@code baseUrl}.
   * <li>Otherwise, the url consists of the concatenation of {@code baseUri} and {@code stringUri}.
   * </ul>
   *
   * @param baseUri An uri that can form the base of the uri defined by the instance.
   * @param stringUri A relative or absolute uri in string form.
   * @param start The (zero based) index of the first byte of the range.
   * @param length The length of the range, or -1 to indicate that the range is unbounded.
   */
  public RangedUri(Uri baseUri, String stringUri, long start, long length) {
    Assertions.checkArgument(baseUri != null || stringUri != null);
    this.baseUri = baseUri;
    this.stringUri = stringUri;
    this.start = start;
    this.length = length;
  }

  /**
   * Returns the {@link Uri} represented by the instance.
   *
   * @return The {@link Uri} represented by the instance.
   */
  public Uri getUri() {
    if (stringUri == null) {
      return baseUri;
    }
    Uri uri = Uri.parse(stringUri);
    if (!uri.isAbsolute() && baseUri != null) {
      uri = Uri.withAppendedPath(baseUri, stringUri);
    }
    return uri;
  }

  /**
   * Attempts to merge this {@link RangedUri} with another.
   * <p>
   * A merge is successful if both instances define the same {@link Uri}, and if one starte the
   * byte after the other ends, forming a contiguous region with no overlap.
   * <p>
   * If {@code other} is null then the merge is considered unsuccessful, and null is returned.
   *
   * @param other The {@link RangedUri} to merge.
   * @return The merged {@link RangedUri} if the merge was successful. Null otherwise.
   */
  public RangedUri attemptMerge(RangedUri other) {
    if (other == null || !getUri().equals(other.getUri())) {
      return null;
    } else if (length != -1 && start + length == other.start) {
      return new RangedUri(baseUri, stringUri, start,
          other.length == -1 ? -1 : length + other.length);
    } else if (other.length != -1 && other.start + other.length == start) {
      return new RangedUri(baseUri, stringUri, other.start,
          length == -1 ? -1 : other.length + length);
    } else {
      return null;
    }
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 31 * result + (int) start;
      result = 31 * result + (int) length;
      result = 31 * result + getUri().hashCode();
      hashCode = result;
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    RangedUri other = (RangedUri) obj;
    return this.start == other.start
        && this.length == other.length
        && getUri().equals(other.getUri());
  }

}
