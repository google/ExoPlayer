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
import static androidx.media3.common.util.Assertions.checkNotNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.SystemClock;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DebugTraceUtil;
import androidx.media3.test.utils.SsimHelper;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.base.Ascii;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

/** An android instrumentation test runner for {@link Transformer}. */
public class TransformerAndroidTestRunner {
  private static final String TAG = "TransformerAndroidTest";

  /** The default export timeout value. */
  public static final int DEFAULT_TIMEOUT_SECONDS = 120;

  /** A {@link Builder} for {@link TransformerAndroidTestRunner} instances. */
  public static class Builder {
    private final Context context;
    private final Transformer transformer;
    private boolean requestCalculateSsim;
    private int timeoutSeconds;
    private boolean suppressAnalysisExceptions;
    @Nullable private Map<String, Object> inputValues;

    /**
     * Creates a {@link Builder}.
     *
     * @param context The {@link Context}.
     * @param transformer The {@link Transformer} that performs the export.
     */
    public Builder(Context context, Transformer transformer) {
      this.context = context;
      this.transformer = transformer;
      this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
    }

    /**
     * Sets the timeout in seconds for a single export. An exception is thrown when this is
     * exceeded.
     *
     * <p>The default value is {@link #DEFAULT_TIMEOUT_SECONDS}.
     *
     * @param timeoutSeconds The timeout.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
      return this;
    }

    /**
     * Sets whether to calculate the SSIM of the exported output compared to the input, if
     * supported. Calculating SSIM is not supported if the input and output video dimensions don't
     * match, or if the input video is trimmed.
     *
     * <p>Calculating SSIM involves decoding and comparing frames of the expected and actual videos,
     * which will increase the runtime of the test.
     *
     * <p>The default value is {@code false}.
     *
     * @param requestCalculateSsim Whether to calculate SSIM, if supported.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setRequestCalculateSsim(boolean requestCalculateSsim) {
      this.requestCalculateSsim = requestCalculateSsim;
      return this;
    }

    /**
     * Sets whether to suppress failures that occurs as a result of post-export analysis, such as
     * SSIM calculation.
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
    @CanIgnoreReturnValue
    public Builder setSuppressAnalysisExceptions(boolean suppressAnalysisExceptions) {
      this.suppressAnalysisExceptions = suppressAnalysisExceptions;
      return this;
    }

    /**
     * Sets a {@link Map} of transformer input values, which are propagated to the export summary
     * JSON file.
     *
     * <p>Values in the map should be convertible according to {@link JSONObject#wrap(Object)} to be
     * recorded properly in the summary file.
     *
     * @param inputValues A {@link Map} of values to be written to the export summary.
     * @return This {@link Builder}.
     */
    @CanIgnoreReturnValue
    public Builder setInputValues(@Nullable Map<String, Object> inputValues) {
      this.inputValues = inputValues;
      return this;
    }

    /** Builds the {@link TransformerAndroidTestRunner}. */
    public TransformerAndroidTestRunner build() {
      return new TransformerAndroidTestRunner(
          context,
          transformer,
          timeoutSeconds,
          requestCalculateSsim,
          suppressAnalysisExceptions,
          inputValues);
    }
  }

  private final Context context;
  private final Transformer transformer;
  private final int timeoutSeconds;
  private final boolean requestCalculateSsim;
  private final boolean suppressAnalysisExceptions;
  @Nullable private final Map<String, Object> inputValues;

  private TransformerAndroidTestRunner(
      Context context,
      Transformer transformer,
      int timeoutSeconds,
      boolean requestCalculateSsim,
      boolean suppressAnalysisExceptions,
      @Nullable Map<String, Object> inputValues) {
    this.context = context;
    this.transformer = transformer;
    this.timeoutSeconds = timeoutSeconds;
    this.requestCalculateSsim = requestCalculateSsim;
    this.suppressAnalysisExceptions = suppressAnalysisExceptions;
    this.inputValues = inputValues;
  }

