/*
 * Copyright 2020 The Android Open Source Project
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
package com.google.android.exoplayer2.device;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Information about the playback device. */
public final class DeviceInfo {

  /** Types of playback. One of {@link #PLAYBACK_TYPE_LOCAL} or {@link #PLAYBACK_TYPE_REMOTE}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target({ElementType.TYPE_PARAMETER, ElementType.TYPE_USE})
  @IntDef({
    PLAYBACK_TYPE_LOCAL,
    PLAYBACK_TYPE_REMOTE,
  })
  public @interface PlaybackType {}
  /** Playback happens on the local device (e.g. phone). */
  public static final int PLAYBACK_TYPE_LOCAL = 0;
  /** Playback happens outside of the device (e.g. a cast device). */
  public static final int PLAYBACK_TYPE_REMOTE = 1;

  /** The type of playback. */
  public final @PlaybackType int playbackType;
  /** The minimum volume that the device supports. */
  public final int minVolume;
  /** The maximum volume that the device supports. */
  public final int maxVolume;

  /** Creates device information. */
  public DeviceInfo(@PlaybackType int playbackType, int minVolume, int maxVolume) {
    this.playbackType = playbackType;
    this.minVolume = minVolume;
    this.maxVolume = maxVolume;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof DeviceInfo)) {
      return false;
    }
    DeviceInfo other = (DeviceInfo) obj;
    return playbackType == other.playbackType
        && minVolume == other.minVolume
        && maxVolume == other.maxVolume;
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + playbackType;
    result = 31 * result + minVolume;
    result = 31 * result + maxVolume;
    return result;
  }
}
