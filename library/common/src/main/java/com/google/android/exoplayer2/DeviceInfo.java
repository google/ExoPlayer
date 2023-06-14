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
package com.google.android.exoplayer2;

import static java.lang.annotation.ElementType.TYPE_USE;

import android.media.MediaRouter2;
import android.os.Bundle;
import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.Nullable;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Information about the playback device.
 *
 * @deprecated com.google.android.exoplayer2 is deprecated. Please migrate to androidx.media3 (which
 *     contains the same ExoPlayer code). See <a
 *     href="https://developer.android.com/guide/topics/media/media3/getting-started/migration-guide">the
 *     migration guide</a> for more details, including a script to help with the migration.
 */
@Deprecated
public final class DeviceInfo implements Bundleable {

  /** Types of playback. One of {@link #PLAYBACK_TYPE_LOCAL} or {@link #PLAYBACK_TYPE_REMOTE}. */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
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
  public static final DeviceInfo UNKNOWN = new Builder(PLAYBACK_TYPE_LOCAL).build();

  /** Builder for {@link DeviceInfo}. */
  public static final class Builder {

    private final @PlaybackType int playbackType;

    private int minVolume;
    private int maxVolume;
    @Nullable private String routingControllerId;

    /**
     * Creates the builder.
     *
     * @param playbackType The {@link PlaybackType}.
     */
    public Builder(@PlaybackType int playbackType) {
      this.playbackType = playbackType;
    }

    /**
     * Sets the minimum supported device volume.
     *
     * <p>The minimum will be set to {@code 0} if not specified.
     *
     * @param minVolume The minimum device volume.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMinVolume(@IntRange(from = 0) int minVolume) {
      this.minVolume = minVolume;
      return this;
    }

    /**
     * Sets the maximum supported device volume.
     *
     * @param maxVolume The maximum device volume, or {@code 0} to leave the maximum unspecified.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setMaxVolume(@IntRange(from = 0) int maxVolume) {
      this.maxVolume = maxVolume;
      return this;
    }

    /**
     * Sets the {@linkplain MediaRouter2.RoutingController#getId() routing controller id} of the
     * associated {@link MediaRouter2.RoutingController}.
     *
     * <p>This id allows mapping this device information to a routing controller, which provides
     * information about the media route and allows controlling its volume.
     *
     * <p>The set value must be null if {@link DeviceInfo#playbackType} is {@link
     * #PLAYBACK_TYPE_LOCAL}.
     *
     * @param routingControllerId The {@linkplain MediaRouter2.RoutingController#getId() routing
     *     controller id} of the associated {@link MediaRouter2.RoutingController}, or null to leave
     *     it unspecified.
     * @return This builder.
     */
    @CanIgnoreReturnValue
    public Builder setRoutingControllerId(@Nullable String routingControllerId) {
      Assertions.checkArgument(playbackType != PLAYBACK_TYPE_LOCAL || routingControllerId == null);
      this.routingControllerId = routingControllerId;
      return this;
    }

    /** Builds the {@link DeviceInfo}. */
    public DeviceInfo build() {
      Assertions.checkArgument(minVolume <= maxVolume);
      return new DeviceInfo(this);
    }
  }

  /** The type of playback. */
  public final @PlaybackType int playbackType;
  /** The minimum volume that the device supports. */
  @IntRange(from = 0)
  public final int minVolume;
  /** The maximum volume that the device supports, or {@code 0} if unspecified. */
  @IntRange(from = 0)
  public final int maxVolume;
  /**
   * The {@linkplain MediaRouter2.RoutingController#getId() routing controller id} of the associated
   * {@link MediaRouter2.RoutingController}, or null if unset or {@link #playbackType} is {@link
   * #PLAYBACK_TYPE_LOCAL}.
   *
   * <p>This id allows mapping this device information to a routing controller, which provides
   * information about the media route and allows controlling its volume.
   */
  @Nullable public final String routingControllerId;

  /**
   * @deprecated Use {@link Builder} instead.
   */
  @Deprecated
  public DeviceInfo(
      @PlaybackType int playbackType,
      @IntRange(from = 0) int minVolume,
      @IntRange(from = 0) int maxVolume) {
    this(new Builder(playbackType).setMinVolume(minVolume).setMaxVolume(maxVolume));
  }

  private DeviceInfo(Builder builder) {
    this.playbackType = builder.playbackType;
    this.minVolume = builder.minVolume;
    this.maxVolume = builder.maxVolume;
    this.routingControllerId = builder.routingControllerId;
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
        && maxVolume == other.maxVolume
        && Util.areEqual(routingControllerId, other.routingControllerId);
  }

  @Override
  public int hashCode() {
    int result = 17;
    result = 31 * result + playbackType;
    result = 31 * result + minVolume;
    result = 31 * result + maxVolume;
    result = 31 * result + (routingControllerId == null ? 0 : routingControllerId.hashCode());
    return result;
  }

  // Bundleable implementation.

  private static final String FIELD_PLAYBACK_TYPE = Util.intToStringMaxRadix(0);
  private static final String FIELD_MIN_VOLUME = Util.intToStringMaxRadix(1);
  private static final String FIELD_MAX_VOLUME = Util.intToStringMaxRadix(2);
  private static final String FIELD_ROUTING_CONTROLLER_ID = Util.intToStringMaxRadix(3);

  @Override
  public Bundle toBundle() {
    Bundle bundle = new Bundle();
    if (playbackType != PLAYBACK_TYPE_LOCAL) {
      bundle.putInt(FIELD_PLAYBACK_TYPE, playbackType);
    }
    if (minVolume != 0) {
      bundle.putInt(FIELD_MIN_VOLUME, minVolume);
    }
    if (maxVolume != 0) {
      bundle.putInt(FIELD_MAX_VOLUME, maxVolume);
    }
    if (routingControllerId != null) {
      bundle.putString(FIELD_ROUTING_CONTROLLER_ID, routingControllerId);
    }
    return bundle;
  }

  /** Object that can restore {@link DeviceInfo} from a {@link Bundle}. */
  public static final Creator<DeviceInfo> CREATOR =
      bundle -> {
        int playbackType =
            bundle.getInt(FIELD_PLAYBACK_TYPE, /* defaultValue= */ PLAYBACK_TYPE_LOCAL);
        int minVolume = bundle.getInt(FIELD_MIN_VOLUME, /* defaultValue= */ 0);
        int maxVolume = bundle.getInt(FIELD_MAX_VOLUME, /* defaultValue= */ 0);
        @Nullable String routingControllerId = bundle.getString(FIELD_ROUTING_CONTROLLER_ID);
        return new DeviceInfo.Builder(playbackType)
            .setMinVolume(minVolume)
            .setMaxVolume(maxVolume)
            .setRoutingControllerId(routingControllerId)
            .build();
      };
}