  /**
   * Exports the {@link Composition}, saving a summary of the export to the application cache.
   *
   * @param testId A unique identifier for the transformer test run.
   * @param composition The {@link Composition} to export.
   * @return The {@link ExportTestResult}.
   * @throws Exception The cause of the export not completing.
   */
  public ExportTestResult run(String testId, Composition composition) throws Exception {
    return run(testId, composition, /* oldFilePath= */ null);
  }

  /**
   * Exports the {@link Composition}, saving a summary of the export to the application cache.
   * Resumes exporting if the {@code oldFilePath} is specified.
   *
   * @param testId A unique identifier for the transformer test run.
   * @param composition The {@link Composition} to export.
   * @param oldFilePath The old output file path to resume the export from. Passing {@code null}
   *     will restart the export from the beginning.
   * @return The {@link ExportTestResult}.
   * @throws Exception The cause of the export not completing.
   */
  public ExportTestResult run(String testId, Composition composition, @Nullable String oldFilePath)
      throws Exception {
    JSONObject resultJson = new JSONObject();
    if (inputValues != null) {
      resultJson.put("inputValues", JSONObject.wrap(inputValues));
    }
    try {
      ExportTestResult exportTestResult = runInternal(testId, composition, oldFilePath);
      resultJson.put("exportResult", exportTestResult.asJsonObject());
      if (exportTestResult.exportResult.exportException != null) {
        throw exportTestResult.exportResult.exportException;
      }
      if (!suppressAnalysisExceptions && exportTestResult.analysisException != null) {
        throw exportTestResult.analysisException;
      }
      return exportTestResult;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      resultJson.put(
          "exportResult", new JSONObject().put("testException", JsonUtil.exceptionAsJsonObject(e)));
      throw e;
    } catch (IOException | TimeoutException | UnsupportedOperationException e) {
      resultJson.put(
          "exportResult", new JSONObject().put("testException", JsonUtil.exceptionAsJsonObject(e)));
      throw e;
    } finally {
      AndroidTestUtil.writeTestSummaryToFile(context, testId, resultJson);
    }
  }

  /**
   * Exports the {@link EditedMediaItem}, saving a summary of the export to the application cache.
   *
   * @param testId A unique identifier for the transformer test run.
   * @param editedMediaItem The {@link EditedMediaItem} to export.
   * @return The {@link ExportTestResult}.
   * @throws Exception The cause of the export not completing.
   */
  public ExportTestResult run(String testId, EditedMediaItem editedMediaItem) throws Exception {
    Composition composition =
        new Composition.Builder(new EditedMediaItemSequence(editedMediaItem)).build();
    return run(testId, composition);
  }

  /**
   * Exports the {@link MediaItem}, saving a summary of the export to the application cache.
   *
   * @param testId A unique identifier for the transformer test run.
   * @param mediaItem The {@link MediaItem} to export.
   * @return The {@link ExportTestResult}.
   * @throws Exception The cause of the export not completing.
   */
  public ExportTestResult run(String testId, MediaItem mediaItem) throws Exception {
    EditedMediaItem editedMediaItem = new EditedMediaItem.Builder(mediaItem).build();
    return run(testId, editedMediaItem);
  }

