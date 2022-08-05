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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.C;
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
    @Nullable private Exception analysisException;
    private long elapsedTimeMs;
    private double ssim;

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
          transformationResult, filePath, elapsedTimeMs, ssim, analysisException);
    }
  }

  public final TransformationResult transformationResult;
  @Nullable public final String filePath;
  /**
   * The average rate (per second) at which frames are processed by the transformer, or {@link
   * C#RATE_UNSET} if unset or unknown.
   */
  public final float throughputFps;
  /**
   * The amount of time taken to perform the transformation in milliseconds. {@link C#TIME_UNSET} if
   * unset.
   */
  public final long elapsedTimeMs;
  /** The SSIM score of the transformation, {@link #SSIM_UNSET} if unavailable. */
  public final double ssim;
  /**
   * The {@link Exception} that was thrown during post-transformation analysis, or {@code null} if
   * nothing was thrown.
   */
  @Nullable public final Exception analysisException;

  /** Returns a {@link JSONObject} representing all the values in {@code this}. */
  public JSONObject asJsonObject() throws JSONException {
    JSONObject jsonObject = new JSONObject();
    if (transformationResult.durationMs != C.LENGTH_UNSET) {
      jsonObject.put("durationMs", transformationResult.durationMs);
    }
    if (transformationResult.fileSizeBytes != C.LENGTH_UNSET) {
      jsonObject.put("fileSizeBytes", transformationResult.fileSizeBytes);
    }
    if (transformationResult.averageAudioBitrate != C.RATE_UNSET_INT) {
      jsonObject.put("averageAudioBitrate", transformationResult.averageAudioBitrate);
    }
    if (transformationResult.averageVideoBitrate != C.RATE_UNSET_INT) {
      jsonObject.put("averageVideoBitrate", transformationResult.averageVideoBitrate);
    }
    if (transformationResult.videoFrameCount > 0) {
      jsonObject.put("videoFrameCount", transformationResult.videoFrameCount);
    }
    if (throughputFps != C.RATE_UNSET) {
      jsonObject.put("throughputFps", throughputFps);
    }
    if (elapsedTimeMs != C.TIME_UNSET) {
      jsonObject.put("elapsedTimeMs", elapsedTimeMs);
    }
    if (ssim != TransformationTestResult.SSIM_UNSET) {
      jsonObject.put("ssim", ssim);
    }
    if (analysisException != null) {
      jsonObject.put("analysisException", AndroidTestUtil.exceptionAsJsonObject(analysisException));
    }
    return jsonObject;
  }

  private TransformationTestResult(
      TransformationResult transformationResult,
      @Nullable String filePath,
      long elapsedTimeMs,
      double ssim,
      @Nullable Exception analysisException) {
    this.transformationResult = transformationResult;
    this.filePath = filePath;
    this.elapsedTimeMs = elapsedTimeMs;
    this.ssim = ssim;
    this.analysisException = analysisException;
    this.throughputFps =
        elapsedTimeMs != C.TIME_UNSET && transformationResult.videoFrameCount > 0
            ? 1000f * transformationResult.videoFrameCount / elapsedTimeMs
            : C.RATE_UNSET;
  }
}
