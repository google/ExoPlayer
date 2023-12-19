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
import static java.lang.annotation.ElementType.TYPE_USE;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

/** Information about the result of an export. */
@UnstableApi
public final class ExportResult {
  /** A builder for {@link ExportResult} instances. */
  public static final class Builder {
    private ImmutableList.Builder<ProcessedInput> processedInputsBuilder;
    private long durationMs;
    private long fileSizeBytes;
    private int averageAudioBitrate;
    private int channelCount;
    private int sampleRate;
    @Nullable private String audioEncoderName;
    private int averageVideoBitrate;
    @Nullable private ColorInfo colorInfo;
    private int height;
    private int width;
    private int videoFrameCount;
    @Nullable private String videoEncoderName;
    private @OptimizationResult int optimizationResult;
    @Nullable private ExportException exportException;

    /** Creates a builder. */
    @SuppressWarnings({"initialization.fields.uninitialized", "nullness:method.invocation"})
    public Builder() {
      reset();
    }

    /** Adds {@linkplain ProcessedInput processed inputs} to the {@link ProcessedInput} list. */
    @CanIgnoreReturnValue
    public Builder addProcessedInputs(List<ProcessedInput> processedInputs) {
      this.processedInputsBuilder.addAll(processedInputs);
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
      checkArgument(
          fileSizeBytes > 0 || fileSizeBytes == C.LENGTH_UNSET,
          "Invalid file size = " + fileSizeBytes);
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

    /**
     * Sets {@link OptimizationResult} to indicate an optimization as been successful, or has failed
     * and normal export proceeded instead.
     *
     * <p>The default value is {@link #OPTIMIZATION_NONE}.
     *
     * @param optimizationResult The {@link OptimizationResult}.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setOptimizationResult(@OptimizationResult int optimizationResult) {
      this.optimizationResult = optimizationResult;
      return this;
    }

    /** Sets the {@link ExportException} that caused the export to fail. */
    @CanIgnoreReturnValue
    public Builder setExportException(@Nullable ExportException exportException) {
      this.exportException = exportException;
      return this;
    }

    /** Builds an {@link ExportResult} instance. */
    public ExportResult build() {
      return new ExportResult(
          processedInputsBuilder.build(),
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
          optimizationResult,
          exportException);
    }

    /** Resets all the fields to their default values. */
    public void reset() {
      processedInputsBuilder = new ImmutableList.Builder<>();
      durationMs = C.TIME_UNSET;
      fileSizeBytes = C.LENGTH_UNSET;
      averageAudioBitrate = C.RATE_UNSET_INT;
      channelCount = C.LENGTH_UNSET;
      sampleRate = C.RATE_UNSET_INT;
      audioEncoderName = null;
      averageVideoBitrate = C.RATE_UNSET_INT;
      colorInfo = null;
      height = C.LENGTH_UNSET;
      width = C.LENGTH_UNSET;
      videoFrameCount = 0;
      videoEncoderName = null;
      optimizationResult = OPTIMIZATION_NONE;
      exportException = null;
    }
  }

  /** An input entirely or partially processed. */
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

  /**
   * Specifies the result of an optimized operation, such as {@link
   * Transformer.Builder#experimentalSetTrimOptimizationEnabled}. One of:
   *
   * <ul>
   *   <li>{@link #OPTIMIZATION_NONE}
   *   <li>{@link #OPTIMIZATION_SUCCEEDED}
   *   <li>{@link #OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM}
   *   <li>{@link #OPTIMIZATION_ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED}
   *   <li>{@link #OPTIMIZATION_ABANDONED_OTHER}
   *   <li>{@link #OPTIMIZATION_FAILED_EXTRACTION_FAILED}
   *   <li>{@link #OPTIMIZATION_FAILED_FORMAT_MISMATCH}
   * </ul>
   */
  @Documented
  @Retention(RetentionPolicy.SOURCE)
  @Target(TYPE_USE)
  @IntDef({
    OPTIMIZATION_NONE,
    OPTIMIZATION_SUCCEEDED,
    OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM,
    OPTIMIZATION_ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED,
    OPTIMIZATION_ABANDONED_OTHER,
    OPTIMIZATION_FAILED_EXTRACTION_FAILED,
    OPTIMIZATION_FAILED_FORMAT_MISMATCH
  })
  @interface OptimizationResult {}

  /** No optimizations were applied since none were requested. */
  public static final int OPTIMIZATION_NONE = 0;

  /** The optimization was successfully applied. */
  public static final int OPTIMIZATION_SUCCEEDED = 1;

  /**
   * {@linkplain Transformer.Builder#experimentalSetTrimOptimizationEnabled Trim optimization was
   * requested}, but it would not improve performance because of key frame placement. The
   * optimization was abandoned and normal export proceeded.
   *
   * <p>The trim optimization does not improve performance when the requested {@link
   * androidx.media3.common.MediaItem.ClippingConfiguration#startPositionUs} is at a key frame, or
   * when there are no key frames between the requested {@link
   * androidx.media3.common.MediaItem.ClippingConfiguration#startPositionUs} and {@link
   * androidx.media3.common.MediaItem.ClippingConfiguration#endPositionUs}
   */
  public static final int OPTIMIZATION_ABANDONED_KEYFRAME_PLACEMENT_OPTIMAL_FOR_TRIM = 2;

  /**
   * {@linkplain Transformer.Builder#experimentalSetTrimOptimizationEnabled Trim optimization was
   * requested}, but it would not improve performance because another transformation that requires
   * transcoding was also requested. The optimization was abandoned and normal export proceeded.
   */
  public static final int OPTIMIZATION_ABANDONED_TRIM_AND_TRANSCODING_TRANSFORMATION_REQUESTED = 3;

  /**
   * The requested optimization would not improve performance for a reason other than the ones
   * specified above, so it was abandoned. Normal export proceeded.
   */
  public static final int OPTIMIZATION_ABANDONED_OTHER = 4;

  /**
   * The optimization failed because mp4 metadata extraction failed (possibly because the file
   * wasn't an mp4 file). Normal export proceeded.
   */
  public static final int OPTIMIZATION_FAILED_EXTRACTION_FAILED = 5;

  /**
   * The optimization failed because the format between the two parts of the media to be put
   * together did not match. Normal export proceeded.
   */
  public static final int OPTIMIZATION_FAILED_FORMAT_MISMATCH = 6;

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

  /** The result of any requested optimizations. */
  public final @OptimizationResult int optimizationResult;

  /**
   * The {@link ExportException} that caused the export to fail, or {@code null} if the export was a
   * success.
   */
  @Nullable public final ExportException exportException;

  private ExportResult(
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
      @OptimizationResult int optimizationResult,
      @Nullable ExportException exportException) {
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
    this.optimizationResult = optimizationResult;
    this.exportException = exportException;
  }

  public Builder buildUpon() {
    return new Builder()
        .addProcessedInputs(processedInputs)
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
        .setOptimizationResult(optimizationResult)
        .setExportException(exportException);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ExportResult)) {
      return false;
    }
    ExportResult result = (ExportResult) o;
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
        && optimizationResult == result.optimizationResult
        && Objects.equals(exportException, result.exportException);
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
    result = 31 * result + optimizationResult;
    result = 31 * result + Objects.hashCode(exportException);
    return result;
  }
}
