/*
 * Copyright 2021 The Android Open Source Project
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
package androidx.media3.common.audio;

import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;

/** A custom interface that determines the speed for media at specific timestamps. */
@UnstableApi
public interface SpeedProvider {

  /**
   * Returns the media speed from the provided timestamp.
   *
   * <p>The media speed will stay the same until {@linkplain #getNextSpeedChangeTimeUs the next
   * specified speed change}.
   *
   * @param timeUs The timestamp of the media.
   * @return The speed that the media should be played at.
   */
  float getSpeed(long timeUs);

  /**
   * Returns the timestamp of the next speed change, if there is any.
   *
   * @param timeUs A timestamp, in microseconds.
   * @return The timestamp of the next speed change, in microseconds, or {@link C#TIME_UNSET} if
   *     there is no next speed change. If {@code timeUs} corresponds to a speed change, the
   *     returned value corresponds to the following speed change.
   */
  long getNextSpeedChangeTimeUs(long timeUs);
}
