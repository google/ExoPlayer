/*
 * Copyright 2021 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.common.util.Assertions.checkState;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Log;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.json.JSONException;
import org.json.JSONObject;

/** Utilities for instrumentation tests. */
public final class AndroidTestUtil {
  public static final String MP4_ASSET_URI_STRING = "asset:///media/mp4/sample.mp4";
  public static final String SEF_ASSET_URI_STRING = "asset:///media/mp4/sample_sef_slow_motion.mp4";
  public static final String REMOTE_MP4_10_SECONDS_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4";

  /**
   * Transforms the {@code uriString} with the {@link Transformer}, saving a summary of the
   * transformation to the application cache.
   *
   * @param context The {@link Context}.
   * @param testId An identifier for the test.
   * @param transformer The {@link Transformer} that performs the transformation.
   * @param uriString The uri (as a {@link String}) that will be transformed.
   * @param timeoutSeconds The transformer timeout. An exception is thrown if this is exceeded.
   * @return The {@link TransformationResult}.
   * @throws Exception The cause of the transformation not completing.
   */
  public static TransformationResult runTransformer(
      Context context, String testId, Transformer transformer, String uriString, int timeoutSeconds)
      throws Exception {
    JSONObject resultJson = new JSONObject();
    try {
      TransformationResult transformationResult =
          runTransformerInternal(context, testId, transformer, uriString, timeoutSeconds);
      resultJson.put("transformationResult", getTransformationResultJson(transformationResult));
      return transformationResult;
    } catch (Exception e) {
      resultJson.put("exception", getExceptionJson(e));
      throw e;
    } finally {
      writeTestSummaryToFile(context, testId, resultJson);
    }
  }

  private static TransformationResult runTransformerInternal(
      Context context, String testId, Transformer transformer, String uriString, int timeoutSeconds)
      throws Exception {
    AtomicReference<@NullableType TransformationException> transformationExceptionReference =
        new AtomicReference<>();
    AtomicReference<@NullableType Exception> unexpectedExceptionReference = new AtomicReference<>();
    AtomicReference<@NullableType TransformationResult> transformationResultReference =
        new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

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
    File outputVideoFile = createExternalCacheFile(context, /* fileName= */ testId + "-output.mp4");
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

    @Nullable Exception unexpectedException = unexpectedExceptionReference.get();
    if (unexpectedException != null) {
      throw unexpectedException;
    }

    @Nullable
    TransformationException transformationException = transformationExceptionReference.get();
    if (transformationException != null) {
      throw transformationException;
    }

    // If both exceptions are null, the Transformation must have succeeded, and a
    // transformationResult will be available.
    return checkNotNull(transformationResultReference.get())
        .buildUpon()
        .setFileSizeBytes(outputVideoFile.length())
        .build();
  }

  private static void writeTestSummaryToFile(Context context, String testId, JSONObject resultJson)
      throws IOException, JSONException {
    resultJson.put("testId", testId).put("device", getDeviceJson());

    String analysisContents = resultJson.toString(/* indentSpaces= */ 2);

    // Log contents as well as writing to file, for easier visibility on individual device testing.
    Log.i("TransformerAndroidTest_" + testId, analysisContents);

    File analysisFile = createExternalCacheFile(context, /* fileName= */ testId + "-result.txt");
    try (FileWriter fileWriter = new FileWriter(analysisFile)) {
      fileWriter.write(analysisContents);
    }
  }

  private static File createExternalCacheFile(Context context, String fileName) throws IOException {
    File file = new File(context.getExternalCacheDir(), fileName);
    checkState(!file.exists() || file.delete(), "Could not delete file: " + file.getAbsolutePath());
    checkState(file.createNewFile(), "Could not create file: " + file.getAbsolutePath());
    return file;
  }

  private static JSONObject getDeviceJson() throws JSONException {
    return new JSONObject()
        .put("manufacturer", Build.MANUFACTURER)
        .put("model", Build.MODEL)
        .put("sdkVersion", Build.VERSION.SDK_INT)
        .put("fingerprint", Build.FINGERPRINT);
  }

  private static JSONObject getTransformationResultJson(TransformationResult transformationResult)
      throws JSONException {
    JSONObject transformationResultJson = new JSONObject();
    if (transformationResult.fileSizeBytes != C.LENGTH_UNSET) {
      transformationResultJson.put("fileSizeBytes", transformationResult.fileSizeBytes);
    }
    if (transformationResult.averageAudioBitrate != C.RATE_UNSET_INT) {
      transformationResultJson.put("averageAudioBitrate", transformationResult.averageAudioBitrate);
    }
    if (transformationResult.averageVideoBitrate != C.RATE_UNSET_INT) {
      transformationResultJson.put("averageVideoBitrate", transformationResult.averageVideoBitrate);
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

  private AndroidTestUtil() {}
}
