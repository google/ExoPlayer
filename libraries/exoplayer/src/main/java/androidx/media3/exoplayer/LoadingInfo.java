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
package androidx.media3.exoplayer;

import static androidx.media3.common.util.Assertions.checkArgument;

import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import com.google.common.base.Objects;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Information about the player state when loading is started or continued. */
@UnstableApi
public final class LoadingInfo {

  /** A builder for {@link LoadingInfo} instances. */
  public static final class Builder {
    private long playbackPositionUs;
    private float playbackSpeed;
    private long lastRebufferRealtimeMs;

    /** Creates a new instance with default values. */
    public Builder() {
      this.playbackPositionUs = C.TIME_UNSET;
      this.playbackSpeed = C.RATE_UNSET;
      this.lastRebufferRealtimeMs = C.TIME_UNSET;
    }

    private Builder(LoadingInfo loadingInfo) {
      this.playbackPositionUs = loadingInfo.playbackPositionUs;
      this.playbackSpeed = loadingInfo.playbackSpeed;
      this.lastRebufferRealtimeMs = loadingInfo.lastRebufferRealtimeMs;
    }

    /** Sets {@link LoadingInfo#playbackPositionUs}. The default is {@link C#TIME_UNSET} */
    @CanIgnoreReturnValue
    public Builder setPlaybackPositionUs(long playbackPositionUs) {
      this.playbackPositionUs = playbackPositionUs;
      return this;
    }

    /**
     * Sets {@link LoadingInfo#playbackSpeed}. The default is {@link C#RATE_UNSET}
     *
     * @throws IllegalArgumentException If {@code playbackSpeed} is not equal to {@link
     *     C#RATE_UNSET} and is non-positive.
     */
    @CanIgnoreReturnValue
    public Builder setPlaybackSpeed(float playbackSpeed) {
      checkArgument(playbackSpeed > 0 || playbackSpeed == C.RATE_UNSET);
      this.playbackSpeed = playbackSpeed;
      return this;
    }

    /**
     * Sets {@link LoadingInfo#lastRebufferRealtimeMs}. The default is {@link C#TIME_UNSET}
     *
     * @throws IllegalArgumentException If {@code lastRebufferRealtimeMs} is not equal to {@link
     *     C#TIME_UNSET} and is negative.
     */
    @CanIgnoreReturnValue
    public Builder setLastRebufferRealtimeMs(long lastRebufferRealtimeMs) {
      checkArgument(lastRebufferRealtimeMs >= 0 || lastRebufferRealtimeMs == C.TIME_UNSET);
      this.lastRebufferRealtimeMs = lastRebufferRealtimeMs;
      return this;
    }

    /** Returns a new {@link LoadingInfo} instance with the current builder values. */
    public LoadingInfo build() {
      return new LoadingInfo(this);
    }
  }

  /**
   * The current playback position in microseconds, or {@link C#TIME_UNSET} if unset. If playback of
   * the period to which this loading info belongs has not yet started, the value will be the
   * starting position in the period minus the duration of any media in previous periods still to be
   * played.
   */
  public final long playbackPositionUs;

  /**
   * The playback speed indicating the current rate of playback, or {@link C#RATE_UNSET} if playback
   * speed is not known when the load is started or continued.
   */
  public final float playbackSpeed;

  /**
   * Sets the time at which the last rebuffering occurred, in milliseconds since boot including time
   * spent in sleep.
   *
   * <p>The time base used is the same as that measured by {@link SystemClock#elapsedRealtime}.
   *
   * <p><b>Note:</b> If rebuffer events are not known when the load is started or continued, or if
   * no rebuffering has occurred, or if there have been any user interactions such as seeking or
   * stopping the player, the value will be set to {@link C#TIME_UNSET}.
   */
  public final long lastRebufferRealtimeMs;

  private LoadingInfo(Builder builder) {
    this.playbackPositionUs = builder.playbackPositionUs;
    this.playbackSpeed = builder.playbackSpeed;
    this.lastRebufferRealtimeMs = builder.lastRebufferRealtimeMs;
  }

  /** Creates a new {@link Builder}, copying the initial values from this instance. */
  public LoadingInfo.Builder buildUpon() {
    return new LoadingInfo.Builder(this);
  }

  /**
   * Checks if rebuffering has occurred since {@code realtimeMs}.
   *
   * @param realtimeMs The time to compare against, as measured by {@link
   *     SystemClock#elapsedRealtime()}.
   * @return Whether rebuffering has occurred since the provided timestamp.
   */
  public boolean rebufferedSince(long realtimeMs) {
    return lastRebufferRealtimeMs != C.TIME_UNSET
        && realtimeMs != C.TIME_UNSET
        && lastRebufferRealtimeMs >= realtimeMs;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof LoadingInfo)) {
      return false;
    }
    LoadingInfo that = (LoadingInfo) o;
    return playbackPositionUs == that.playbackPositionUs
        && playbackSpeed == that.playbackSpeed
        && lastRebufferRealtimeMs == that.lastRebufferRealtimeMs;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(playbackPositionUs, playbackSpeed, lastRebufferRealtimeMs);
  }
}
