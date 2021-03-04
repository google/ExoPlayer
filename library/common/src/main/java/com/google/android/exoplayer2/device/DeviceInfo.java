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

import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.Bundleable;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Information about the playback device. */
public final class DeviceInfo implements Bundleable {

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

  /** Unknown DeviceInfo. */
  public static final DeviceInfo UNKNOWN =
      new DeviceInfo(PLAYBACK_TYPE_LOCAL, /* minVolume= */ 0, /* maxVolume= */ 0);

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

  // Bundleable implementation.

  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({FIELD_PLAYBACK_TYPE, FIELD_MIN_VOLUME, FIELD_MAX_VOLUME})
  private @interface FieldNumber {}

  private static final int FIELD_PLAYBACK_TYPE = 0;
  private static final int FIELD_MIN_VOLUME = 1;
  private static final int FIELD_MAX_VOLUME = 2;

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    bundle.putInt(keyForField(FIELD_PLAYBACK_TYPE), playbackType);
    bundle.putInt(keyForField(FIELD_MIN_VOLUME), minVolume);
    bundle.putInt(keyForField(FIELD_MAX_VOLUME), maxVolume);
    return bundle;
  }

  /** Object that can restore {@link DeviceInfo} from a {@link Bundle}. */
  public static final Creator<DeviceInfo> CREATOR =
      bundle -> {
        int playbackType =
            bundle.getInt(
                keyForField(FIELD_PLAYBACK_TYPE), /* defaultValue= */ PLAYBACK_TYPE_LOCAL);
        int minVolume = bundle.getInt(keyForField(FIELD_MIN_VOLUME), /* defaultValue= */ 0);
        int maxVolume = bundle.getInt(keyForField(FIELD_MAX_VOLUME), /* defaultValue= */ 0);
        return new DeviceInfo(playbackType, minVolume, maxVolume);
      };

  private static String keyForField(@FieldNumber int field) {
    return Integer.toString(field, Character.MAX_RADIX);
  }
}
