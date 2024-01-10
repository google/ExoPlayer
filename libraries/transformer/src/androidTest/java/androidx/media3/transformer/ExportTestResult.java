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

import static androidx.media3.transformer.JsonUtil.exceptionAsJsonObject;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.json.JSONException;
import org.json.JSONObject;

/** A test only class for holding the details of a test export. */
public class ExportTestResult {
  /** Represents an unset or unknown SSIM score. */
  public static final double SSIM_UNSET = -1.0d;

  /** A builder for {@link ExportTestResult}. */
  public static class Builder {
    private final ExportResult exportResult;

    @Nullable private String filePath;
    private long elapsedTimeMs;
    private double ssim;
    @Nullable private FallbackDetails fallbackDetails;
    @Nullable private Exception analysisException;

    /** Creates a new {@link Builder}. */
    public Builder(ExportResult exportResult) {
      this.exportResult = exportResult;
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
     * Sets the amount of time taken to perform the export in milliseconds. {@link C#TIME_UNSET} if
     * unset.
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
     * post-export analysis.
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
     * Sets an {@link Exception} that occurred during post-export analysis.
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

    /** Builds the {@link ExportTestResult} instance. */
    public ExportTestResult build() {
      return new ExportTestResult(
          exportResult, filePath, elapsedTimeMs, ssim, fallbackDetails, analysisException);
    }
  }

  /** The {@link ExportResult} of the export. */
  public final ExportResult exportResult;

  /** The path to the file created in the export, or {@code null} if unset. */
  @Nullable public final String filePath;

  /**
   * The amount of time taken to perform the export in milliseconds, or {@link C#TIME_UNSET} if
   * unset.
   */
  public final long elapsedTimeMs;

  /**
   * The average rate (per second) at which frames were processed by the transformer, or {@link
   * C#RATE_UNSET} if unset.
   */
  public final float throughputFps;

  /** The SSIM score of the export, or {@link #SSIM_UNSET} if unset. */
  public final double ssim;

  /**
   * The {@link FallbackDetails} describing the fallbacks that occurred doing export, or {@code
   * null} if no fallback occurred.
   */
  @Nullable public final FallbackDetails fallbackDetails;

  /**
   * The {@link Exception} thrown during post-export analysis, or {@code null} if nothing was
   * thrown.
   */
  @Nullable public final Exception analysisException;

  /** Returns a {@link JSONObject} representing all the values in {@code this}. */
  public JSONObject asJsonObject() throws JSONException {
    JSONObject jsonObject =
        JsonUtil.exportResultAsJsonObject(exportResult)
            .putOpt(
                "fallbackDetails", fallbackDetails != null ? fallbackDetails.asJsonObject() : null)
            .putOpt("filePath", filePath)
            .putOpt("analysisException", exceptionAsJsonObject(analysisException));

    if (elapsedTimeMs != C.TIME_UNSET) {
      jsonObject.put("elapsedTimeMs", elapsedTimeMs);
    }
    if (ssim != ExportTestResult.SSIM_UNSET) {
      jsonObject.put("ssim", ssim);
    }
    if (throughputFps != C.RATE_UNSET) {
      jsonObject.put("throughputFps", throughputFps);
    }
    return jsonObject;
  }

  private ExportTestResult(
      ExportResult exportResult,
      @Nullable String filePath,
      long elapsedTimeMs,
      double ssim,
      @Nullable FallbackDetails fallbackDetails,
      @Nullable Exception analysisException) {
    this.exportResult = exportResult;
    this.filePath = filePath;
    this.elapsedTimeMs = elapsedTimeMs;
    this.ssim = ssim;
    this.fallbackDetails = fallbackDetails;
    this.analysisException = analysisException;
    this.throughputFps =
        elapsedTimeMs != C.TIME_UNSET && exportResult.videoFrameCount > 0
            ? 1000f * exportResult.videoFrameCount / elapsedTimeMs
            : C.RATE_UNSET;
  }
}
