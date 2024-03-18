/*
 * Copyright 2024 The Android Open Source Project
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

import static androidx.media3.common.util.Assertions.checkStateNotNull;
import static androidx.media3.common.util.Util.isRunningOnEmulator;
import static androidx.media3.transformer.AndroidTestUtil.MP4_TRIM_OPTIMIZATION_URI_STRING;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_NOT_STARTED;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static androidx.media3.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.content.Context;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.MediaItem;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.NullableType;
import androidx.media3.common.util.Util;
import androidx.media3.effect.BaseGlShaderProgram;
import androidx.media3.effect.Brightness;
import androidx.media3.effect.DebugTraceUtil;
import androidx.media3.effect.GlEffect;
import androidx.media3.effect.GlShaderProgram;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.SettableFuture;
import java.io.File;
import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/** End-to-end instrumentation test for {@link Transformer#getProgress}. */
@RunWith(AndroidJUnit4.class)
public class TransformerProgressTest {
  private static final long DELAY_MS = 50;

  @Rule public final TestName testName = new TestName();

  private final Context context = ApplicationProvider.getApplicationContext();

  private String testId;

  @Before
  public void setUp() {
    testId = testName.getMethodName();
  }

  /**
   * Tests that {@link Transformer#getProgress(ProgressHolder)} returns monotonically increasing
   * updates. The test runs a transformation of a {@link Composition} using a custom video effect
   * that adds delay in the video processing pipeline, ensuring that the the transformation takes
   * long enough for the test thread to collect at least two progress updates.
   */
  @Test
  @SuppressWarnings("PreferJavaTimeOverload")
  public void getProgress_monotonicallyIncreasingUpdates() throws InterruptedException {
    AtomicBoolean completed = new AtomicBoolean();
    AtomicReference<ExportResult> exportResultRef = new AtomicReference<>();
    AtomicReference<ExportException> exportExceptionRef = new AtomicReference<>();
    Transformer.Listener listener =
        new Transformer.Listener() {
          @Override
          public void onCompleted(Composition composition, ExportResult exportResult) {
            exportResultRef.set(exportResult);
            completed.set(true);
          }

          @Override
          public void onError(
              Composition composition, ExportResult exportResult, ExportException exportException) {
            exportExceptionRef.set(exportException);
            exportResultRef.set(exportResult);
            completed.set(true);
          }
        };
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    AtomicReference<@NullableType Transformer> transformerRef = new AtomicReference<>();
    AtomicReference<@NullableType Exception> exceptionRef = new AtomicReference<>();
    instrumentation.runOnMainSync(
        () -> {
          try {
            // A composition with a custom effect that's slow: puts the video processing thread to
            // sleep on every received frame.
            Composition composition =
                new Composition.Builder(
                        new EditedMediaItemSequence(
                            new EditedMediaItem.Builder(
                                    MediaItem.fromUri(AndroidTestUtil.MP4_ASSET_URI_STRING))
                                .setEffects(
                                    new Effects(
                                        /* audioProcessors= */ ImmutableList.of(),
                                        /* videoEffects= */ ImmutableList.of(
                                            new DelayEffect(/* delayMs= */ DELAY_MS))))
                                .build()))
                    .build();
            File outputVideoFile =
                AndroidTestUtil.createExternalCacheFile(
                    context, /* fileName= */ testId + "-output.mp4");
            Transformer transformer = new Transformer.Builder(context).build();
            transformer.addListener(listener);
            transformer.start(composition, outputVideoFile.getPath());
            transformerRef.set(transformer);
          } catch (Exception e) {
            exceptionRef.set(e);
          }
        });

    assertThat(exceptionRef.get()).isNull();

    ArrayList<Integer> progresses = new ArrayList<>();
    while (!completed.get()) {
      instrumentation.runOnMainSync(
          () -> {
            Transformer transformer = checkStateNotNull(transformerRef.get());
            ProgressHolder progressHolder = new ProgressHolder();
            if (transformer.getProgress(progressHolder) == PROGRESS_STATE_AVAILABLE
                && (progresses.isEmpty()
                    || Iterables.getLast(progresses) != progressHolder.progress)) {
              progresses.add(progressHolder.progress);
            }
          });

      Thread.sleep(DELAY_MS);
    }

    assertThat(exportExceptionRef.get()).isNull();
    // Transformer.getProgress() should be able to retrieve at least 2 progress updates since the
    // delay effect stalls the video processing for each video frame.
    assertThat(progresses.size()).isAtLeast(2);
    assertThat(progresses).isInOrder();
    assertThat(Iterables.getFirst(progresses, /* defaultValue= */ -1)).isAtLeast(0);
    assertThat(Iterables.getLast(progresses)).isAtMost(100);
  }

  @Test
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
            .setUri(MP4_TRIM_OPTIMIZATION_URI_STRING)
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
    // TODO - b/322145448 Make tests more deterministic and produce at least one progress output.
    if (!progresses.isEmpty()) {
      // The progress list could be empty if the export ends before any progress can be retrieved.
      assertThat(Iterables.getFirst(progresses, /* defaultValue= */ -1)).isAtLeast(0);
      assertThat(Iterables.getLast(progresses)).isAtMost(100);
    }
  }

  @Test
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
            .setUri(MP4_TRIM_OPTIMIZATION_URI_STRING)
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

  /** A {@link GlEffect} that adds delay in the video pipeline by putting the thread to sleep. */
  private static final class DelayEffect implements GlEffect {
    public final long delayMs;

    public DelayEffect(long delayMs) {
      this.delayMs = delayMs;
    }

    @Override
    public GlShaderProgram toGlShaderProgram(Context context, boolean useHdr)
        throws VideoFrameProcessingException {
      // Wrapping Brightness's GlShaderProgram for convenience. All existing BaseGlShaderProgram
      // implementations are final and can't be extended.
      BaseGlShaderProgram brightnessShaderGlProgram =
          new Brightness(1.0f).toGlShaderProgram(context, useHdr);
      return new GlShaderProgram() {
        @Override
        public void setInputListener(InputListener inputListener) {
          brightnessShaderGlProgram.setInputListener(inputListener);
        }

        @Override
        public void setOutputListener(OutputListener outputListener) {
          brightnessShaderGlProgram.setOutputListener(outputListener);
        }

        @Override
        public void setErrorListener(Executor executor, ErrorListener errorListener) {
          brightnessShaderGlProgram.setErrorListener(executor, errorListener);
        }

        @Override
        public void queueInputFrame(
            GlObjectsProvider glObjectsProvider,
            GlTextureInfo inputTexture,
            long presentationTimeUs) {
          try {
            Thread.sleep(delayMs);
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
          brightnessShaderGlProgram.queueInputFrame(
              glObjectsProvider, inputTexture, presentationTimeUs);
        }

        @Override
        public void releaseOutputFrame(GlTextureInfo outputTexture) {
          brightnessShaderGlProgram.releaseOutputFrame(outputTexture);
        }

        @Override
        public void signalEndOfCurrentInputStream() {
          brightnessShaderGlProgram.signalEndOfCurrentInputStream();
        }

        @Override
        public void flush() {
          brightnessShaderGlProgram.flush();
        }

        @Override
        public void release() throws VideoFrameProcessingException {
          brightnessShaderGlProgram.release();
        }
      };
    }
  }
}
