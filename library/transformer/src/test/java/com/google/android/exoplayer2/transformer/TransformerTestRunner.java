/*
 * Copyright 2020 The Android Open Source Project
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

import static com.google.android.exoplayer2.robolectric.RobolectricUtil.runLooperUntil;
import static com.google.android.exoplayer2.util.Assertions.checkNotNull;

import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Helper class to run a {@link Transformer} test. */
public final class TransformerTestRunner {

  private TransformerTestRunner() {}

  /**
   * Runs tasks of the {@linkplain Transformer#getApplicationLooper() transformer Looper} until the
   * {@linkplain Transformer transformation} ends.
   *
   * @param transformer The {@link Transformer}.
   * @return The {@link TransformationResult}.
   * @throws TransformationException If the transformation threw an exception.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   * @throws IllegalStateException If the method is not called from the main thread.
   */
  public static TransformationResult runLooper(Transformer transformer)
      throws TransformationException, TimeoutException {
    AtomicReference<@NullableType TransformationResult> transformationResultRef =
        new AtomicReference<>();

    transformer.addListener(
        new Transformer.Listener() {
          @Override
          public void onTransformationCompleted(
              MediaItem inputMediaItem, TransformationResult result) {
            transformationResultRef.set(result);
          }

          @Override
          public void onTransformationError(
              MediaItem inputMediaItem,
              TransformationResult result,
              TransformationException exception) {
            if (!Objects.equals(result.transformationException, exception)) {
              result = result.buildUpon().setTransformationException(exception).build();
            }
            transformationResultRef.set(result);
          }
        });
    runLooperUntil(transformer.getApplicationLooper(), () -> transformationResultRef.get() != null);

    TransformationResult result = checkNotNull(transformationResultRef.get());
    if (result.transformationException != null) {
      throw result.transformationException;
    }

    return result;
  }
}
