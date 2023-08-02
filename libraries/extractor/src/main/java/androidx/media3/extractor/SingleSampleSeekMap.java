/*
 * Copyright 2023 The Android Open Source Project
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
package androidx.media3.extractor;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;

/**
 * A {@link SeekMap} implementation that maps the given point back onto itself.
 *
 * <p>Used for single sample media.
 */
@UnstableApi
public final class SingleSampleSeekMap implements SeekMap {
  private final long durationUs;
  private final long startPosition;

  /**
   * Creates an instance with {@code startPosition} set to 0.
   *
   * @param durationUs The duration of the stream in microseconds, or {@link C#TIME_UNSET} if the
   *     duration is unknown.
   */
  public SingleSampleSeekMap(long durationUs) {
    this(durationUs, /* startPosition= */ 0);
  }

  /**
   * Creates an instance.
   *
   * @param durationUs The duration of the stream in microseconds, or {@link C#TIME_UNSET} if the
   *     duration is unknown.
   * @param startPosition The position (byte offset) of the start of the media.
   */
  public SingleSampleSeekMap(long durationUs, long startPosition) {
    this.durationUs = durationUs;
    this.startPosition = startPosition;
  }

  @Override
  public boolean isSeekable() {
    return true;
  }

  @Override
  public long getDurationUs() {
    return durationUs;
  }

  @Override
  public SeekPoints getSeekPoints(long timeUs) {
    return new SeekPoints(new SeekPoint(timeUs, startPosition));
  }
}
