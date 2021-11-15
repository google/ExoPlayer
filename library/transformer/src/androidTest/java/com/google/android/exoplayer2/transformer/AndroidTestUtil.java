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

import android.content.Context;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.util.Assertions;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Utility methods for instrumentation tests. */
/* package */ final class AndroidTestUtil {
  public static final String MP4_ASSET_URI = "asset:///media/mp4/sample.mp4";
  public static final String SEF_ASSET_URI = "asset:///media/mp4/sample_sef_slow_motion.mp4";

  /** Transforms the {@code uriString} with the {@link Transformer}. */
  public static void runTransformer(Context context, Transformer transformer, String uriString)
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
    File externalCacheFile = createExternalCacheFile(uri, context);
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
      countDownLatch.await();
      @Nullable Exception exception = exceptionReference.get();
      if (exception != null) {
        throw exception;
      }
    } finally {
      externalCacheFile.delete();
    }
  }

  private static File createExternalCacheFile(Uri uri, Context context) throws IOException {
    File file = new File(context.getExternalCacheDir(), "transformer-" + uri.hashCode());
    Assertions.checkState(
        !file.exists() || file.delete(), "Could not delete the previous transformer output file");
    Assertions.checkState(file.createNewFile(), "Could not create the transformer output file");
    return file;
  }

  private AndroidTestUtil() {}
}
