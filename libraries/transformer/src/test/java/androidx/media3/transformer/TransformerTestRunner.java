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

package androidx.media3.transformer;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.media3.test.utils.robolectric.RobolectricUtil.runLooperUntil;

import androidx.media3.common.util.NullableType;
import androidx.media3.test.utils.robolectric.RobolectricUtil;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/** Helper class to run a {@link Transformer} test. */
public final class TransformerTestRunner {

  private TransformerTestRunner() {}

  /**
   * Runs tasks of the {@linkplain Transformer#getApplicationLooper() transformer Looper} until the
   * {@linkplain Transformer export} ends.
   *
   * @param transformer The {@link Transformer}.
   * @return The {@link ExportResult}.
   * @throws ExportException If the export threw an exception.
   * @throws TimeoutException If the {@link RobolectricUtil#DEFAULT_TIMEOUT_MS default timeout} is
   *     exceeded.
   * @throws IllegalStateException If the method is not called from the main thread.
   */
  public static ExportResult runLooper(Transformer transformer)
      throws ExportException, TimeoutException {
    AtomicReference<@NullableType ExportResult> exportResultRef = new AtomicReference<>();

    transformer.addListener(
        new Transformer.Listener() {
          @Override
          public void onCompleted(Composition composition, ExportResult exportResult) {
            exportResultRef.set(exportResult);
          }

          @Override
          public void onError(
              Composition composition, ExportResult exportResult, ExportException exportException) {
            if (!Objects.equals(exportResult.exportException, exportException)) {
              exportResult = exportResult.buildUpon().setExportException(exportException).build();
            }
            exportResultRef.set(exportResult);
          }
        });
    runLooperUntil(transformer.getApplicationLooper(), () -> exportResultRef.get() != null);

    ExportResult exportResult = checkNotNull(exportResultRef.get());
    if (exportResult.exportException != null) {
      throw exportResult.exportException;
    }

    return exportResult;
  }
}
