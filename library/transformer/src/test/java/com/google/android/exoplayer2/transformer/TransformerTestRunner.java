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

import androidx.annotation.Nullable;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.robolectric.RobolectricUtil;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;

/** Helper class to run a {@link Transformer} test. */
public final class TransformerTestRunner {

  private TransformerTestRunner() {}

  /**
   * Runs tasks of the {@link Transformer#getApplicationLooper() transformer Looper} until the
   * current {@link Transformer transformation} completes.
   *
   * @param transformer The {@link Transformer}.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   * @throws IllegalStateException If the method is not called from the main thread, or if the
   *     transformation completes with error.
   */
  public static void runUntilCompleted(Transformer transformer) throws TimeoutException {
    @Nullable Exception exception = runUntilListenerCalled(transformer);
    if (exception != null) {
      throw new IllegalStateException(exception);
    }
  }

  /**
   * Runs tasks of the {@link Transformer#getApplicationLooper() transformer Looper} until a {@link
   * Transformer} error occurs.
   *
   * @param transformer The {@link Transformer}.
   * @return The raised exception.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   * @throws IllegalStateException If the method is not called from the main thread, or if the
   *     transformation completes without error.
   */
  public static TransformationException runUntilError(Transformer transformer)
      throws TimeoutException {
    @Nullable TransformationException exception = runUntilListenerCalled(transformer);
    if (exception == null) {
      throw new IllegalStateException("The transformation completed without error.");
    }
    return exception;
  }

  @Nullable
  private static TransformationException runUntilListenerCalled(Transformer transformer)
      throws TimeoutException {
    AtomicBoolean transformationCompleted = new AtomicBoolean();
    AtomicReference<@NullableType TransformationException> transformationException =
        new AtomicReference<>();

    transformer.addListener(
        new Transformer.Listener() {
          @Override
          public void onTransformationCompleted(
              MediaItem inputMediaItem, TransformationResult transformationResult) {
            transformationCompleted.set(true);
          }

          @Override
          public void onTransformationError(
              MediaItem inputMediaItem, TransformationException exception) {
            transformationException.set(exception);
          }
        });
    runLooperUntil(
        transformer.getApplicationLooper(),
        () -> transformationCompleted.get() || transformationException.get() != null);

    return transformationException.get();
  }
}
