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
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Objects;

/** Information about the result of a transformation. */
public final class TransformationResult {

  /** A builder for {@link TransformationResult} instances. */
  public static final class Builder {
    private long durationMs;
    private long fileSizeBytes;
    private int averageAudioBitrate;
    @Nullable private String audioDecoderName;
    @Nullable private String audioEncoderName;
    private int averageVideoBitrate;
    private int videoFrameCount;
    @Nullable private String videoDecoderName;
    @Nullable private String videoEncoderName;
    @Nullable private TransformationException transformationException;

    public Builder() {
      durationMs = C.TIME_UNSET;
      fileSizeBytes = C.LENGTH_UNSET;
      averageAudioBitrate = C.RATE_UNSET_INT;
      averageVideoBitrate = C.RATE_UNSET_INT;
    }

    /**
     * Sets the duration of the output in milliseconds.
     *
     * <p>Must be positive or {@link C#TIME_UNSET}.
     */
    @CanIgnoreReturnValue
    public Builder setDurationMs(long durationMs) {
      checkArgument(durationMs >= 0 || durationMs == C.TIME_UNSET);
      this.durationMs = durationMs;
      return this;
    }

    /**
     * Sets the file size in bytes.
     *
     * <p>Must be positive or {@link C#LENGTH_UNSET}.
     */
    @CanIgnoreReturnValue
    public Builder setFileSizeBytes(long fileSizeBytes) {
      checkArgument(fileSizeBytes > 0 || fileSizeBytes == C.LENGTH_UNSET);
      this.fileSizeBytes = fileSizeBytes;
      return this;
    }

    /**
     * Sets the average audio bitrate.
     *
     * <p>Must be positive or {@link C#RATE_UNSET_INT}.
     */
    @CanIgnoreReturnValue
    public Builder setAverageAudioBitrate(int averageAudioBitrate) {
      checkArgument(averageAudioBitrate > 0 || averageAudioBitrate == C.RATE_UNSET_INT);
      this.averageAudioBitrate = averageAudioBitrate;
      return this;
    }

    /** Sets the name of the audio decoder used. */
    @CanIgnoreReturnValue
    public Builder setAudioDecoderName(@Nullable String audioDecoderName) {
      this.audioDecoderName = audioDecoderName;
      return this;
    }

    /** Sets the name of the audio encoder used. */
    @CanIgnoreReturnValue
    public Builder setAudioEncoderName(@Nullable String audioEncoderName) {
      this.audioEncoderName = audioEncoderName;
      return this;
    }

    /**
     * Sets the average video bitrate.
     *
     * <p>Must be positive or {@link C#RATE_UNSET_INT}.
     */
    @CanIgnoreReturnValue
    public Builder setAverageVideoBitrate(int averageVideoBitrate) {
      checkArgument(averageVideoBitrate > 0 || averageVideoBitrate == C.RATE_UNSET_INT);
      this.averageVideoBitrate = averageVideoBitrate;
      return this;
    }

    /**
     * Sets the number of video frames.
     *
     * <p>Must be positive or {@code 0}.
     */
    @CanIgnoreReturnValue
    public Builder setVideoFrameCount(int videoFrameCount) {
      checkArgument(videoFrameCount >= 0);
      this.videoFrameCount = videoFrameCount;
      return this;
    }

    /** Sets the name of the video decoder used. */
    @CanIgnoreReturnValue
    public Builder setVideoDecoderName(@Nullable String videoDecoderName) {
      this.videoDecoderName = videoDecoderName;
      return this;
    }

    /** Sets the name of the video encoder used. */
    @CanIgnoreReturnValue
    public Builder setVideoEncoderName(@Nullable String videoEncoderName) {
      this.videoEncoderName = videoEncoderName;
      return this;
    }

    /** Sets the {@link TransformationException} that caused the transformation to fail. */
    @CanIgnoreReturnValue
    public Builder setTransformationException(
        @Nullable TransformationException transformationException) {
      this.transformationException = transformationException;
      return this;
    }

