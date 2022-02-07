/*
 * Copyright 2022 The Android Open Source Project
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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkArgument;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;

/** Information about the result of a successful transformation. */
public final class TransformationResult {

  /** A builder for {@link TransformationResult} instances. */
  public static final class Builder {
    private long fileSizeBytes;
    private int averageAudioBitrate;
    private int averageVideoBitrate;

    public Builder() {
      fileSizeBytes = C.LENGTH_UNSET;
      averageAudioBitrate = C.RATE_UNSET_INT;
      averageVideoBitrate = C.RATE_UNSET_INT;
    }

    /**
     * Sets the file size in bytes.
     *
     * <p>Input must be positive or {@link C#LENGTH_UNSET}.
     */
    public Builder setFileSizeBytes(long fileSizeBytes) {
      checkArgument(fileSizeBytes > 0 || fileSizeBytes == C.LENGTH_UNSET);
      this.fileSizeBytes = fileSizeBytes;
      return this;
    }

    /**
     * Sets the average audio bitrate.
     *
     * <p>Input must be positive or {@link C#RATE_UNSET_INT}.
     */
    public Builder setAverageAudioBitrate(int averageAudioBitrate) {
      checkArgument(averageAudioBitrate > 0 || averageAudioBitrate == C.RATE_UNSET_INT);
      this.averageAudioBitrate = averageAudioBitrate;
      return this;
    }

    /**
     * Sets the average video bitrate.
     *
     * <p>Input must be positive or {@link C#RATE_UNSET_INT}.
     */
    public Builder setAverageVideoBitrate(int averageVideoBitrate) {
      checkArgument(averageVideoBitrate > 0 || averageVideoBitrate == C.RATE_UNSET_INT);
      this.averageVideoBitrate = averageVideoBitrate;
      return this;
    }

    public TransformationResult build() {
      return new TransformationResult(fileSizeBytes, averageAudioBitrate, averageVideoBitrate);
    }
  }

  /** The size of the file in bytes, or {@link C#LENGTH_UNSET} if unset or unknown. */
  public final long fileSizeBytes;
  /**
   * The average bitrate of the audio track data, or {@link C#RATE_UNSET_INT} if unset or unknown.
   */
  public final int averageAudioBitrate;
  /**
   * The average bitrate of the video track data, or {@link C#RATE_UNSET_INT} if unset or unknown.
   */
  public final int averageVideoBitrate;

  private TransformationResult(
      long fileSizeBytes, int averageAudioBitrate, int averageVideoBitrate) {
    this.fileSizeBytes = fileSizeBytes;
    this.averageAudioBitrate = averageAudioBitrate;
    this.averageVideoBitrate = averageVideoBitrate;
  }

  public Builder buildUpon() {
    return new Builder()
        .setFileSizeBytes(fileSizeBytes)
        .setAverageAudioBitrate(averageAudioBitrate)
        .setAverageVideoBitrate(averageVideoBitrate);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TransformationResult)) {
      return false;
    }
    TransformationResult result = (TransformationResult) o;
    return fileSizeBytes == result.fileSizeBytes
        && averageAudioBitrate == result.averageAudioBitrate
        && averageVideoBitrate == result.averageVideoBitrate;
  }

  @Override
  public int hashCode() {
    int result = (int) fileSizeBytes;
    result = 31 * result + averageAudioBitrate;
    result = 31 * result + averageVideoBitrate;
    return result;
  }
}
