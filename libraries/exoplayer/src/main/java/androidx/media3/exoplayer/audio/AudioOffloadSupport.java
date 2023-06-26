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
package androidx.media3.exoplayer.audio;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Represents the support capabilities for audio offload playback. */
@UnstableApi
public final class AudioOffloadSupport {

  /** The default configuration. */
  public static final AudioOffloadSupport DEFAULT_UNSUPPORTED =
      new AudioOffloadSupport.Builder().build();

  /** A builder to create {@link AudioOffloadSupport} instances. */
  public static final class Builder {
    /** Whether the format is supported with offload playback. */
    private boolean isFormatSupported;

    /** Whether playback of the format is supported with gapless transitions. */
    private boolean isGaplessSupported;

    /** Whether playback of the format is supported with speed changes. */
    private boolean isSpeedChangeSupported;

    public Builder() {}

    public Builder(AudioOffloadSupport audioOffloadSupport) {
      isFormatSupported = audioOffloadSupport.isFormatSupported;
      isGaplessSupported = audioOffloadSupport.isGaplessSupported;
      isSpeedChangeSupported = audioOffloadSupport.isSpeedChangeSupported;
    }

    /**
     * Sets if media format is supported in offload playback.
     *
     * <p>Default is {@code false}.
     */
    @CanIgnoreReturnValue
    public Builder setIsFormatSupported(boolean isFormatSupported) {
      this.isFormatSupported = isFormatSupported;
      return this;
    }

    /**
     * Sets whether playback of the format is supported with gapless transitions.
     *
     * <p>Default is {@code false}.
     */
    @CanIgnoreReturnValue
    public Builder setIsGaplessSupported(boolean isGaplessSupported) {
      this.isGaplessSupported = isGaplessSupported;
      return this;
    }

    /**
     * Sets whether playback of the format is supported with speed changes.
     *
     * <p>Default is {@code false}.
     */
    @CanIgnoreReturnValue
    public Builder setIsSpeedChangeSupported(boolean isSpeedChangeSupported) {
      this.isSpeedChangeSupported = isSpeedChangeSupported;
      return this;
    }

    /**
     * Builds the {@link AudioOffloadSupport}.
     *
     * @throws IllegalStateException If either {@link #isGaplessSupported} or {@link
     *     #isSpeedChangeSupported} are true when {@link #isFormatSupported} is false.
     */
    public AudioOffloadSupport build() {
      if (!isFormatSupported && (isGaplessSupported || isSpeedChangeSupported)) {
        throw new IllegalStateException(
            "Secondary offload attribute fields are true but primary isFormatSupported is false");
      }
      return new AudioOffloadSupport(this);
    }
  }

  /** Whether the format is supported with offload playback. */
  public final boolean isFormatSupported;

  /** Whether playback of the format is supported with gapless transitions. */
  public final boolean isGaplessSupported;

  /** Whether playback of the format is supported with speed changes. */
  public final boolean isSpeedChangeSupported;

  private AudioOffloadSupport(AudioOffloadSupport.Builder builder) {
    this.isFormatSupported = builder.isFormatSupported;
    this.isGaplessSupported = builder.isGaplessSupported;
    this.isSpeedChangeSupported = builder.isSpeedChangeSupported;
  }

  /** Creates a new {@link Builder}, copying the initial values from this instance. */
  public Builder buildUpon() {
    return new Builder(this);
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    AudioOffloadSupport other = (AudioOffloadSupport) obj;
    return isFormatSupported == other.isFormatSupported
        && isGaplessSupported == other.isGaplessSupported
        && isSpeedChangeSupported == other.isSpeedChangeSupported;
  }

  @Override
  public int hashCode() {
    int hashCode = (isFormatSupported ? 1 : 0) << 2;
    hashCode += (isGaplessSupported ? 1 : 0) << 1;
    hashCode += (isSpeedChangeSupported ? 1 : 0);
    return hashCode;
  }
}
