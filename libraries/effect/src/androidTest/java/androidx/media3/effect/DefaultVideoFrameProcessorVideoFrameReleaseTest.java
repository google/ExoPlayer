/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.media3.effect;

import static androidx.media3.common.util.Assertions.checkNotNull;
import static androidx.test.core.app.ApplicationProvider.getApplicationContext;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.graphics.PixelFormat;
import android.media.Image;
import android.media.ImageReader;
import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DebugViewProvider;
import androidx.media3.common.FrameInfo;
import androidx.media3.common.GlObjectsProvider;
import androidx.media3.common.GlTextureInfo;
import androidx.media3.common.SurfaceInfo;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.VideoFrameProcessor;
import androidx.media3.common.util.GlUtil;
import androidx.media3.common.util.Util;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Longs;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.checkerframework.checker.nullness.compatqual.NullableType;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for frame release in {@link DefaultVideoFrameProcessor}. */
@RunWith(AndroidJUnit4.class)
public final class DefaultVideoFrameProcessorVideoFrameReleaseTest {

  private static final int WIDTH = 200;
  private static final int HEIGHT = 100;
  /**
   * Time to wait between releasing frames to avoid frame drops between GL and the {@link
   * ImageReader}.
   */
  private static final long PER_FRAME_RELEASE_WAIT_TIME_MS = 1000L;
  /** Maximum time to wait for each released frame to be notified. */
  private static final long PER_FRAME_TIMEOUT_MS = 5000L;

  private static final long MICROS_TO_NANOS = 1000L;

  private final LinkedBlockingQueue<Long> outputReleaseTimesNs = new LinkedBlockingQueue<>();

  private @MonotonicNonNull DefaultVideoFrameProcessor defaultVideoFrameProcessor;

  @After
  public void release() {
    if (defaultVideoFrameProcessor != null) {
      defaultVideoFrameProcessor.release();
    }
  }

