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
package com.google.android.exoplayer2.transformer;

import static com.google.android.exoplayer2.util.Assertions.checkState;
import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.MediaItem;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Utilities for instrumentation tests. */
public final class AndroidTestUtil {
  public static final String MP4_ASSET_URI_STRING = "asset:///media/mp4/sample.mp4";
  public static final String SEF_ASSET_URI_STRING = "asset:///media/mp4/sample_sef_slow_motion.mp4";
  public static final String REMOTE_MP4_10_SECONDS_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4";

  /** Information about the result of successfully running a transformer. */
  public static final class TransformationResult {

    /** A builder for {@link TransformationResult} instances. */
    public static final class Builder {
      private final String testId;
      @Nullable private Long fileSizeBytes;

      public Builder(String testId) {
        this.testId = testId;
      }

      public Builder setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
        return this;
      }

      public TransformationResult build() {
        return new TransformationResult(testId, fileSizeBytes);
      }
    }

    public final String testId;
    @Nullable public final Long fileSizeBytes;

    private TransformationResult(String testId, @Nullable Long fileSizeBytes) {
      this.testId = testId;
      this.fileSizeBytes = fileSizeBytes;
    }

    /**
     * Returns all the analysis data from the test.
     *
     * <p>If a value was not generated, it will not be part of the return value.
     */
    public String getFormattedAnalysis() {
      String analysis = "test=" + testId;
      if (fileSizeBytes != null) {
        analysis += ", fileSizeBytes=" + fileSizeBytes;
      }
      return analysis;
    }
  }

  /**
   * Transforms the {@code uriString} with the {@link Transformer}.
   *
   * @param context The {@link Context}.
   * @param testId An identifier for the test.
   * @param transformer The {@link Transformer} that performs the transformation.
   * @param uriString The uri (as a {@link String}) that will be transformed.
   * @param timeoutSeconds The transformer timeout. An assertion confirms this is not exceeded.
   * @return The {@link TransformationResult}.
   * @throws Exception The cause of the transformation not completing.
   */
  public static TransformationResult runTransformer(
      Context context, String testId, Transformer transformer, String uriString, int timeoutSeconds)
      throws Exception {
    AtomicReference<@NullableType Exception> exceptionReference = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    Transformer testTransformer =
        transformer
            .buildUpon()
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onTransformationCompleted(MediaItem inputMediaItem) {
                    countDownLatch.countDown();
                  }

                  @Override
                  public void onTransformationError(
                      MediaItem inputMediaItem, TransformationException exception) {
                    exceptionReference.set(exception);
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
              } catch (IOException e) {
                exceptionReference.set(e);
              }
            });

    assertWithMessage("Transformer timed out after " + timeoutSeconds + " seconds.")
        .that(countDownLatch.await(timeoutSeconds, SECONDS))
        .isTrue();
    @Nullable Exception exception = exceptionReference.get();
    if (exception != null) {
      throw exception;
    }

    TransformationResult result =
        new TransformationResult.Builder(testId).setFileSizeBytes(outputVideoFile.length()).build();

    writeTransformationResultToFile(context, result);
    return result;
  }

  private static void writeTransformationResultToFile(Context context, TransformationResult result)
      throws IOException {
    File analysisFile =
        createExternalCacheFile(context, /* fileName= */ result.testId + "-result.txt");
    try (FileWriter fileWriter = new FileWriter(analysisFile)) {
      String fileContents =
          result.getFormattedAnalysis()
              + ", deviceFingerprint="
              + Build.FINGERPRINT
              + ", deviceBrand="
              + Build.MANUFACTURER
              + ", deviceModel="
              + Build.MODEL
              + ", sdkVersion="
              + Build.VERSION.SDK_INT;
      fileWriter.write(fileContents);
    }
  }

  private static File createExternalCacheFile(Context context, String fileName) throws IOException {
    File file = new File(context.getExternalCacheDir(), fileName);
    checkState(!file.exists() || file.delete(), "Could not delete file: " + file.getAbsolutePath());
    checkState(file.createNewFile(), "Could not create file: " + file.getAbsolutePath());
    return file;
  }

  private AndroidTestUtil() {}
}
