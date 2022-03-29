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

import static com.google.android.exoplayer2.util.Assertions.checkNotNull;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.SystemClock;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.json.JSONException;
import org.json.JSONObject;

/** An android instrumentation test runner for {@link Transformer}. */
public class TransformerAndroidTestRunner {
  private static final String TAG_PREFIX = "TransformerAndroidTest_";

  /** The default transformation timeout value. */
  public static final int DEFAULT_TIMEOUT_SECONDS = 120;

  /** A {@link Builder} for {@link TransformerAndroidTestRunner} instances. */
  public static class Builder {
    private final Context context;
    private final Transformer transformer;
    private boolean calculateSsim;
    private int timeoutSeconds;
    private boolean suppressAnalysisExceptions;

    /**
     * Creates a {@link Builder}.
     *
     * @param context The {@link Context}.
     * @param transformer The {@link Transformer} that performs the transformation.
     */
    public Builder(Context context, Transformer transformer) {
      this.context = context;
      this.transformer = transformer;
      this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Sets the timeout in seconds for a single transformation. An exception is thrown when this is
     * exceeded.
     *
     * <p>The default value is {@link #DEFAULT_TIMEOUT_SECONDS}.
     *
     * @param timeoutSeconds The timeout.
     * @return This {@link Builder}.
     */
    public Builder setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
      return this;
    }

    /**
     * Sets whether to calculate the SSIM of the transformation output.
     *
     * <p>The calculation involves decoding and comparing both the input and the output video.
     * Consequently this calculation is not cost-free. Requires the input and output video to be the
     * same size.
     *
     * <p>The default value is {@code false}.
     *
     * @param calculateSsim Whether to calculate SSIM.
     * @return This {@link Builder}.
     */
    public Builder setCalculateSsim(boolean calculateSsim) {
      this.calculateSsim = calculateSsim;
      return this;
    }

    /**
     * Sets whether the runner should suppress any {@link Exception} that occurs as a result of
     * post-transformation analysis, such as SSIM calculation.
     *
     * <p>Regardless of this value, analysis exceptions are attached to the analysis file.
     *
     * <p>It's recommended to add a comment explaining why this suppression is needed, ideally with
     * a bug number.
     *
     * <p>The default value is {@code false}.
     *
     * @param suppressAnalysisExceptions Whether to suppress analysis exceptions.
     * @return This {@link Builder}.
     */
    public Builder setSuppressAnalysisExceptions(boolean suppressAnalysisExceptions) {
      this.suppressAnalysisExceptions = suppressAnalysisExceptions;
      return this;
    }

    /** Builds the {@link TransformerAndroidTestRunner}. */
    public TransformerAndroidTestRunner build() {
      return new TransformerAndroidTestRunner(
          context, transformer, timeoutSeconds, calculateSsim, suppressAnalysisExceptions);
    }
  }

  private final Context context;
  private final Transformer transformer;
  private final int timeoutSeconds;
  private final boolean calculateSsim;
  private final boolean suppressAnalysisExceptions;

  private TransformerAndroidTestRunner(
      Context context,
      Transformer transformer,
      int timeoutSeconds,
      boolean calculateSsim,
      boolean suppressAnalysisExceptions) {
    this.context = context;
    this.transformer = transformer;
    this.timeoutSeconds = timeoutSeconds;
    this.calculateSsim = calculateSsim;
    this.suppressAnalysisExceptions = suppressAnalysisExceptions;
  }

  /**
   * Transforms the {@code uriString}, saving a summary of the transformation to the application
   * cache.
   *
   * @param testId An identifier for the test.
   * @param uriString The uri (as a {@link String}) of the file to transform.
   * @return The {@link TransformationTestResult}.
   * @throws Exception The cause of the transformation not completing.
   */
  public TransformationTestResult run(String testId, String uriString) throws Exception {
    JSONObject resultJson = new JSONObject();
    try {
      TransformationTestResult transformationTestResult = runInternal(testId, uriString);
      resultJson.put("transformationResult", getTestResultJson(transformationTestResult));
      if (!suppressAnalysisExceptions && transformationTestResult.analysisException != null) {
        throw transformationTestResult.analysisException;
      }
      return transformationTestResult;
    } catch (Exception e) {
      resultJson.put("exception", getExceptionJson(e));
      throw e;
    } finally {
      writeTestSummaryToFile(context, testId, resultJson);
    }
  }

  /**
   * Transforms the {@code uriString}.
   *
   * @param testId An identifier for the test.
   * @param uriString The uri (as a {@link String}) of the file to transform.
   * @return The {@link TransformationTestResult}.
   * @throws IOException If an error occurs opening the output file for writing
   * @throws TimeoutException If the transformation takes longer than the {@link #timeoutSeconds}.
   * @throws InterruptedException If the thread is interrupted whilst waiting for transformer to
   *     complete.
   * @throws TransformationException If an exception occurs as a result of the transformation.
   * @throws IllegalArgumentException If the path is invalid.
   * @throws IllegalStateException If an unexpected exception occurs when starting a transformation.
   */
  private TransformationTestResult runInternal(String testId, String uriString)
      throws InterruptedException, IOException, TimeoutException, TransformationException {
    AtomicReference<@NullableType TransformationException> transformationExceptionReference =
        new AtomicReference<>();
    AtomicReference<@NullableType Exception> unexpectedExceptionReference = new AtomicReference<>();
    AtomicReference<@NullableType TransformationResult> transformationResultReference =
        new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    long startTimeMs = SystemClock.DEFAULT.elapsedRealtime();

    Transformer testTransformer =
        transformer
            .buildUpon()
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onTransformationCompleted(
                      MediaItem inputMediaItem, TransformationResult result) {
                    transformationResultReference.set(result);
                    countDownLatch.countDown();
                  }

                  @Override
                  public void onTransformationError(
                      MediaItem inputMediaItem, TransformationException exception) {
                    transformationExceptionReference.set(exception);
                    countDownLatch.countDown();
                  }
                })
            .build();

