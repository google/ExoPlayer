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
package com.google.android.exoplayer2.extractor;

import com.google.android.exoplayer2.C;

/**
 * Maps seek positions (in microseconds) to corresponding positions (byte offsets) in the stream.
 */
public interface SeekMap {

  /**
   * A {@link SeekMap} that does not support seeking.
   */
  final class Unseekable implements SeekMap {

    private final long durationUs;
    private final long startPosition;

    /**
     * @param durationUs The duration of the stream in microseconds, or {@link C#TIME_UNSET} if
     *     the duration is unknown.
     */
    public Unseekable(long durationUs) {
      this(durationUs, 0);
    }

    /**
     * @param durationUs The duration of the stream in microseconds, or {@link C#TIME_UNSET} if
     *     the duration is unknown.
     * @param startPosition The position (byte offset) of the start of the media.
     */
    public Unseekable(long durationUs, long startPosition) {
      this.durationUs = durationUs;
      this.startPosition = startPosition;
    }

    @Override
    public boolean isSeekable() {
      return false;
    }

    @Override
    public long getDurationUs() {
      return durationUs;
    }

    @Override
    public long getPosition(long timeUs) {
      return startPosition;
    }

  }

  /**
   * Returns whether seeking is supported.
   * <p>
   * If seeking is not supported then the only valid seek position is the start of the file, and so
   * {@link #getPosition(long)} will return 0 for all input values.
   *
   * @return Whether seeking is supported.
   */
  boolean isSeekable();

  /**
   * Returns the duration of the stream in microseconds.
   *
   * @return The duration of the stream in microseconds, or {@link C#TIME_UNSET} if the
   *     duration is unknown.
   */
  long getDurationUs();

  /**
   * Maps a seek position in microseconds to a corresponding position (byte offset) in the stream
   * from which data can be provided to the extractor.
   *
   * @param timeUs A seek position in microseconds.
   * @return The corresponding position (byte offset) in the stream from which data can be provided
   *     to the extractor. If {@link #isSeekable()} returns false then the returned value will be
   *     independent of {@code timeUs}, and will indicate the start of the media in the stream.
   */
  long getPosition(long timeUs);

}
