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
package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkArgument;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/**
 * @deprecated Use {@link ExportResult} instead.
 */
@SuppressWarnings("deprecation") // Deprecated usages of own type
@Deprecated
@UnstableApi
public final class TransformationResult {
  /**
   * @deprecated Use {@link ExportResult.Builder} instead.
   */
  @Deprecated
  public static final class Builder {
    private ImmutableList<ProcessedInput> processedInputs;
    private long durationMs;
    private long fileSizeBytes;
    private int averageAudioBitrate;
    private int channelCount;
    private int sampleRate;
    @Nullable private String audioEncoderName;
    private int averageVideoBitrate;
    @Nullable ColorInfo colorInfo;
    private int height;
    private int width;
    private int videoFrameCount;
    @Nullable private String videoEncoderName;
    @Nullable private TransformationException transformationException;

    /** Creates a builder. */
    public Builder() {
      processedInputs = ImmutableList.of();
      durationMs = C.TIME_UNSET;
      fileSizeBytes = C.LENGTH_UNSET;
      averageAudioBitrate = C.RATE_UNSET_INT;
      channelCount = C.LENGTH_UNSET;
      sampleRate = C.RATE_UNSET_INT;
      averageVideoBitrate = C.RATE_UNSET_INT;
      height = C.LENGTH_UNSET;
      width = C.LENGTH_UNSET;
    }

    /** Creates a builder from an {@link ExportResult}. */
    /* package */ Builder(ExportResult exportResult) {
      ImmutableList.Builder<ProcessedInput> processedInputsBuilder = new ImmutableList.Builder<>();
      for (int i = 0; i < exportResult.processedInputs.size(); i++) {
        ExportResult.ProcessedInput processedInput = exportResult.processedInputs.get(i);
        processedInputsBuilder.add(
            new ProcessedInput(
                processedInput.mediaItem,
                processedInput.audioDecoderName,
                processedInput.videoDecoderName));
      }
      processedInputs = processedInputsBuilder.build();
      durationMs = exportResult.durationMs;
      fileSizeBytes = exportResult.fileSizeBytes;
      averageAudioBitrate = exportResult.averageAudioBitrate;
      channelCount = exportResult.channelCount;
      sampleRate = exportResult.sampleRate;
      audioEncoderName = exportResult.audioEncoderName;
      averageVideoBitrate = exportResult.averageVideoBitrate;
      colorInfo = exportResult.colorInfo;
      height = exportResult.height;
      width = exportResult.width;
      videoFrameCount = exportResult.videoFrameCount;
      videoEncoderName = exportResult.videoEncoderName;
      if (exportResult.exportException != null) {
        transformationException = new TransformationException(exportResult.exportException);
      }
    }

