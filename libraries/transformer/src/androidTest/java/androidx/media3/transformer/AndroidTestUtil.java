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

import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.Assertions;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Utilities for instrumentation tests. */
/* package */ final class AndroidTestUtil {
  public static final String MP4_ASSET_URI_STRING = "asset:///media/mp4/sample.mp4";
  public static final String SEF_ASSET_URI_STRING = "asset:///media/mp4/sample_sef_slow_motion.mp4";
  public static final String REMOTE_MP4_10_SECONDS_URI_STRING =
      "https://storage.googleapis.com/exoplayer-test-media-1/mp4/android-screens-10s.mp4";

  /** Information about the result of successfully running a transformer. */
  public static final class TransformationResult {
    public long outputSizeBytes;

    private TransformationResult(long outputSizeBytes) {
      this.outputSizeBytes = outputSizeBytes;
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
            .setListener(
                new Transformer.Listener() {
                  @Override
                  public void onTransformationCompleted(MediaItem inputMediaItem) {
                    countDownLatch.countDown();
                  }

                  @Override
                  public void onTransformationError(MediaItem inputMediaItem, Exception exception) {
                    exceptionReference.set(exception);
                    countDownLatch.countDown();
                  }
                })
            .build();

    Uri uri = Uri.parse(uriString);
    File externalCacheFile = createExternalCacheFile(context, /* filePrefix= */ testId);
    try {
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> {
                try {
                  testTransformer.startTransformation(
                      MediaItem.fromUri(uri), externalCacheFile.getAbsolutePath());
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
      long outputSizeBytes = externalCacheFile.length();
      return new TransformationResult(outputSizeBytes);
    } finally {
      externalCacheFile.delete();
    }
  }

  private static File createExternalCacheFile(Context context, String filePrefix)
      throws IOException {
    File file = new File(context.getExternalCacheDir(), filePrefix + "-output.mp4");
    Assertions.checkState(
        !file.exists() || file.delete(), "Could not delete the previous transformer output file.");
    Assertions.checkState(file.createNewFile(), "Could not create the transformer output file.");
    return file;
  }

  private AndroidTestUtil() {}
}
