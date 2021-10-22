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

import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_AVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_NO_TRANSFORMATION;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_UNAVAILABLE;
import static com.google.android.exoplayer2.transformer.Transformer.PROGRESS_STATE_WAITING_FOR_AVAILABILITY;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.testutil.DumpFileAsserts;
import com.google.android.exoplayer2.testutil.FakeClock;
import com.google.android.exoplayer2.util.MimeTypes;
import com.google.android.exoplayer2.util.Util;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.shadows.ShadowMediaCodec;

/** Unit test for {@link Transformer}. */
@RunWith(AndroidJUnit4.class)
public final class TransformerTest {

  private static final String URI_PREFIX = "asset:///media/";
  private static final String FILE_VIDEO_ONLY = "mkv/sample.mkv";
  private static final String FILE_AUDIO_ONLY = "amr/sample_nb.amr";
  private static final String FILE_AUDIO_VIDEO = "mp4/sample.mp4";
  private static final String FILE_WITH_SUBTITLES = "mkv/sample_with_srt.mkv";
  private static final String FILE_WITH_SEF_SLOW_MOTION = "mp4/sample_sef_slow_motion.mp4";
  private static final String FILE_WITH_ALL_SAMPLE_FORMATS_UNSUPPORTED = "mp4/sample_ac3.mp4";
  private static final String FILE_UNKNOWN_DURATION = "mp4/sample_fragmented.mp4";
  public static final String DUMP_FILE_OUTPUT_DIRECTORY = "transformerdumps";
  public static final String DUMP_FILE_EXTENSION = "dump";

  private Context context;
  private String outputPath;
  private TestMuxer testMuxer;
  private FakeClock clock;
  private ProgressHolder progressHolder;

  @Before
  public void setUp() throws Exception {
    context = ApplicationProvider.getApplicationContext();
    outputPath = Util.createTempFile(context, "TransformerTest").getPath();
    clock = new FakeClock(/* isAutoAdvancing= */ true);
    progressHolder = new ProgressHolder();
    createEncodersAndDecoders();
  }

  @After
  public void tearDown() throws Exception {
    Files.delete(Paths.get(outputPath));
    removeEncodersAndDecoders();
  }