  /**
   * Exports the {@link Composition}.
   *
   * @param testId An identifier for the test.
   * @param composition The {@link Composition} to export.
   * @param oldFilePath The old output file path to resume the export from. Passing {@code null}
   *     will restart the export from the beginning.
   * @return The {@link ExportTestResult}.
   * @throws IllegalStateException See {@link Transformer#start(Composition, String)}.
   * @throws InterruptedException If the thread is interrupted whilst waiting for transformer to
   *     complete.
   * @throws IOException If an error occurs opening the output file for writing.
   * @throws TimeoutException If the export has not completed after {@linkplain
   *     Builder#setTimeoutSeconds(int) the given timeout}.
   */
  private ExportTestResult runInternal(
      String testId, Composition composition, @Nullable String oldFilePath)
      throws InterruptedException, IOException, TimeoutException {
    if (requestCalculateSsim) {
      checkArgument(
          composition.sequences.size() == 1
              && composition.sequences.get(0).editedMediaItems.size() == 1,
          "SSIM is only relevant for single MediaItem compositions");
      checkArgument(
          composition
              .sequences
              .get(0)
              .editedMediaItems
              .get(0)
              .mediaItem
              .clippingConfiguration
              .equals(MediaItem.ClippingConfiguration.UNSET),
          "SSIM calculation is not supported for clipped inputs.");
    }
    for (EditedMediaItemSequence sequence : composition.sequences) {
      for (EditedMediaItem editedMediaItem : sequence.editedMediaItems) {
        Uri mediaItemUri = checkNotNull(editedMediaItem.mediaItem.localConfiguration).uri;
        String scheme = mediaItemUri.getScheme();
        if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) {
          assumeTrue(
              "Input network file requested on device with no network connection. Input file"
                  + " name: "
                  + mediaItemUri,
              hasNetworkConnection(context));
        }
      }
    }

    AtomicReference<@NullableType FallbackDetails> fallbackDetailsReference =
        new AtomicReference<>();
    AtomicReference<@NullableType Exception> unexpectedExceptionReference = new AtomicReference<>();
    AtomicReference<@NullableType ExportResult> exportResultReference = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    long startTimeMs = SystemClock.DEFAULT.elapsedRealtime();

    DebugTraceUtil.enableTracing = true;

    Transformer testTransformer =
        transformer
            .buildUpon()
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onCompleted(Composition composition, ExportResult exportResult) {
                    exportResultReference.set(exportResult);
                    countDownLatch.countDown();
                  }

