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

import static com.google.android.exoplayer2.transformer.AndroidTestUtil.exceptionAsJsonObject;
import static com.google.android.exoplayer2.transformer.AndroidTestUtil.processedInputsAsJsonArray;

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.Format;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.json.JSONException;
import org.json.JSONObject;

/** A test only class for holding the details of a test transformation. */
public class TransformationTestResult {
  /** Represents an unset or unknown SSIM score. */
  public static final double SSIM_UNSET = -1.0d;

  /** A builder for {@link TransformationTestResult}. */
  public static class Builder {
    private final TransformationResult transformationResult;

    @Nullable private String filePath;
    private long elapsedTimeMs;
    private double ssim;
    @Nullable private FallbackDetails fallbackDetails;
    @Nullable private Exception analysisException;

    /** Creates a new {@link Builder}. */
    public Builder(TransformationResult transformationResult) {
      this.transformationResult = transformationResult;
      this.elapsedTimeMs = C.TIME_UNSET;
      this.ssim = SSIM_UNSET;
    }

    /**
     * Sets the file path of the output file.
     *
     * <p>{@code null} represents an unset or unknown value.
     *
     * @param filePath The path.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setFilePath(@Nullable String filePath) {
      this.filePath = filePath;
      return this;
    }

    /**
     * Sets the amount of time taken to perform the transformation in milliseconds. {@link
     * C#TIME_UNSET} if unset.
     *
     * <p>{@link C#TIME_UNSET} represents an unset or unknown value.
     *
     * @param elapsedTimeMs The time, in ms.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setElapsedTimeMs(long elapsedTimeMs) {
      this.elapsedTimeMs = elapsedTimeMs;
      return this;
    }

    /**
     * Sets the SSIM of the output file, compared to input file.
     *
     * <p>{@link #SSIM_UNSET} represents an unset or unknown value.
     *
     * @param ssim The structural similarity index.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setSsim(double ssim) {
      this.ssim = ssim;
      return this;
    }

    /**
     * Sets an {@link FallbackDetails} object that describes the fallbacks that occurred during
     * post-transformation analysis.
     *
     * <p>{@code null} represents no fallback was applied.
     *
     * @param fallbackDetails The {@link FallbackDetails}.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setFallbackDetails(@Nullable FallbackDetails fallbackDetails) {
      this.fallbackDetails = fallbackDetails;
      return this;
    }

    /**
     * Sets an {@link Exception} that occurred during post-transformation analysis.
     *
     * <p>{@code null} represents an unset or unknown value.
     *
     * @param analysisException The {@link Exception} thrown during analysis.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setAnalysisException(@Nullable Exception analysisException) {
      this.analysisException = analysisException;
      return this;
    }

    /** Builds the {@link TransformationTestResult} instance. */
    public TransformationTestResult build() {
      return new TransformationTestResult(
          transformationResult, filePath, elapsedTimeMs, ssim, fallbackDetails, analysisException);
    }
  }

  /** The {@link TransformationResult} of the transformation. */
  public final TransformationResult transformationResult;
  /** The path to the file created in the transformation, or {@code null} if unset. */
  @Nullable public final String filePath;
  /**
   * The amount of time taken to perform the transformation in milliseconds, or {@link C#TIME_UNSET}
   * if unset.
   */
  public final long elapsedTimeMs;
  /**
   * The average rate (per second) at which frames were processed by the transformer, or {@link
   * C#RATE_UNSET} if unset.
   */
  public final float throughputFps;
  /** The SSIM score of the transformation, or {@link #SSIM_UNSET} if unset. */
  public final double ssim;
  /**
   * The {@link FallbackDetails} describing the fallbacks that occurred doing transformation, or
   * {@code null} if no fallback occurred.
   */
  @Nullable public final FallbackDetails fallbackDetails;
  /**
   * The {@link Exception} thrown during post-transformation analysis, or {@code null} if nothing
   * was thrown.
   */
  @Nullable public final Exception analysisException;

  /** Returns a {@link JSONObject} representing all the values in {@code this}. */
  public JSONObject asJsonObject() throws JSONException {
    JSONObject jsonObject =
        new JSONObject()
            .putOpt("audioEncoderName", transformationResult.audioEncoderName)
            .putOpt(
                "fallbackDetails", fallbackDetails != null ? fallbackDetails.asJsonObject() : null)
            .putOpt("filePath", filePath)
            .putOpt("colorInfo", transformationResult.colorInfo)
            .putOpt("videoEncoderName", transformationResult.videoEncoderName)
            .putOpt(
                "testException",
                exceptionAsJsonObject(transformationResult.transformationException))
            .putOpt("analysisException", exceptionAsJsonObject(analysisException));

    if (!transformationResult.processedInputs.isEmpty()) {
      jsonObject.put(
          "processedInputs", processedInputsAsJsonArray(transformationResult.processedInputs));
    }

    if (transformationResult.averageAudioBitrate != C.RATE_UNSET_INT) {
      jsonObject.put("averageAudioBitrate", transformationResult.averageAudioBitrate);
    }
    if (transformationResult.averageVideoBitrate != C.RATE_UNSET_INT) {
      jsonObject.put("averageVideoBitrate", transformationResult.averageVideoBitrate);
    }
    if (transformationResult.channelCount != C.LENGTH_UNSET) {
      jsonObject.put("channelCount", transformationResult.channelCount);
    }
    if (transformationResult.durationMs != C.TIME_UNSET) {
      jsonObject.put("durationMs", transformationResult.durationMs);
    }
    if (elapsedTimeMs != C.TIME_UNSET) {
      jsonObject.put("elapsedTimeMs", elapsedTimeMs);
    }
    if (transformationResult.fileSizeBytes != C.LENGTH_UNSET) {
      jsonObject.put("fileSizeBytes", transformationResult.fileSizeBytes);
    }
    if (transformationResult.height != C.LENGTH_UNSET) {
      jsonObject.put("height", transformationResult.height);
    }
    if (transformationResult.pcmEncoding != Format.NO_VALUE) {
      jsonObject.put("pcmEncoding", transformationResult.pcmEncoding);
    }
    if (transformationResult.sampleRate != C.RATE_UNSET_INT) {
      jsonObject.put("sampleRate", transformationResult.sampleRate);
    }
    if (ssim != TransformationTestResult.SSIM_UNSET) {
      jsonObject.put("ssim", ssim);
    }
    if (throughputFps != C.RATE_UNSET) {
      jsonObject.put("throughputFps", throughputFps);
    }
    if (transformationResult.videoFrameCount > 0) {
      jsonObject.put("videoFrameCount", transformationResult.videoFrameCount);
    }
    if (transformationResult.width != C.LENGTH_UNSET) {
      jsonObject.put("width", transformationResult.width);
    }
    return jsonObject;
  }

  private TransformationTestResult(
      TransformationResult transformationResult,
      @Nullable String filePath,
      long elapsedTimeMs,
      double ssim,
      @Nullable FallbackDetails fallbackDetails,
      @Nullable Exception analysisException) {
    this.transformationResult = transformationResult;
    this.filePath = filePath;
    this.elapsedTimeMs = elapsedTimeMs;
    this.ssim = ssim;
    this.fallbackDetails = fallbackDetails;
    this.analysisException = analysisException;
    this.throughputFps =
        elapsedTimeMs != C.TIME_UNSET && transformationResult.videoFrameCount > 0
            ? 1000f * transformationResult.videoFrameCount / elapsedTimeMs
            : C.RATE_UNSET;
  }
}
