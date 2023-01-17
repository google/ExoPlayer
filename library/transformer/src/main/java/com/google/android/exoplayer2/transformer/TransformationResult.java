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
import com.google.android.exoplayer2.Format;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Objects;

/** Information about the result of a transformation. */
public final class TransformationResult {
  /** A builder for {@link TransformationResult} instances. */
  public static final class Builder {
    private long durationMs;
    private long fileSizeBytes;
    private int averageAudioBitrate;
    private int channelCount;
    private @C.PcmEncoding int pcmEncoding;
    private int sampleRate;
    @Nullable private String audioDecoderName;
    @Nullable private String audioEncoderName;
    private int averageVideoBitrate;
    private int height;
    private int width;
    private int videoFrameCount;
    @Nullable private String videoDecoderName;
    @Nullable private String videoEncoderName;
    @Nullable private TransformationException transformationException;

    public Builder() {
      durationMs = C.TIME_UNSET;
      fileSizeBytes = C.LENGTH_UNSET;
      averageAudioBitrate = C.RATE_UNSET_INT;
      channelCount = C.LENGTH_UNSET;
      pcmEncoding = Format.NO_VALUE;
      sampleRate = C.RATE_UNSET_INT;
      averageVideoBitrate = C.RATE_UNSET_INT;
      height = C.LENGTH_UNSET;
      width = C.LENGTH_UNSET;
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

    /**
     * Sets the channel count.
     *
     * <p>Must be positive or {@link C#LENGTH_UNSET}.
     */
    @CanIgnoreReturnValue
    public Builder setChannelCount(int channelCount) {
      checkArgument(channelCount > 0 || channelCount == C.LENGTH_UNSET);
      this.channelCount = channelCount;
      return this;
    }

    /** Sets the {@link C.PcmEncoding}. */
    @CanIgnoreReturnValue
    public Builder setPcmEncoding(@C.PcmEncoding int pcmEncoding) {
      this.pcmEncoding = pcmEncoding;
      return this;
    }

    /**
     * Sets the sample rate.
     *
     * <p>Must be positive or {@link C#RATE_UNSET_INT}.
     */
    @CanIgnoreReturnValue
    public Builder setSampleRate(int sampleRate) {
      checkArgument(sampleRate > 0 || sampleRate == C.RATE_UNSET_INT);
      this.sampleRate = sampleRate;
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
     * Sets the height.
     *
     * <p>Must be positive or {@link C#LENGTH_UNSET}.
     */
    @CanIgnoreReturnValue
    public Builder setHeight(int height) {
      checkArgument(height > 0 || height == C.LENGTH_UNSET);
      this.height = height;
      return this;
    }

    /**
     * Sets the width.
     *
     * <p>Must be positive or {@link C#LENGTH_UNSET}.
     */
    @CanIgnoreReturnValue
    public Builder setWidth(int width) {
      checkArgument(width > 0 || width == C.LENGTH_UNSET);
      this.width = width;
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
          channelCount,
          pcmEncoding,
          sampleRate,
          audioDecoderName,
          audioEncoderName,
          averageVideoBitrate,
          height,
          width,
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
  /** The channel count of the audio, or {@link C#LENGTH_UNSET} if unset or unknown. */
  public final int channelCount;
  /* The {@link C.PcmEncoding} of the audio, or {@link Format#NO_VALUE} if unset or unknown. */
  public final @C.PcmEncoding int pcmEncoding;
  /** The sample rate of the audio, or {@link C#RATE_UNSET_INT} if unset or unknown. */
  public final int sampleRate;
  /** The name of the audio decoder used, or {@code null} if none were used. */
  @Nullable public final String audioDecoderName;
  /** The name of the audio encoder used, or {@code null} if none were used. */
  @Nullable public final String audioEncoderName;

  /**
   * The average bitrate of the video track data, or {@link C#RATE_UNSET_INT} if unset or unknown.
   */
  public final int averageVideoBitrate;
  /** The height of the video, or {@link C#LENGTH_UNSET} if unset or unknown. */
  public final int height;
  /** The width of the video, or {@link C#LENGTH_UNSET} if unset or unknown. */
  public final int width;
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
      int channelCount,
      @C.PcmEncoding int pcmEncoding,
      int sampleRate,
      @Nullable String audioDecoderName,
      @Nullable String audioEncoderName,
      int averageVideoBitrate,
      int height,
      int width,
      int videoFrameCount,
      @Nullable String videoDecoderName,
      @Nullable String videoEncoderName,
      @Nullable TransformationException transformationException) {
    this.durationMs = durationMs;
    this.fileSizeBytes = fileSizeBytes;
    this.averageAudioBitrate = averageAudioBitrate;
    this.channelCount = channelCount;
    this.pcmEncoding = pcmEncoding;
    this.sampleRate = sampleRate;
    this.audioDecoderName = audioDecoderName;
    this.audioEncoderName = audioEncoderName;
    this.averageVideoBitrate = averageVideoBitrate;
    this.height = height;
    this.width = width;
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
        .setChannelCount(channelCount)
        .setPcmEncoding(pcmEncoding)
        .setSampleRate(sampleRate)
        .setAudioDecoderName(audioDecoderName)
        .setAudioEncoderName(audioEncoderName)
        .setAverageVideoBitrate(averageVideoBitrate)
        .setHeight(height)
        .setWidth(width)
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
        && channelCount == result.channelCount
        && pcmEncoding == result.pcmEncoding
        && sampleRate == result.sampleRate
        && Objects.equals(audioDecoderName, result.audioDecoderName)
        && Objects.equals(audioEncoderName, result.audioEncoderName)
        && averageVideoBitrate == result.averageVideoBitrate
        && height == result.height
        && width == result.width
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
    result = 31 * result + channelCount;
    result = 31 * result + pcmEncoding;
    result = 31 * result + sampleRate;
    result = 31 * result + Objects.hashCode(audioDecoderName);
    result = 31 * result + Objects.hashCode(audioEncoderName);
    result = 31 * result + averageVideoBitrate;
    result = 31 * result + height;
    result = 31 * result + width;
    result = 31 * result + videoFrameCount;
    result = 31 * result + Objects.hashCode(videoDecoderName);
    result = 31 * result + Objects.hashCode(videoEncoderName);
    result = 31 * result + Objects.hashCode(transformationException);
    return result;
  }
}