    public TransformationResult build() {
      return new TransformationResult(
          durationMs,
          fileSizeBytes,
          averageAudioBitrate,
          audioDecoderName,
          audioEncoderName,
          averageVideoBitrate,
          videoFrameCount,
          videoDecoderName,
          videoEncoderName,
          transformationException);
    }
  }

  /** The duration of the file in milliseconds, or {@link C#TIME_UNSET} if unset or unknown. */
  public final long durationMs;
  /** The size of the file in bytes, or {@link C#LENGTH_UNSET} if unset or unknown. */
  public final long fileSizeBytes;

  /**
   * The average bitrate of the audio track data, or {@link C#RATE_UNSET_INT} if unset or unknown.
   */
  public final int averageAudioBitrate;
  /** The name of the audio decoder used, or {@code null} if none were used. */
  @Nullable public final String audioDecoderName;
  /** The name of the audio encoder used, or {@code null} if none were used. */
  @Nullable public final String audioEncoderName;

  /**
   * The average bitrate of the video track data, or {@link C#RATE_UNSET_INT} if unset or unknown.
   */
  public final int averageVideoBitrate;
  /** The number of video frames. */
  public final int videoFrameCount;
  /** The name of the video decoder used, or {@code null} if none were used. */
  @Nullable public final String videoDecoderName;
  /** The name of the video encoder used, or {@code null} if none were used. */
  @Nullable public final String videoEncoderName;

  /**
   * The {@link TransformationException} that caused the transformation to fail, or {@code null} if
   * the transformation was a success.
   */
  @Nullable public final TransformationException transformationException;

  private TransformationResult(
      long durationMs,
      long fileSizeBytes,
      int averageAudioBitrate,
      @Nullable String audioDecoderName,
      @Nullable String audioEncoderName,
      int averageVideoBitrate,
      int videoFrameCount,
      @Nullable String videoDecoderName,
      @Nullable String videoEncoderName,
      @Nullable TransformationException transformationException) {
    this.durationMs = durationMs;
    this.fileSizeBytes = fileSizeBytes;
    this.averageAudioBitrate = averageAudioBitrate;
    this.audioDecoderName = audioDecoderName;
    this.audioEncoderName = audioEncoderName;
    this.averageVideoBitrate = averageVideoBitrate;
    this.videoFrameCount = videoFrameCount;
    this.videoDecoderName = videoDecoderName;
    this.videoEncoderName = videoEncoderName;
    this.transformationException = transformationException;
  }

  public Builder buildUpon() {
    return new Builder()
        .setDurationMs(durationMs)
        .setFileSizeBytes(fileSizeBytes)
        .setAverageAudioBitrate(averageAudioBitrate)
        .setAudioDecoderName(audioDecoderName)
        .setAudioEncoderName(audioEncoderName)
        .setAverageVideoBitrate(averageVideoBitrate)
        .setVideoFrameCount(videoFrameCount)
        .setVideoDecoderName(videoDecoderName)
        .setVideoEncoderName(videoEncoderName)
        .setTransformationException(transformationException);
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
    return durationMs == result.durationMs
        && fileSizeBytes == result.fileSizeBytes
        && averageAudioBitrate == result.averageAudioBitrate
        && Objects.equals(audioDecoderName, result.audioDecoderName)
        && Objects.equals(audioEncoderName, result.audioEncoderName)
        && averageVideoBitrate == result.averageVideoBitrate
        && videoFrameCount == result.videoFrameCount
        && Objects.equals(videoDecoderName, result.videoDecoderName)
        && Objects.equals(videoEncoderName, result.videoEncoderName)
        && Objects.equals(transformationException, result.transformationException);
  }

  @Override
  public int hashCode() {
    int result = (int) durationMs;
    result = 31 * result + (int) fileSizeBytes;
    result = 31 * result + averageAudioBitrate;
    result = 31 * result + Objects.hashCode(audioDecoderName);
    result = 31 * result + Objects.hashCode(audioEncoderName);
    result = 31 * result + averageVideoBitrate;
    result = 31 * result + videoFrameCount;
    result = 31 * result + Objects.hashCode(videoDecoderName);
    result = 31 * result + Objects.hashCode(videoEncoderName);
    result = 31 * result + Objects.hashCode(transformationException);
    return result;
  }
}