    Uri uri = Uri.parse(uriString);
    File outputVideoFile =
        AndroidTestUtil.createExternalCacheFile(context, /* fileName= */ testId + "-output.mp4");
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              try {
                testTransformer.startTransformation(
                    MediaItem.fromUri(uri), outputVideoFile.getAbsolutePath());
                // Catch all exceptions to report. Exceptions thrown here and not caught will NOT
                // propagate.
              } catch (Exception e) {
                unexpectedExceptionReference.set(e);
                countDownLatch.countDown();
              }
            });

    if (!countDownLatch.await(timeoutSeconds, SECONDS)) {
      throw new TimeoutException("Transformer timed out after " + timeoutSeconds + " seconds.");
    }
    long elapsedTimeMs = SystemClock.DEFAULT.elapsedRealtime() - startTimeMs;

    @Nullable Exception unexpectedException = unexpectedExceptionReference.get();
    if (unexpectedException != null) {
      throw new IllegalStateException(
          "Unexpected exception starting the transformer.", unexpectedException);
    }

    @Nullable
    TransformationException transformationException = transformationExceptionReference.get();
    if (transformationException != null) {
      throw transformationException;
    }

    // If both exceptions are null, the Transformation must have succeeded, and a
    // transformationResult will be available.
    TransformationResult transformationResult =
        checkNotNull(transformationResultReference.get())
            .buildUpon()
            .setFileSizeBytes(outputVideoFile.length())
            .build();

    TransformationTestResult.Builder resultBuilder =
        new TransformationTestResult.Builder(transformationResult)
            .setFilePath(outputVideoFile.getPath())
            .setElapsedTimeMs(elapsedTimeMs);

    try {
      if (calculateSsim) {
        double ssim =
            SsimHelper.calculate(
                context, /* expectedVideoPath= */ uriString, outputVideoFile.getPath());
        resultBuilder.setSsim(ssim);
      }
    } catch (InterruptedException interruptedException) {
      // InterruptedException is a special unexpected case because it is not related to Ssim
      // calculation, so it should be thrown, rather than processed as part of the
      // TransformationTestResult.
      throw interruptedException;
    } catch (Exception analysisException) {
      // Catch all (checked and unchecked) exceptions throw by the SsimHelper and process them as
      // part of the TransformationTestResult.
      resultBuilder.setAnalysisException(analysisException);
      Log.e(TAG_PREFIX + testId, "SSIM calculation failed.", analysisException);
    }

    return resultBuilder.build();
  }

  private static void writeTestSummaryToFile(Context context, String testId, JSONObject resultJson)
      throws IOException, JSONException {
    resultJson.put("testId", testId).put("device", getDeviceJson());

    String analysisContents = resultJson.toString(/* indentSpaces= */ 2);

    // Log contents as well as writing to file, for easier visibility on individual device testing.
    Log.i(TAG_PREFIX + testId, analysisContents);

    File analysisFile =
        AndroidTestUtil.createExternalCacheFile(context, /* fileName= */ testId + "-result.txt");
    try (FileWriter fileWriter = new FileWriter(analysisFile)) {
      fileWriter.write(analysisContents);
    }
  }

  private static JSONObject getDeviceJson() throws JSONException {
    return new JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("sdkVersion", Build.VERSION.SDK_INT)
        .put("fingerprint", Build.FINGERPRINT);
  }

  private static JSONObject getTestResultJson(TransformationTestResult testResult)
      throws JSONException {
    TransformationResult transformationResult = testResult.transformationResult;

    JSONObject transformationResultJson = new JSONObject();
    if (transformationResult.durationMs != C.LENGTH_UNSET) {
      transformationResultJson.put("durationMs", transformationResult.durationMs);
    }
    if (transformationResult.fileSizeBytes != C.LENGTH_UNSET) {
      transformationResultJson.put("fileSizeBytes", transformationResult.fileSizeBytes);
    }
    if (transformationResult.averageAudioBitrate != C.RATE_UNSET_INT) {
      transformationResultJson.put("averageAudioBitrate", transformationResult.averageAudioBitrate);
    }
    if (transformationResult.averageVideoBitrate != C.RATE_UNSET_INT) {
      transformationResultJson.put("averageVideoBitrate", transformationResult.averageVideoBitrate);
    }
    if (testResult.elapsedTimeMs != C.TIME_UNSET) {
      transformationResultJson.put("elapsedTimeMs", testResult.elapsedTimeMs);
    }
    if (testResult.ssim != TransformationTestResult.SSIM_UNSET) {
      transformationResultJson.put("ssim", testResult.ssim);
    }
    if (testResult.analysisException != null) {
      transformationResultJson.put(
          "analysisException", getExceptionJson(testResult.analysisException));
    }
    return transformationResultJson;
  }

  private static JSONObject getExceptionJson(Exception exception) throws JSONException {
    JSONObject exceptionJson = new JSONObject();
    exceptionJson.put("message", exception.getMessage());
    exceptionJson.put("type", exception.getClass());
    if (exception instanceof TransformationException) {
      exceptionJson.put("errorCode", ((TransformationException) exception).errorCode);
    }
    exceptionJson.put("stackTrace", Log.getThrowableString(exception));
    return exceptionJson;
  }
}
