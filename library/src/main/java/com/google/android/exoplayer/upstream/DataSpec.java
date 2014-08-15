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
package com.google.android.exoplayer.upstream;

import com.google.android.exoplayer.C;
import com.google.android.exoplayer.util.Assertions;

import android.net.Uri;

/**
 * Defines a region of media data.
 */
public final class DataSpec {

  /**
   * Identifies the source from which data should be read.
   */
  public final Uri uri;
  /**
   * True if the data at {@link #uri} is the full stream. False otherwise. An example where this
   * may be false is if {@link #uri} defines the location of a cached part of the stream.
   */
  public final boolean uriIsFullStream;
  /**
   * The absolute position of the data in the full stream.
   */
  public final long absoluteStreamPosition;
  /**
   * The position of the data when read from {@link #uri}. Always equal to
   * {@link #absoluteStreamPosition} if {@link #uriIsFullStream}.
   */
  public final long position;
  /**
   * The length of the data. Greater than zero, or equal to {@link C#LENGTH_UNBOUNDED}.
   */
  public final long length;
  /**
   * A key that uniquely identifies the original stream. Used for cache indexing. May be null if the
   * {@link DataSpec} is not intended to be used in conjunction with a cache.
   */
  public final String key;

  /**
   * Construct a {@link DataSpec} for which {@link #uriIsFullStream} is true.
   *
   * @param uri {@link #uri}.
   * @param absoluteStreamPosition {@link #absoluteStreamPosition}, equal to {@link #position}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   */
  public DataSpec(Uri uri, long absoluteStreamPosition, long length, String key) {
    this(uri, absoluteStreamPosition, length, key, absoluteStreamPosition, true);
  }

  /**
   * Construct a {@link DataSpec} for which {@link #uriIsFullStream} is false.
   *
   * @param uri {@link #uri}.
   * @param absoluteStreamPosition {@link #absoluteStreamPosition}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   * @param position {@link #position}.
   */
  public DataSpec(Uri uri, long absoluteStreamPosition, long length, String key, long position) {
    this(uri, absoluteStreamPosition, length, key, position, false);
  }

  /**
   * Construct a {@link DataSpec}.
   *
   * @param uri {@link #uri}.
   * @param absoluteStreamPosition {@link #absoluteStreamPosition}.
   * @param length {@link #length}.
   * @param key {@link #key}.
   * @param position {@link #position}.
   * @param uriIsFullStream {@link #uriIsFullStream}.
   */
  public DataSpec(Uri uri, long absoluteStreamPosition, long length, String key, long position,
      boolean uriIsFullStream) {
    Assertions.checkArgument(absoluteStreamPosition >= 0);
    Assertions.checkArgument(position >= 0);
    Assertions.checkArgument(length > 0 || length == C.LENGTH_UNBOUNDED);
    Assertions.checkArgument(absoluteStreamPosition == position || !uriIsFullStream);
    this.uri = uri;
    this.uriIsFullStream = uriIsFullStream;
    this.absoluteStreamPosition = absoluteStreamPosition;
    this.position = position;
    this.length = length;
    this.key = key;
  }

  @Override
  public String toString() {
    return "DataSpec[" + uri + ", " + uriIsFullStream + ", " + absoluteStreamPosition + ", " +
        position + ", " + length + ", " + key + "]";
  }

}