    /** Sets the {@linkplain ProcessedInput processed inputs}. */
    @CanIgnoreReturnValue
    public Builder setProcessedInputs(ImmutableList<ProcessedInput> processedInputs) {
      this.processedInputs = processedInputs;
      return this;
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

    /** Sets the {@link ColorInfo}. */
    @CanIgnoreReturnValue
    public Builder setColorInfo(@Nullable ColorInfo colorInfo) {
      this.colorInfo = colorInfo;
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

    /** Builds a {@link TransformationResult} instance. */
    public TransformationResult build() {
      return new TransformationResult(
          processedInputs,
          durationMs,
          fileSizeBytes,
          averageAudioBitrate,
          channelCount,
          sampleRate,
          audioEncoderName,
          averageVideoBitrate,
          colorInfo,
          height,
          width,
          videoFrameCount,
          videoEncoderName,
          transformationException);
    }
  }

  /**
   * @deprecated Use {@link ExportResult.ProcessedInput} instead.
   */
  @Deprecated
  public static final class ProcessedInput {
    /** The processed {@link MediaItem}. */
    public final MediaItem mediaItem;

    /**
     * The name of the audio decoder used to process {@code mediaItem}. This field is {@code null}
     * if no audio decoder was used.
     */
    public final @MonotonicNonNull String audioDecoderName;

    /**
     * The name of the video decoder used to process {@code mediaItem}. This field is {@code null}
     * if no video decoder was used.
     */
    public final @MonotonicNonNull String videoDecoderName;

    /** Creates an instance. */
    public ProcessedInput(
        MediaItem mediaItem, @Nullable String audioDecoderName, @Nullable String videoDecoderName) {
      this.mediaItem = mediaItem;
      this.audioDecoderName = audioDecoderName;
      this.videoDecoderName = videoDecoderName;
    }
  }

  /** The list of {@linkplain ProcessedInput processed inputs}. */
  public final ImmutableList<ProcessedInput> processedInputs;

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

  /** The sample rate of the audio, or {@link C#RATE_UNSET_INT} if unset or unknown. */
  public final int sampleRate;

  /** The name of the audio encoder used, or {@code null} if none were used. */
  @Nullable public final String audioEncoderName;

  /**
   * The average bitrate of the video track data, or {@link C#RATE_UNSET_INT} if unset or unknown.
   */
  public final int averageVideoBitrate;

  /** The {@link ColorInfo} of the video, or {@code null} if unset or unknown. */
  @Nullable public final ColorInfo colorInfo;

  /** The height of the video, or {@link C#LENGTH_UNSET} if unset or unknown. */
  public final int height;

  /** The width of the video, or {@link C#LENGTH_UNSET} if unset or unknown. */
  public final int width;

  /** The number of video frames. */
  public final int videoFrameCount;

  /** The name of the video encoder used, or {@code null} if none were used. */
  @Nullable public final String videoEncoderName;

  /**
   * The {@link TransformationException} that caused the transformation to fail, or {@code null} if
   * the transformation was a success.
   */
  @Nullable public final TransformationException transformationException;

  private TransformationResult(
      ImmutableList<ProcessedInput> processedInputs,
      long durationMs,
      long fileSizeBytes,
      int averageAudioBitrate,
      int channelCount,
      int sampleRate,
      @Nullable String audioEncoderName,
      int averageVideoBitrate,
      @Nullable ColorInfo colorInfo,
      int height,
      int width,
      int videoFrameCount,
      @Nullable String videoEncoderName,
      @Nullable TransformationException transformationException) {
    this.processedInputs = processedInputs;
    this.durationMs = durationMs;
    this.fileSizeBytes = fileSizeBytes;
    this.averageAudioBitrate = averageAudioBitrate;
    this.channelCount = channelCount;
    this.sampleRate = sampleRate;
    this.audioEncoderName = audioEncoderName;
    this.averageVideoBitrate = averageVideoBitrate;
    this.colorInfo = colorInfo;
    this.height = height;
    this.width = width;
    this.videoFrameCount = videoFrameCount;
    this.videoEncoderName = videoEncoderName;
    this.transformationException = transformationException;
  }

  public Builder buildUpon() {
    return new Builder()
        .setProcessedInputs(processedInputs)
        .setDurationMs(durationMs)
        .setFileSizeBytes(fileSizeBytes)
        .setAverageAudioBitrate(averageAudioBitrate)
        .setChannelCount(channelCount)
        .setSampleRate(sampleRate)
        .setAudioEncoderName(audioEncoderName)
        .setAverageVideoBitrate(averageVideoBitrate)
        .setColorInfo(colorInfo)
        .setHeight(height)
        .setWidth(width)
        .setVideoFrameCount(videoFrameCount)
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
    return Objects.equals(processedInputs, result.processedInputs)
        && durationMs == result.durationMs
        && fileSizeBytes == result.fileSizeBytes
        && averageAudioBitrate == result.averageAudioBitrate
        && channelCount == result.channelCount
        && sampleRate == result.sampleRate
        && Objects.equals(audioEncoderName, result.audioEncoderName)
        && averageVideoBitrate == result.averageVideoBitrate
        && Objects.equals(colorInfo, result.colorInfo)
        && height == result.height
        && width == result.width
        && videoFrameCount == result.videoFrameCount
        && Objects.equals(videoEncoderName, result.videoEncoderName)
        && Objects.equals(transformationException, result.transformationException);
  }

  @Override
  public int hashCode() {
    int result = Objects.hashCode(processedInputs);
    result = 31 * result + (int) durationMs;
    result = 31 * result + (int) fileSizeBytes;
    result = 31 * result + averageAudioBitrate;
    result = 31 * result + channelCount;
    result = 31 * result + sampleRate;
    result = 31 * result + Objects.hashCode(audioEncoderName);
    result = 31 * result + averageVideoBitrate;
    result = 31 * result + Objects.hashCode(colorInfo);
    result = 31 * result + height;
    result = 31 * result + width;
    result = 31 * result + videoFrameCount;
    result = 31 * result + Objects.hashCode(videoEncoderName);
    result = 31 * result + Objects.hashCode(transformationException);
    return result;
  }
}
