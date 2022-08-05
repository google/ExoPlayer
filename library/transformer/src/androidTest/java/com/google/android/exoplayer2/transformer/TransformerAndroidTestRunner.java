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
import android.view.Surface;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Log;
import com.google.android.exoplayer2.util.SystemClock;
import com.google.android.exoplayer2.util.Util;
import com.google.common.base.Ascii;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.json.JSONException;
import org.json.JSONObject;

/** An android instrumentation test runner for {@link Transformer}. */
public class TransformerAndroidTestRunner {
  private static final String TAG = "TransformerAndroidTest";

  /** The default transformation timeout value. */
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
    @CanIgnoreReturnValue
    public Builder setTimeoutSeconds(int timeoutSeconds) {
      this.timeoutSeconds = timeoutSeconds;
      return this;
    }

    /**
     * Sets whether to calculate the SSIM of the transformation output compared to the input, if
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
     * Sets whether to suppress failures that occurs as a result of post-transformation analysis,
     * such as SSIM calculation.
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
     * Sets a {@link Map} of transformer input values, which are propagated to the transformation
     * summary JSON file.
     *
     * <p>Values in the map should be convertible according to {@link JSONObject#wrap(Object)} to be
     * recorded properly in the summary file.
     *
     * @param inputValues A {@link Map} of values to be written to the transformation summary.
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
  private final CodecNameForwardingCodecFactory transformerCodecFactory;
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
    this.transformerCodecFactory =
        new CodecNameForwardingCodecFactory(transformer.decoderFactory, transformer.encoderFactory);
    this.transformer =
        transformer
            .buildUpon()
            .setDecoderFactory(transformerCodecFactory)
            .setEncoderFactory(transformerCodecFactory)
            .build();
    this.timeoutSeconds = timeoutSeconds;
    this.requestCalculateSsim = requestCalculateSsim;
    this.suppressAnalysisExceptions = suppressAnalysisExceptions;
    this.inputValues = inputValues;
  }

  /**
   * Transforms the {@code uriString}, saving a summary of the transformation to the application
   * cache.
   *
   * @param testId A unique identifier for the transformer test run.
   * @param mediaItem The {@link MediaItem} to transform.
   * @return The {@link TransformationTestResult}.
   * @throws Exception The cause of the transformation not completing.
   */
  public TransformationTestResult run(String testId, MediaItem mediaItem) throws Exception {
    JSONObject resultJson = new JSONObject();
    if (inputValues != null) {
      resultJson.put("inputValues", JSONObject.wrap(inputValues));
    }
    try {
      TransformationTestResult transformationTestResult = runInternal(testId, mediaItem);
      resultJson.put("transformationResult", transformationTestResult.asJsonObject());
      if (!suppressAnalysisExceptions && transformationTestResult.analysisException != null) {
        throw transformationTestResult.analysisException;
      }
      return transformationTestResult;
    } catch (Exception e) {
      resultJson.put("exception", AndroidTestUtil.exceptionAsJsonObject(e));
      throw e;
    } finally {
      resultJson.put("codecDetails", transformerCodecFactory.getCodecNamesAsJsonObject());
      AndroidTestUtil.writeTestSummaryToFile(context, testId, resultJson);
    }
  }

  /**
   * Transforms the {@code uriString}.
   *
   * @param testId An identifier for the test.
   * @param mediaItem The {@link MediaItem} to transform.
   * @return The {@link TransformationTestResult}.
   * @throws IOException If an error occurs opening the output file for writing
   * @throws TimeoutException If the transformation takes longer than the {@link #timeoutSeconds}.
   * @throws InterruptedException If the thread is interrupted whilst waiting for transformer to
   *     complete.
   * @throws TransformationException If an exception occurs as a result of the transformation.
   * @throws IllegalArgumentException If the path is invalid.
   * @throws IllegalStateException If an unexpected exception occurs when starting a transformation.
   */
  private TransformationTestResult runInternal(String testId, MediaItem mediaItem)
      throws InterruptedException, IOException, TimeoutException, TransformationException {
    if (!mediaItem.clippingConfiguration.equals(MediaItem.ClippingConfiguration.UNSET)
        && requestCalculateSsim) {
      throw new UnsupportedOperationException(
          "SSIM calculation is not supported for clipped inputs.");
    }

    AtomicReference<@NullableType TransformationException> transformationExceptionReference =
        new AtomicReference<>();
    AtomicReference<@NullableType Exception> unexpectedExceptionReference = new AtomicReference<>();
    AtomicReference<@NullableType TransformationResult> transformationResultReference =
        new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);
    AtomicBoolean fallbackResolutionApplied = new AtomicBoolean(false);
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

