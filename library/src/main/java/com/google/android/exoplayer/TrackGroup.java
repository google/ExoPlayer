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
package com.google.android.exoplayer;

/**
 * Defines a group of tracks exposed by a {@link SampleSource}.
 * <p>
 * A {@link SampleSource} is only able to provide one {@link TrackStream} corresponding to a group
 * at any given time. If {@link #adaptive} is true this {@link TrackStream} can adapt between
 * multiple tracks within the group. If {@link #adaptive} is false then it's only possible to
 * consume one track from the group at a given time.
 */
public final class TrackGroup {

  /**
   * The number of tracks in the group.
   */
  public final int length;
  /**
   * Whether it's possible to adapt between multiple tracks in the group.
   */
  public final boolean adaptive;

  private final Format[] formats;

  /**
   * @param format The format of the single track.
   */
  public TrackGroup(Format format) {
    this(false, format);
  }

  /**
   * @param adaptive Whether it's possible to adapt between multiple tracks in the group.
   * @param formats The track formats.
   */
  public TrackGroup(boolean adaptive, Format... formats) {
    this.adaptive = adaptive;
    this.formats = formats;
    length = formats.length;
  }

  /**
   * Gets the format of the track at a given index.
   *
   * @param index The index of the track.
   * @return The track's format.
   */
  public Format getFormat(int index) {
    return formats[index];
  }

}
