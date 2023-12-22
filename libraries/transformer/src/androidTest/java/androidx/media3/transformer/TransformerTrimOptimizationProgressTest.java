/*
 * Copyright 2023 The Android Open Source Project
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

import static androidx.media3.common.util.Util.isRunningOnEmulator;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.effect.DebugTraceUtil;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.RequiresNonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * End-to-end instrumentation test for {@link Transformer#getProgress} when {@link
 * Transformer.Builder#experimentalSetTrimOptimizationEnabled} is enabled.
 */
@RunWith(AndroidJUnit4.class)
public class TransformerTrimOptimizationProgressTest {
  @Rule public final TestName testName = new TestName();

  private final Context context = ApplicationProvider.getApplicationContext();

  private @MonotonicNonNull String testId;

  @Before
  @EnsuresNonNull({"testId"})
  public void setUp() {
    testId = testName.getMethodName();
  }

  @Test
  @RequiresNonNull("testId")
  public void getProgress_trimOptimizationEnabledAndApplied_givesIncreasingPercentages()
      throws Exception {
    // The trim optimization is only guaranteed to work on emulator for this file.
    assumeTrue(isRunningOnEmulator());
    // MediaCodec returns a segmentation fault fails at this SDK level on emulators.
    assumeFalse(Util.SDK_INT == 26);
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/internal_emulator_transformer_output.mp4")
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(2500)
                    .build())
            .build();
    // Written to on main sync, read on test thread.
    Queue<Integer> progresses = new ConcurrentLinkedDeque<>();
    SettableFuture<@NullableType Exception> transformerExceptionFuture = SettableFuture.create();
    DebugTraceUtil.enableTracing = true;
    // Created on test thread, only used on main sync.
    Transformer testTransformer =
        transformer
            .buildUpon()
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onCompleted(Composition composition, ExportResult exportResult) {
                    transformerExceptionFuture.set(null);
                  }

                  @Override
                  public void onError(
                      Composition composition,
                      ExportResult exportResult,
                      ExportException exportException) {
                    transformerExceptionFuture.set(exportException);
                  }
                })
            .build();
    File outputVideoFile =
        AndroidTestUtil.createExternalCacheFile(context, /* fileName= */ testId + "-output.mp4");

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              try {
                testTransformer.start(mediaItem, outputVideoFile.getAbsolutePath());
                // Catch all exceptions to report. Exceptions thrown here that are not caught will
                // NOT propagate.
              } catch (RuntimeException e) {
                transformerExceptionFuture.set(e);
              }
            });
    while (!transformerExceptionFuture.isDone()) {
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> {
                ProgressHolder progressHolder = new ProgressHolder();
                if (testTransformer.getProgress(progressHolder) == PROGRESS_STATE_AVAILABLE
                    && (progresses.isEmpty()
                        || Iterables.getLast(progresses) != progressHolder.progress)) {
                  progresses.add(progressHolder.progress);
                }
              });
      Thread.sleep(/* millis= */ 200);
    }

    assertThat(transformerExceptionFuture.get()).isNull();
    assertThat(progresses).isInOrder();
    if (!progresses.isEmpty()) {
      // The progress list could be empty if the export ends before any progress can be retrieved.
      assertThat(Iterables.getFirst(progresses, /* defaultValue= */ -1)).isAtLeast(0);
      assertThat(Iterables.getLast(progresses)).isAtMost(100);
    }
  }

  @Test
  @RequiresNonNull("testId")
  public void getProgress_trimOptimizationEnabledAndActive_returnsConsistentStates()
      throws Exception {
    // The trim optimization is only guaranteed to work on emulator for this file.
    assumeTrue(isRunningOnEmulator());
    // MediaCodec returns a segmentation fault fails at this SDK level on emulators.
    assumeFalse(Util.SDK_INT == 26);
    Transformer transformer =
        new Transformer.Builder(context).experimentalSetTrimOptimizationEnabled(true).build();
    MediaItem mediaItem =
        new MediaItem.Builder()
            .setUri("asset:///media/mp4/internal_emulator_transformer_output.mp4")
            .setClippingConfiguration(
                new MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(500)
                    .setEndPositionMs(2500)
                    .build())
            .build();
    AtomicInteger previousProgressState =
        new AtomicInteger(PROGRESS_STATE_WAITING_FOR_AVAILABILITY);
    AtomicBoolean foundInconsistentState = new AtomicBoolean();
    SettableFuture<@NullableType Exception> transformerExceptionFuture = SettableFuture.create();
    DebugTraceUtil.enableTracing = true;
    // Created on test thread, only used on main sync.
    Transformer testTransformer =
        transformer
            .buildUpon()
            .addListener(
                new Transformer.Listener() {
                  @Override
                  public void onCompleted(Composition composition, ExportResult exportResult) {
                    transformerExceptionFuture.set(null);
                  }

                  @Override
                  public void onError(
                      Composition composition,
                      ExportResult exportResult,
                      ExportException exportException) {
                    transformerExceptionFuture.set(exportException);
                  }
                })
            .build();
    File outputVideoFile =
        AndroidTestUtil.createExternalCacheFile(context, /* fileName= */ testId + "-output.mp4");

    InstrumentationRegistry.getInstrumentation()
        .runOnMainSync(
            () -> {
              try {
                testTransformer.start(mediaItem, outputVideoFile.getAbsolutePath());
                // Catch all exceptions to report. Exceptions thrown here that are not caught will
                // NOT propagate.
              } catch (RuntimeException e) {
                transformerExceptionFuture.set(e);
              }
            });
    while (!transformerExceptionFuture.isDone()) {
      InstrumentationRegistry.getInstrumentation()
          .runOnMainSync(
              () -> {
                @Transformer.ProgressState
                int progressState = transformer.getProgress(new ProgressHolder());
                if (progressState == PROGRESS_STATE_UNAVAILABLE) {
                  foundInconsistentState.set(true);
                  return;
                }
                switch (previousProgressState.get()) {
                  case PROGRESS_STATE_WAITING_FOR_AVAILABILITY:
                    break;
                  case PROGRESS_STATE_AVAILABLE:
                    if (progressState == PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                      foundInconsistentState.set(true);
                      return;
                    }
                    break;
                  case PROGRESS_STATE_NOT_STARTED:
                    if (progressState != PROGRESS_STATE_NOT_STARTED) {
                      foundInconsistentState.set(true);
                      return;
                    }
                    break;
                  default:
                    throw new IllegalStateException();
                }
                previousProgressState.set(progressState);
              });
      Thread.sleep(/* millis= */ 200);
    }

    assertThat(transformerExceptionFuture.get()).isNull();
    assertThat(foundInconsistentState.get()).isFalse();
  }
}