                  @Override
                  public void onFallbackApplied(
                      MediaItem inputMediaItem,
                      TransformationRequest originalTransformationRequest,
                      TransformationRequest fallbackTransformationRequest) {
                    // Note: As TransformationRequest only reports the output height but not the
                    // output width, it's not possible to check whether the encoder has changed
                    // the output aspect ratio.
                    if (originalTransformationRequest.outputHeight
                        != fallbackTransformationRequest.outputHeight) {
                      fallbackResolutionApplied.set(true);
                    }
                  }
                })
            .build();

    File outputVideoFile =
        AndroidTestUtil.createExternalCacheFile(context, /* fileName= */ testId + "-output.mp4");
    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              try {
                testTransformer.startTransformation(mediaItem, outputVideoFile.getAbsolutePath());
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

    if (!requestCalculateSsim) {
      return resultBuilder.build();
    }
    if (fallbackResolutionApplied.get()) {
      Log.i(
          TAG,
          testId
              + ": Skipping SSIM calculation because an encoder resolution fallback was applied.");
      return resultBuilder.build();
    }
    try {
      double ssim =
          SsimHelper.calculate(
              context,
              /* referenceVideoPath= */ checkNotNull(mediaItem.localConfiguration).uri.toString(),
              /* distortedVideoPath= */ outputVideoFile.getPath());
      resultBuilder.setSsim(ssim);
    } catch (InterruptedException interruptedException) {
      // InterruptedException is a special unexpected case because it is not related to Ssim
      // calculation, so it should be thrown, rather than processed as part of the
      // TransformationTestResult.
      throw interruptedException;
    } catch (Throwable analysisFailure) {
      if (Util.SDK_INT == 21 && Ascii.toLowerCase(Util.MODEL).contains("nexus")) {
        // b/233584640, b/230093713
        Log.i(TAG, testId + ": Skipping SSIM calculation due to known device-specific issue");
      } else {
        // Catch all (checked and unchecked) failures thrown by the SsimHelper and process them as
        // part of the TransformationTestResult.
        Exception analysisException =
            analysisFailure instanceof Exception
                ? (Exception) analysisFailure
                : new IllegalStateException(analysisFailure);

        resultBuilder.setAnalysisException(analysisException);
        Log.e(TAG, testId + ": SSIM calculation failed.", analysisException);
      }
    }
    return resultBuilder.build();
  }

  /**
   * A {@link Codec.EncoderFactory} that forwards all methods to another encoder factory, whilst
   * providing visibility into the names of last codecs created by it.
   */
  private static class CodecNameForwardingCodecFactory
      implements Codec.DecoderFactory, Codec.EncoderFactory {

    /** The name of the last audio {@link Codec decoder} created. */
    @Nullable public String audioDecoderName;
    /** The name of the last video {@link Codec decoder} created. */
    @Nullable public String videoDecoderName;
    /** The name of the last audio {@link Codec encoder} created. */
    @Nullable public String audioEncoderName;
    /** The name of the last video {@link Codec encoder} created. */
    @Nullable public String videoEncoderName;

    private final Codec.DecoderFactory decoderFactory;
    private final Codec.EncoderFactory encoderFactory;

    public CodecNameForwardingCodecFactory(
        Codec.DecoderFactory decoderFactory, Codec.EncoderFactory encoderFactory) {
      this.decoderFactory = decoderFactory;
      this.encoderFactory = encoderFactory;
    }

    @Override
    public Codec createForAudioDecoding(Format format) throws TransformationException {
      Codec audioDecoder = decoderFactory.createForAudioDecoding(format);
      audioDecoderName = audioDecoder.getName();
      return audioDecoder;
    }

    @Override
    public Codec createForVideoDecoding(
        Format format, Surface outputSurface, boolean enableRequestSdrToneMapping)
        throws TransformationException {
      Codec videoDecoder =
          decoderFactory.createForVideoDecoding(format, outputSurface, enableRequestSdrToneMapping);
      videoDecoderName = videoDecoder.getName();
      return videoDecoder;
    }

    @Override
    public Codec createForAudioEncoding(Format format, List<String> allowedMimeTypes)
        throws TransformationException {
      Codec audioEncoder = encoderFactory.createForAudioEncoding(format, allowedMimeTypes);
      audioEncoderName = audioEncoder.getName();
      return audioEncoder;
    }

    @Override
    public Codec createForVideoEncoding(Format format, List<String> allowedMimeTypes)
        throws TransformationException {
      Codec videoEncoder = encoderFactory.createForVideoEncoding(format, allowedMimeTypes);
      videoEncoderName = videoEncoder.getName();
      return videoEncoder;
    }

    @Override
    public boolean audioNeedsEncoding() {
      return encoderFactory.audioNeedsEncoding();
    }

    @Override
    public boolean videoNeedsEncoding() {
      return encoderFactory.videoNeedsEncoding();
    }

    public JSONObject getCodecNamesAsJsonObject() throws JSONException {
      JSONObject detailsJson = new JSONObject();
      if (audioDecoderName != null) {
        detailsJson.put("audioDecoderName", audioDecoderName);
      }
      if (videoDecoderName != null) {
        detailsJson.put("videoDecoderName", videoDecoderName);
      }
      if (audioEncoderName != null) {
        detailsJson.put("audioEncoderName", audioEncoderName);
      }
      if (videoEncoderName != null) {
        detailsJson.put("videoEncoderName", videoEncoderName);
      }
      return detailsJson;
    }
  }
}
