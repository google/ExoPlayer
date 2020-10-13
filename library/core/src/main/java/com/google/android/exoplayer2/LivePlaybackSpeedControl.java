/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.exoplayer2;

import com.google.android.exoplayer2.MediaItem.LiveConfiguration;

/**
 * Controls the playback speed while playing live content in order to maintain a steady target live
 * offset.
 */
public interface LivePlaybackSpeedControl {

  /**
   * Updates the live configuration defined by the media.
   *
   * @param liveConfiguration The {@link LiveConfiguration} as defined by the media.
   */
  void updateLiveConfiguration(LiveConfiguration liveConfiguration);

  /**
   * Overrides the {@link #updateLiveConfiguration configured} target live offset in microseconds,
   * or {@code C.TIME_UNSET} to delete a previous override.
   *
   * <p>If no target live offset is configured by {@link #updateLiveConfiguration}, this override
   * has no effect.
   */
  void overrideTargetLiveOffsetUs(long liveOffsetUs);

  /**
   * Returns the adjusted playback speed in order get closer towards the {@link
   * #getTargetLiveOffsetUs() target live offset}.
   *
   * @param liveOffsetUs The current live offset, in microseconds.
   * @return The adjusted playback speed.
   */
  float adjustPlaybackSpeed(long liveOffsetUs);

  /**
   * Returns the current target live offset, in microseconds, or {@link C#TIME_UNSET} if no target
   * live offset is defined for the current media.
   */
  long getTargetLiveOffsetUs();
}