                  @Override
                  public void onError(
                      Composition composition,
                      ExportResult exportResult,
                      ExportException exportException) {
                    exportResultReference.set(exportResult);
                    countDownLatch.countDown();
                  }

                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    // Note: As TransformationRequest only reports the output height but not the
                    // output width, it's not possible to check whether the encoder has changed
                    // the output aspect ratio.
                    fallbackDetailsReference.set(
                        new FallbackDetails(
                            originalTransformationRequest.outputHeight,
                            fallbackTransformationRequest.outputHeight,
                            originalTransformationRequest.audioMimeType,
                            fallbackTransformationRequest.audioMimeType,
                            originalTransformationRequest.videoMimeType,
                            fallbackTransformationRequest.videoMimeType,
                            originalTransformationRequest.hdrMode,
                            fallbackTransformationRequest.hdrMode));
                  }
                })
            .build();

    File outputVideoFile =
        AndroidTestUtil.createExternalCacheFile(
            context,
            /* fileName= */ testId + "-" + Clock.DEFAULT.elapsedRealtime() + "-output.mp4");
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              try {
                if (oldFilePath == null) {
                  testTransformer.start(composition, outputVideoFile.getAbsolutePath());
                } else {
                  testTransformer.resume(
                      composition, outputVideoFile.getAbsolutePath(), oldFilePath);
                }
                // Catch all exceptions to report. Exceptions thrown here and not caught will NOT
                // propagate.
              } catch (Exception e) {
                unexpectedExceptionReference.set(e);
                countDownLatch.countDown();
              }
            });

    // Block here until timeout reached or latch is counted down.
    if (!countDownLatch.await(timeoutSeconds, SECONDS)) {
      logTimeoutDiagnostics();
      throw new TimeoutException("Transformer timed out after " + timeoutSeconds + " seconds.");
    }
    @Nullable Exception unexpectedException = unexpectedExceptionReference.get();
    if (unexpectedException != null) {
      throw new IllegalStateException(
          "Unexpected exception starting the transformer.", unexpectedException);
    }

    long elapsedTimeMs = SystemClock.DEFAULT.elapsedRealtime() - startTimeMs;
    @Nullable FallbackDetails fallbackDetails = fallbackDetailsReference.get();
    ExportResult exportResult = checkNotNull(exportResultReference.get());

    if (exportResult.exportException != null) {
      return new ExportTestResult.Builder(exportResult)
          .setElapsedTimeMs(elapsedTimeMs)
          .setFallbackDetails(fallbackDetails)
          .build();
    }

    // No exceptions raised, export has succeeded.
    ExportTestResult.Builder testResultBuilder =
        new ExportTestResult.Builder(
                checkNotNull(exportResultReference.get())
                    .buildUpon()
                    .setFileSizeBytes(outputVideoFile.length())
                    .build())
            .setElapsedTimeMs(elapsedTimeMs)
            .setFallbackDetails(fallbackDetails)
            .setFilePath(outputVideoFile.getPath());

    if (!requestCalculateSsim) {
      return testResultBuilder.build();
    }
    if (fallbackDetails != null && fallbackDetails.fallbackOutputHeight != C.LENGTH_UNSET) {
      Log.i(
          TAG,
          testId
              + ": Skipping SSIM calculation because an encoder resolution fallback was applied.");
      return testResultBuilder.build();
    }
    try {
      MediaItem mediaItem = composition.sequences.get(0).editedMediaItems.get(0).mediaItem;
      double ssim =
          SsimHelper.calculate(
              context,
              /* referenceVideoPath= */ checkNotNull(mediaItem.localConfiguration).uri.toString(),
              /* distortedVideoPath= */ outputVideoFile.getPath());
      testResultBuilder.setSsim(ssim);
    } catch (InterruptedException interruptedException) {
      // InterruptedException is a special unexpected case because it is not related to Ssim
      // calculation, so it should be thrown, rather than processed as part of the
      // ExportTestResult.
      throw interruptedException;
    } catch (Throwable analysisFailure) {
      if (Util.SDK_INT == 21 && Ascii.toLowerCase(Util.MODEL).contains("nexus")) {
        // b/233584640, b/230093713
        Log.i(TAG, testId + ": Skipping SSIM calculation due to known device-specific issue");
      } else {
        // Catch all (checked and unchecked) failures thrown by the SsimHelper and process them as
        // part of the ExportTestResult.
        Exception analysisException =
            analysisFailure instanceof Exception
                ? (Exception) analysisFailure
                : new IllegalStateException(analysisFailure);

        testResultBuilder.setAnalysisException(analysisException);
        Log.e(TAG, testId + ": SSIM calculation failed.", analysisException);
      }
    }
    return testResultBuilder.build();
  }

  /** Returns whether the context is connected to the network. */
  private static boolean hasNetworkConnection(Context context) {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      return false;
    }
    if (Util.SDK_INT >= 23) {
      // getActiveNetwork is available from API 23.
      NetworkCapabilities activeNetworkCapabilities =
          connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
      if (activeNetworkCapabilities != null
          && (activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
              || activeNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))) {
        return true;
      }
    } else {
      // getActiveNetworkInfo is deprecated from API 29.
      NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
      if (activeNetworkInfo != null
          && (activeNetworkInfo.getType() == ConnectivityManager.TYPE_WIFI
              || activeNetworkInfo.getType() == ConnectivityManager.TYPE_MOBILE)) {
        return true;
      }
    }
    return false;
  }

  private static void logTimeoutDiagnostics() {
    Log.e(TAG, "Effect debug traces at timeout: " + DebugTraceUtil.generateTraceSummary());
    Log.e(TAG, "Thread state at timeout:");
    Set<Map.Entry<Thread, StackTraceElement[]>> entries = Thread.getAllStackTraces().entrySet();
    for (Map.Entry<Thread, StackTraceElement[]> threadAndStackTraceElements : entries) {
      Thread thread = threadAndStackTraceElements.getKey();
      StackTraceElement[] stackTraceElements = threadAndStackTraceElements.getValue();
      Log.e(TAG, ">  " + thread + ' ' + thread.getState());
      for (StackTraceElement stackTraceElement : stackTraceElements) {
        Log.e(TAG, ">    " + stackTraceElement);
      }
    }
  }
}