  @Test
  public void startTransformation_videoOnly_completesSuccessfully() throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_VIDEO_ONLY));
  }

  @Test
  public void startTransformation_audioOnly_completesSuccessfully() throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_AUDIO_ONLY);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_AUDIO_ONLY));
  }

  @Test
  public void startTransformation_audioAndVideo_completesSuccessfully() throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO));
  }

  @Test
  public void startTransformation_withSubtitles_completesSuccessfully() throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_WITH_SUBTITLES);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_WITH_SUBTITLES));
  }

  @Test
  public void startTransformation_successiveTransformations_completesSuccessfully()
      throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_VIDEO_ONLY);

    // Transform first media item.
    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);
    Files.delete(Paths.get(outputPath));

    // Transform second media item.
    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_VIDEO_ONLY));
  }

  @Test
  public void startTransformation_concurrentTransformations_throwsError() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.startTransformation(mediaItem, outputPath);

    assertThrows(
        IllegalStateException.class, () -> transformer.startTransformation(mediaItem, outputPath));
  }

  @Test
  public void startTransformation_removeAudio_completesSuccessfully() throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setRemoveAudio(true)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".noaudio"));
  }

  @Test
  public void startTransformation_removeVideo_completesSuccessfully() throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setRemoveVideo(true)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_AUDIO_VIDEO);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(
        context, testMuxer, getDumpFileName(FILE_AUDIO_VIDEO + ".novideo"));
  }

  @Test
  public void startTransformation_flattenForSlowMotion_completesSuccessfully() throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setFlattenForSlowMotion(true)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_WITH_SEF_SLOW_MOTION);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_WITH_SEF_SLOW_MOTION));
  }

  @Test
  public void startTransformation_withPlayerError_completesWithError() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri("asset:///non-existing-path.mp4");

    transformer.startTransformation(mediaItem, outputPath);
    Exception exception = TransformerTestRunner.runUntilError(transformer);

    assertThat(exception).isInstanceOf(ExoPlaybackException.class);
    assertThat(exception).hasCauseThat().isInstanceOf(IOException.class);
  }

  @Test
  public void startTransformation_withAllSampleFormatsUnsupported_completesWithError()
      throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_WITH_ALL_SAMPLE_FORMATS_UNSUPPORTED);

    transformer.startTransformation(mediaItem, outputPath);
    Exception exception = TransformerTestRunner.runUntilError(transformer);

    assertThat(exception).isInstanceOf(IllegalStateException.class);
  }

  @Test
  public void startTransformation_afterCancellation_completesSuccessfully() throws Exception {
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.startTransformation(mediaItem, outputPath);
    transformer.cancel();
    Files.delete(Paths.get(outputPath));

    // This would throw if the previous transformation had not been cancelled.
    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);

    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_VIDEO_ONLY));
  }

  @Test
  public void startTransformation_fromSpecifiedThread_completesSuccessfully() throws Exception {
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    anotherThread.start();
    Looper looper = anotherThread.getLooper();
    Transformer transformer =
        new Transformer.Builder()
            .setContext(context)
            .setLooper(looper)
            .setClock(clock)
            .setMuxerFactory(new TestMuxerFactory())
            .build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_AUDIO_ONLY);
    AtomicReference<Exception> exception = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    new Handler(looper)
        .post(
            () -> {
              try {
                transformer.startTransformation(mediaItem, outputPath);
                TransformerTestRunner.runUntilCompleted(transformer);
              } catch (Exception e) {
                exception.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(exception.get()).isNull();
    DumpFileAsserts.assertOutput(context, testMuxer, getDumpFileName(FILE_AUDIO_ONLY));
  }

  @Test
  public void startTransformation_fromWrongThread_throwsError() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_AUDIO_ONLY);
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.startTransformation(mediaItem, outputPath);
              } catch (IOException e) {
                // Do nothing.
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(illegalStateException.get()).isNotNull();
  }

  @Test
  public void getProgress_knownDuration_returnsConsistentStates() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_VIDEO_ONLY);
    AtomicInteger previousProgressState =
        new AtomicInteger(PROGRESS_STATE_WAITING_FOR_AVAILABILITY);
    AtomicBoolean foundInconsistentState = new AtomicBoolean();
    Handler progressHandler =
        new Handler(Looper.myLooper()) {
          @Override
          public void handleMessage(Message msg) {
            @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
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
              case PROGRESS_STATE_NO_TRANSFORMATION:
                if (progressState != PROGRESS_STATE_NO_TRANSFORMATION) {
                  foundInconsistentState.set(true);
                  return;
                }
                break;
              default:
                throw new IllegalStateException();
            }
            previousProgressState.set(progressState);
            sendEmptyMessage(0);
          }
        };

    transformer.startTransformation(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runUntilCompleted(transformer);

    assertThat(foundInconsistentState.get()).isFalse();
  }

  @Test
  public void getProgress_knownDuration_givesIncreasingPercentages() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_VIDEO_ONLY);
    List<Integer> progresses = new ArrayList<>();
    Handler progressHandler =
        new Handler(Looper.myLooper()) {
          @Override
          public void handleMessage(Message msg) {
            @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
            if (progressState == PROGRESS_STATE_NO_TRANSFORMATION) {
              return;
            }
            if (progressState != PROGRESS_STATE_WAITING_FOR_AVAILABILITY
                && (progresses.isEmpty()
                    || Iterables.getLast(progresses) != progressHolder.progress)) {
              progresses.add(progressHolder.progress);
            }
            sendEmptyMessage(0);
          }
        };

    transformer.startTransformation(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runUntilCompleted(transformer);

    assertThat(progresses).isInOrder();
    if (!progresses.isEmpty()) {
      // The progress list could be empty if the transformation ends before any progress can be
      // retrieved.
      assertThat(progresses.get(0)).isAtLeast(0);
      assertThat(Iterables.getLast(progresses)).isLessThan(100);
    }
  }

  @Test
  public void getProgress_noCurrentTransformation_returnsNoTransformation() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_VIDEO_ONLY);

    @Transformer.ProgressState int stateBeforeTransform = transformer.getProgress(progressHolder);
    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);
    @Transformer.ProgressState int stateAfterTransform = transformer.getProgress(progressHolder);

    assertThat(stateBeforeTransform).isEqualTo(Transformer.PROGRESS_STATE_NO_TRANSFORMATION);
    assertThat(stateAfterTransform).isEqualTo(Transformer.PROGRESS_STATE_NO_TRANSFORMATION);
  }

  @Test
  public void getProgress_unknownDuration_returnsConsistentStates() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_UNKNOWN_DURATION);
    AtomicInteger previousProgressState =
        new AtomicInteger(PROGRESS_STATE_WAITING_FOR_AVAILABILITY);
    AtomicBoolean foundInconsistentState = new AtomicBoolean();
    Handler progressHandler =
        new Handler(Looper.myLooper()) {
          @Override
          public void handleMessage(Message msg) {
            @Transformer.ProgressState int progressState = transformer.getProgress(progressHolder);
            switch (previousProgressState.get()) {
              case PROGRESS_STATE_WAITING_FOR_AVAILABILITY:
                break;
              case PROGRESS_STATE_UNAVAILABLE:
              case PROGRESS_STATE_AVAILABLE: // See [Internal: b/176145097].
                if (progressState == PROGRESS_STATE_WAITING_FOR_AVAILABILITY) {
                  foundInconsistentState.set(true);
                  return;
                }
                break;
              case PROGRESS_STATE_NO_TRANSFORMATION:
                if (progressState != PROGRESS_STATE_NO_TRANSFORMATION) {
                  foundInconsistentState.set(true);
                  return;
                }
                break;
              default:
                throw new IllegalStateException();
            }
            previousProgressState.set(progressState);
            sendEmptyMessage(0);
          }
        };

    transformer.startTransformation(mediaItem, outputPath);
    progressHandler.sendEmptyMessage(0);
    TransformerTestRunner.runUntilCompleted(transformer);

    assertThat(foundInconsistentState.get()).isFalse();
  }

  @Test
  public void getProgress_fromWrongThread_throwsError() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.getProgress(progressHolder);
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(illegalStateException.get()).isNotNull();
  }

  @Test
  public void cancel_afterCompletion_doesNotThrow() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    MediaItem mediaItem = MediaItem.fromUri(URI_PREFIX + FILE_VIDEO_ONLY);

    transformer.startTransformation(mediaItem, outputPath);
    TransformerTestRunner.runUntilCompleted(transformer);
    transformer.cancel();
  }

  @Test
  public void cancel_fromWrongThread_throwsError() throws Exception {
    Transformer transformer = new Transformer.Builder().setContext(context).setClock(clock).build();
    HandlerThread anotherThread = new HandlerThread("AnotherThread");
    AtomicReference<IllegalStateException> illegalStateException = new AtomicReference<>();
    CountDownLatch countDownLatch = new CountDownLatch(1);

    anotherThread.start();
    new Handler(anotherThread.getLooper())
        .post(
            () -> {
              try {
                transformer.cancel();
              } catch (IllegalStateException e) {
                illegalStateException.set(e);
              } finally {
                countDownLatch.countDown();
              }
            });
    countDownLatch.await();

    assertThat(illegalStateException.get()).isNotNull();
  }

  private static void createEncodersAndDecoders() {
    ShadowMediaCodec.CodecConfig codecConfig =
        new ShadowMediaCodec.CodecConfig(
            /* inputBufferSize= */ 10_000,
            /* outputBufferSize= */ 10_000,
            /* codec= */ (in, out) -> out.put(in));
    ShadowMediaCodec.addDecoder(MimeTypes.AUDIO_AAC, codecConfig);
    ShadowMediaCodec.addDecoder(MimeTypes.AUDIO_AMR_NB, codecConfig);
    ShadowMediaCodec.addEncoder(MimeTypes.AUDIO_AAC, codecConfig);
  }

  private static void removeEncodersAndDecoders() {
    ShadowMediaCodec.clearCodecs();
  }

  private static String getDumpFileName(String originalFileName) {
    return DUMP_FILE_OUTPUT_DIRECTORY + '/' + originalFileName + '.' + DUMP_FILE_EXTENSION;
  }

  private final class TestMuxerFactory implements Muxer.Factory {
    @Override
    public Muxer create(String path, String outputMimeType) throws IOException {
      testMuxer = new TestMuxer(path, outputMimeType);
      return testMuxer;
    }

    @Override
    public Muxer create(ParcelFileDescriptor parcelFileDescriptor, String outputMimeType)
        throws IOException {
      testMuxer = new TestMuxer("FD:" + parcelFileDescriptor.getFd(), outputMimeType);
      return testMuxer;
    }

    @Override
    public boolean supportsOutputMimeType(String mimeType) {
      return true;
    }
  }
}