  @Test
  public void automaticFrameRelease_withOneFrame_reusesInputTimestamp() throws Exception {
    long originalPresentationTimeUs = 1234;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ actualPresentationTimeUs::set,
        /* releaseFramesAutomatically= */ true);

    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    ImmutableList<Long> actualReleaseTimesNs =
        waitForFrameReleaseAndGetReleaseTimesNs(/* expectedFrameCount= */ 1);
    assertThat(actualReleaseTimesNs).containsExactly(MICROS_TO_NANOS * originalPresentationTimeUs);
  }

  @Test
  public void automaticFrameRelease_withThreeFrames_reusesInputTimestamps() throws Exception {
    long[] originalPresentationTimesUs = new long[] {1234, 3456, 4567};
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    processFramesToEndOfStream(
        originalPresentationTimesUs,
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimesUs.add(presentationTimeUs);
          try {
            // TODO(b/264252759): Investigate output frames being dropped and remove sleep.
            // Frames can be dropped silently between EGL and the ImageReader. Sleep after each call
            // to swap buffers, to avoid this behavior.
            Thread.sleep(PER_FRAME_RELEASE_WAIT_TIME_MS);
          } catch (InterruptedException e) {
            throw new IllegalStateException(e);
          }
        },
        /* releaseFramesAutomatically= */ true);

    assertThat(actualPresentationTimesUs)
        .containsExactly(
            originalPresentationTimesUs[0],
            originalPresentationTimesUs[1],
            originalPresentationTimesUs[2])
        .inOrder();
    ImmutableList<Long> actualReleaseTimesNs =
        waitForFrameReleaseAndGetReleaseTimesNs(/* expectedFrameCount= */ 3);
    assertThat(actualReleaseTimesNs)
        .containsExactly(
            MICROS_TO_NANOS * originalPresentationTimesUs[0],
            MICROS_TO_NANOS * originalPresentationTimesUs[1],
            MICROS_TO_NANOS * originalPresentationTimesUs[2])
        .inOrder();
  }

  @Test
  public void controlledFrameRelease_withOneFrame_usesGivenTimestamp() throws Exception {
    long originalPresentationTimeUs = 1234;
    long releaseTimesNs = System.nanoTime() + 345678;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(defaultVideoFrameProcessor).releaseOutputFrame(releaseTimesNs);
        },
        /* releaseFramesAutomatically= */ false);

    ImmutableList<Long> actualReleaseTimesNs =
        waitForFrameReleaseAndGetReleaseTimesNs(/* expectedFrameCount= */ 1);
    assertThat(actualReleaseTimesNs).containsExactly(releaseTimesNs);
  }

  @Test
  public void controlledFrameRelease_withOneFrameRequestImmediateRelease_releasesFrame()
      throws Exception {
    long originalPresentationTimeUs = 1234;
    long releaseTimesNs = VideoFrameProcessor.RELEASE_OUTPUT_FRAME_IMMEDIATELY;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(defaultVideoFrameProcessor).releaseOutputFrame(releaseTimesNs);
        },
        /* releaseFramesAutomatically= */ false);

    assertThat(actualPresentationTimeUs.get()).isEqualTo(originalPresentationTimeUs);
    // The actual release time is determined by the VideoFrameProcessor when releasing the frame.
    ImmutableList<Long> actualReleaseTimesNs =
        waitForFrameReleaseAndGetReleaseTimesNs(/* expectedFrameCount= */ 1);
    assertThat(actualReleaseTimesNs).hasSize(1);
  }

  @Test
  public void controlledFrameRelease_withLateFrame_releasesFrame() throws Exception {
    long originalPresentationTimeUs = 1234;
    long releaseTimeBeforeCurrentTimeNs = System.nanoTime() - 345678;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimeUs.set(presentationTimeUs);
          checkNotNull(defaultVideoFrameProcessor)
              .releaseOutputFrame(releaseTimeBeforeCurrentTimeNs);
        },
        /* releaseFramesAutomatically= */ false);

    ImmutableList<Long> actualReleaseTimesNs =
        waitForFrameReleaseAndGetReleaseTimesNs(/* expectedFrameCount= */ 1);
    assertThat(actualReleaseTimesNs).hasSize(1);
    // The actual release time is determined by the VideoFrameProcessor when releasing the frame.
    assertThat(actualReleaseTimesNs.get(0)).isAtLeast(releaseTimeBeforeCurrentTimeNs);
  }

  @Test
  public void controlledFrameRelease_requestsFrameDropping_dropsFrame() throws Exception {
    long originalPresentationTimeUs = 1234;
    AtomicLong actualPresentationTimeUs = new AtomicLong();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ new long[] {originalPresentationTimeUs},
        /* onFrameAvailableListener= */ presentationTimeNs -> {
          actualPresentationTimeUs.set(presentationTimeNs);
          checkNotNull(defaultVideoFrameProcessor)
              .releaseOutputFrame(VideoFrameProcessor.DROP_OUTPUT_FRAME);
        },
        /* releaseFramesAutomatically= */ false);

    waitForFrameReleaseAndGetReleaseTimesNs(/* expectedFrameCount= */ 0);
  }

  @Test
  public void controlledFrameRelease_withThreeIndividualFrames_usesGivenTimestamps()
      throws Exception {
    long[] originalPresentationTimesUs = new long[] {1234, 3456, 4567};
    long offsetNs = System.nanoTime();
    long[] releaseTimesNs = new long[] {offsetNs + 123456, offsetNs + 234567, offsetNs + 345678};
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    AtomicInteger frameIndex = new AtomicInteger();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ originalPresentationTimesUs,
        /* onFrameAvailableListener= */ presentationTimeUs -> {
          actualPresentationTimesUs.add(presentationTimeUs);
          checkNotNull(defaultVideoFrameProcessor)
              .releaseOutputFrame(releaseTimesNs[frameIndex.getAndIncrement()]);
          try {
            // TODO(b/264252759): Investigate output frames being dropped and remove sleep.
            // Frames can be dropped silently between EGL and the ImageReader. Sleep after each call
            // to swap buffers, to avoid this behavior.
            Thread.sleep(PER_FRAME_RELEASE_WAIT_TIME_MS);
          } catch (InterruptedException e) {
            throw new IllegalStateException(e);
          }
        },
        /* releaseFramesAutomatically= */ false);

    assertThat(actualPresentationTimesUs)
        .containsExactly(
            originalPresentationTimesUs[0],
            originalPresentationTimesUs[1],
            originalPresentationTimesUs[2])
        .inOrder();
    int actualFrameCount = frameIndex.get();
    assertThat(actualFrameCount).isEqualTo(originalPresentationTimesUs.length);
    long[] actualReleaseTimesNs =
        Longs.toArray(waitForFrameReleaseAndGetReleaseTimesNs(actualFrameCount));
    assertThat(actualReleaseTimesNs).isEqualTo(releaseTimesNs);
  }

  @Test
  public void controlledFrameRelease_withThreeFramesAtOnce_usesGivenTimestamps() throws Exception {
    long[] originalPresentationTimesUs = new long[] {1234, 3456, 4567};
    long offsetNs = System.nanoTime();
    long[] releaseTimesNs = new long[] {offsetNs + 123456, offsetNs + 234567, offsetNs + 345678};
    ArrayList<Long> actualPresentationTimesUs = new ArrayList<>();
    processFramesToEndOfStream(
        /* inputPresentationTimesUs= */ originalPresentationTimesUs,
        /* onFrameAvailableListener= */ actualPresentationTimesUs::add,
        /* releaseFramesAutomatically= */ false);

    // TODO(b/264252759): Investigate output frames being dropped and remove sleep.
    // Frames can be dropped silently between EGL and the ImageReader. Sleep after each call
    // to swap buffers, to avoid this behavior.
    defaultVideoFrameProcessor.releaseOutputFrame(releaseTimesNs[0]);
    Thread.sleep(PER_FRAME_RELEASE_WAIT_TIME_MS);
    defaultVideoFrameProcessor.releaseOutputFrame(releaseTimesNs[1]);
    Thread.sleep(PER_FRAME_RELEASE_WAIT_TIME_MS);
    defaultVideoFrameProcessor.releaseOutputFrame(releaseTimesNs[2]);
    Thread.sleep(PER_FRAME_RELEASE_WAIT_TIME_MS);

    assertThat(actualPresentationTimesUs)
        .containsExactly(
            originalPresentationTimesUs[0],
            originalPresentationTimesUs[1],
            originalPresentationTimesUs[2])
        .inOrder();
    long[] actualReleaseTimesNs =
        Longs.toArray(waitForFrameReleaseAndGetReleaseTimesNs(/* expectedFrameCount= */ 3));
    assertThat(actualReleaseTimesNs).isEqualTo(releaseTimesNs);
  }

  private interface OnOutputFrameAvailableListener {
    void onFrameAvailable(long presentationTimeUs);
  }

  @EnsuresNonNull("defaultVideoFrameProcessor")
  private void processFramesToEndOfStream(
      long[] inputPresentationTimesUs,
      OnOutputFrameAvailableListener onFrameAvailableListener,
      boolean releaseFramesAutomatically)
      throws Exception {
    AtomicReference<@NullableType VideoFrameProcessingException>
        videoFrameProcessingExceptionReference = new AtomicReference<>();
    BlankFrameProducer blankFrameProducer = new BlankFrameProducer();
    CountDownLatch videoFrameProcessingEndedCountDownLatch = new CountDownLatch(1);
    defaultVideoFrameProcessor =
        checkNotNull(
            new DefaultVideoFrameProcessor.Factory()
                .create(
                    getApplicationContext(),
                    ImmutableList.of((GlEffect) (context, useHdr) -> blankFrameProducer),
                    DebugViewProvider.NONE,
                    /* inputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
                    /* outputColorInfo= */ ColorInfo.SDR_BT709_LIMITED,
                    /* isInputTextureExternal= */ true,
                    releaseFramesAutomatically,
                    MoreExecutors.directExecutor(),
                    new VideoFrameProcessor.Listener() {
                      @Override
                      public void onOutputSizeChanged(int width, int height) {
                        ImageReader outputImageReader =
                            ImageReader.newInstance(
                                width,
                                height,
                                PixelFormat.RGBA_8888,
                                /* maxImages= */ inputPresentationTimesUs.length);
                        checkNotNull(defaultVideoFrameProcessor)
                            .setOutputSurfaceInfo(
                                new SurfaceInfo(outputImageReader.getSurface(), width, height));
                        outputImageReader.setOnImageAvailableListener(
                            imageReader -> {
                              try (Image image = imageReader.acquireNextImage()) {
                                outputReleaseTimesNs.add(image.getTimestamp());
                              }
                            },
                            Util.createHandlerForCurrentOrMainLooper());
                      }

                      @Override
                      public void onOutputFrameAvailable(long presentationTimeUs) {
                        onFrameAvailableListener.onFrameAvailable(presentationTimeUs);
                      }

                      @Override
                      public void onError(VideoFrameProcessingException exception) {
                        videoFrameProcessingExceptionReference.set(exception);
                        videoFrameProcessingEndedCountDownLatch.countDown();
                      }

                      @Override
                      public void onEnded() {
                        videoFrameProcessingEndedCountDownLatch.countDown();
                      }
                    }));

    defaultVideoFrameProcessor
        .getTaskExecutor()
        .submit(
            () -> {
              blankFrameProducer.configureGlObjects();
              checkNotNull(defaultVideoFrameProcessor)
                  .setInputFrameInfo(new FrameInfo.Builder(WIDTH, HEIGHT).build());
              // A frame needs to be registered despite not queuing any external input to ensure
              // that
              // the video frame processor knows about the stream offset.
              defaultVideoFrameProcessor.registerInputFrame();
              blankFrameProducer.produceBlankFramesAndQueueEndOfStream(inputPresentationTimesUs);
            });
    videoFrameProcessingEndedCountDownLatch.await();
    @Nullable
    Exception videoFrameProcessingException = videoFrameProcessingExceptionReference.get();
    if (videoFrameProcessingException != null) {
      throw videoFrameProcessingException;
    }
  }

  private ImmutableList<Long> waitForFrameReleaseAndGetReleaseTimesNs(int expectedFrameCount)
      throws Exception {
    ImmutableList.Builder<Long> listBuilder = new ImmutableList.Builder<>();
    for (int i = 0; i < expectedFrameCount; i++) {
      listBuilder.add(checkNotNull(outputReleaseTimesNs.poll(PER_FRAME_TIMEOUT_MS, MILLISECONDS)));
    }
    // This is a best-effort check because there's no guarantee that frames aren't added to the
    // release times after this method has been called.
    assertThat(outputReleaseTimesNs).isEmpty();
    return listBuilder.build();
  }

  /** Produces blank frames with the given timestamps. */
  private static final class BlankFrameProducer implements GlShaderProgram {

    private @MonotonicNonNull GlTextureInfo blankTexture;
    private @MonotonicNonNull OutputListener outputListener;

    public void configureGlObjects() throws VideoFrameProcessingException {
      try {
        int texId =
            GlUtil.createTexture(WIDTH, HEIGHT, /* useHighPrecisionColorComponents= */ false);
        int fboId = GlUtil.createFboForTexture(texId);
        blankTexture = new GlTextureInfo(texId, fboId, /* rboId= */ C.INDEX_UNSET, WIDTH, HEIGHT);
        GlUtil.focusFramebufferUsingCurrentContext(fboId, WIDTH, HEIGHT);
        GlUtil.clearOutputFrame();
      } catch (GlUtil.GlException e) {
        throw new VideoFrameProcessingException(e);
      }
    }

    public void produceBlankFramesAndQueueEndOfStream(long[] presentationTimesUs) {
      checkNotNull(outputListener);
      for (long presentationTimeUs : presentationTimesUs) {
        outputListener.onOutputFrameAvailable(checkNotNull(blankTexture), presentationTimeUs);
      }
      outputListener.onCurrentOutputStreamEnded();
    }

    @Override
    public void setInputListener(InputListener inputListener) {}

    @Override
    public void setOutputListener(OutputListener outputListener) {
      this.outputListener = outputListener;
    }

    @Override
    public void setErrorListener(Executor executor, ErrorListener errorListener) {}

    @Override
    public void setGlObjectsProvider(GlObjectsProvider glObjectsProvider) {}

    @Override
    public void queueInputFrame(GlTextureInfo inputTexture, long presentationTimeUs) {
      // No input is queued in these tests. The BlankFrameProducer is used to produce frames.
      throw new UnsupportedOperationException();
    }

    @Override
    public void releaseOutputFrame(GlTextureInfo outputTexture) {}

    @Override
    public void signalEndOfCurrentInputStream() {
      // The tests don't end the input stream.
      throw new UnsupportedOperationException();
    }

    @Override
    public void flush() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void release() {
      // Do nothing as destroying the OpenGL context destroys the texture.
    }
  }
}
